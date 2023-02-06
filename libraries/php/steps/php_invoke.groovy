@StepAlias(['prepare_tools','source_test'])
void call() {
    String stepName = 'prepare'
    String accountID = config?.accountID ?: '541906215541'
    String region = config?.region ?: 'us-east-1'
    String ecrRepoName = config?.ecrRepoName ?: 'lut'
    String appUrl = config?.appUrl ?: 'health'
    String appName = config?.appName ?: 'lut'
    String tagPrefix = config?.tagPrefix ?: 'lut-'
    String applicationType = config?.applicationType ?: 'eks'
    String varScript = config?.varScript? 'printenv;' : ""
    String loglevel = config?.podman?.loglevel ?: 'debug'
    String deployReleaseStage = config?.podman?.deployReleaseStage ?: 'release'
    String deployStage = config?.podman?.deployStage ?: 'deployer'
    String testStage = config?.podman?.testStage ?: 'tester'
    String container = config?.podman?.container ?: 'podman'
    String jteVersion = config?.jteVersion ?: '1.0.0'
    String dockerLogLevel = config?.dockerLogLevel?: 'debug' //warn
    String codacyApiToken = config?.codacyApiToken?: 'CODACY_PROJECT_TOKEN'

    switch (stepName.name) {
        case 'prepare_tools':
        stepName = 'prepare'
        break
        case 'source_test':
        stepName = 'test'
        break
        // case 'deploying':
        // stepName = 'deploy'
        default:
        error("step name must be readiness,testing and deploying got ${stepContext.name}")
    }
    stage(stepName) {
        try {
            env.TRACE_MESSAGE = "[JTE:${stepName}]"
            echo "${env.TRACE_MESSAGE}- Started for ${applicationType}"
            boolean isEcrOnly = applicationType == 'ecr'
            if (stepName == 'prepare') {
                env.buildDesc = "${appName}"
                env.JTE_VERSION = env.JTE_VERSION ?: "php-${jteVersion}"
                def scmVars = checkout scm
                env.repoName = scmVars?.GIT_URL?.split('/' as Closure)[-1].replace('.git', '')
                env.GIT_COMMIT = scmVars.GIT_COMMIT
                env.AWS_ARGS = ''

                def lastMessage = sh(returnStdout: true, label: 'Checking CI',
                    script:"""#!/bin/bash
                    _tmp=\$(git log --oneline -n 1)
                    echo \$_tmp """).trim()
                    env.VERBOSE_CI = ''
                    env.VERBOSE_MESSAGE
                if (lastMessage.contains('skip_ci')) {
                    env.SKIP_CI = true
                    env.TRACE_MESSAGE += '[SKIP_CI]'
                    echo "${env.TRACE_MESSAGE} means a --dry-run will be appended to various aws commands, and --help docker"
                }
                if (lastMessage.contains('verbose_ci')) {
                    env.VERBOSE_CI = true
                    env.TRACE_MESSAGE += '[VERBOSE_CI]'
                    env.VERBOSE_MESSAGE = varScript == '' ? '' : varScript
                    env.bashSetup = 'set -ex pipefail;'
                    echo "${env.TRACE_MESSAGE} means a --debug will be appended to various aws commands, and --log-level debug to docker"
                    env.AWS_ARGS += '--debug'
                    env.dockerPushArgs += '--log-level debug'
                }
                echo "${env.TRACE_MESSAGE} skip_ci:${env.SKIP_CI}| verbose_ci:${env.VERBOSE_CI}"
                env.masterBranch = env.CHANGE_TARGET? env.CHANGE_TARGET : env.BRANCH_NAME
                env.releaseEnv = hasQaEnv ? 'qa' : 'staging'
                env.dockerBuilArgs = "--events-backend=file --log-level=${dockerLogLevel}"
                if (codacy != 'NONE') {
                    withCredentials([string(credentialsId: "${codacy}", variable: 'CODACY_TOKEN')]) {
                        codacyApiToken = CODACY_TOKEN
                    }
                    env.dockerBuilArgs += "--build-arg CODACY_API_TOKEN_ARG=${codacyApiToken}"
                }
                if (projectCodacy != 'NONE') {
                    withCredentials([string(credentialsId: "${codacy}", variable: 'CODACY_TOKEN')]) {
                        codacyProjectToken = CODACY__PROJECT_TOKEN
                    }
                    env.dockerBuilArgs += "--build-arg CODACY_PROJECT_TOKEN_ARG=${codacyProjectToken}"
                }
                if (newRelicToken != 'NONE') {
                    withAWSParameterStore(naming: 'relative', path: "${newrelicParam}", recursive: true, region: "${region}") {
                        newRelicApiKey = NEWRELIC_API_KEY
                        newRelicToken = NEWRELIC_LICENCE_KEY
                    }
                    env.dockerBuilArgs += "--build-arg NR_TOKEN_ARG=${newRelicToken}"
                }
                env.NR_TOKEN = newRelicApiKey
                env.ECR_AWS = "${env.ECR_AWS}/${ecrRepoName}"
                if (env.VERBOSE_CI == "true") {
                    echo "{env.TRACE_MESSAGE} ${env.builDesc}"
                }
                env.versionNumber = env.TAG_NAME ? env.TAG_NAME.replace(tagPrefix, '').replace('testing-', '') : 'latest'
                //def login = ecrLogin(registryIds: [accountId]).replace('docker','podman')
                echo "${env.TRACE_MESSAGE} checking existing images for version ${env.versionNumber}"
                def images = ecrListImages(repositoryName: ecrRepoName)
                env.deployableEnvs = 'staging'
                env.IS_DEPLOYED = false
                env.latestDigest = 'NONE'
                for (ecrImage in images) {
                    def imageTag = ecrImage.get("imageTag")
                    if (imageTag == "latest") {
                        env.latestDigest = ecrImage.get("imageDigest")
                    } else if (imageTag == env.versionNumber) {
                            env.IS_DEPLOYED = true
                            env.deployableEnvs = 'any'
                    }
                }
                if (env.TAG_NAME) {
                    echo "${env.TRACE_MESSAGE} Images retrieved ${images}"
                    def deployOptions = (env.deployableEnvs == 'staging') ? ['qa','staging'] : ['qa','staging','live']
                    env.releaseEnv = input(id: 'EnvChoice', message: 'Deploy to which env?', parameters: [choice(choices: deployOptions, desc: 'env to deploy?', name: 'configChoice')])
                    env.GIT_CURRENT_CMT_URL = "\nCommit : <https://${gitUrl}/${companyName}/${env.repoName}/commits/tag/${env.TAG_NAME}|github>"
                    env.dockerBuilArgs += " --build-arg RELEASE_TAG=${env.versionNumber} --build-arg APP_ENV=${env.releaseEnv}"
                    buildName "${env.releaseEnv}:${env.versionNumber}:${env.BUILD_ID}"
                }
                else {
                    // Load dockerBuil args for master, and pr
                    env.dockerBuilArgs += " --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} --build-arg LAST_COMMIT=${env.GIT_COMMIT} --build-arg APP_ENV=${env.releaseEnv}"
                    env.GIT_CURRENT_CMT_URL = "\nCommit: <https://${gitUrl}/${companyName}/${env.repoName}/commits/${env.GIT_COMMIT}|github>"
                    if (env.CHANGE_ID) {
                        buildName "${env.CHANGE_ID}:${env.BUILD_ID}"
                    } else {
                        buildName "${env.releaseEnv}:${env.BUILD_ID}"
                    }
                }


            }
            else if (stepName == 'testing') {
                if (env.SKIP_CI == 'true') {
                    echo "${env.TRACE_MESSAGE} Skipping ci per commit message"
                } else if (env.TAG_NAME) {
                    echo "${env.TRACE_MESSAGE} Skipping test stage for deploy from tags"
                } else if (env.CHANGE_ID) {
                    container(dockerContainer) {
                        sh(script: """#!/bin/bash
                        set -e +o pipefail;
                        ${env.VERBOSE_MESSAGE}
                        echo \"${env.TRACE_MESSAGE} Running tests via podman via build --target ${dockerStageTest} -t \"${env.fullECRRepoName}:testing\" ${env.dockerBuildArgs} -f ${dockerfileName} ${dockerfilePath}\";
                        export DOCKER_BUILDKIT=1;
                        podman system prune -a --force;
                        echo \"${env.TRACE_MESSAGE} prune run\";
                        podman build --target ${dockerStageTest} -t \"${env.fullECRRepoName}:testing\" ${env.dockerBuildArgs} -f ${dockerfileName} ${dockerfilePath};
                        podman images prune -a --filter \"reference=${env.fullECRRepoName}*\";
                    """, label: "Test Coverage")
                        
                    }
                }    
                    else {
                        echo "${env.TRACE_MESSAGE} test stage ignored for ${env.BRANCH_NAME}"
                        }
                }
                else if (showSlackNotifications) {
                    slackSend(color: "warning", channel: "${slackChannel}", message: "Called Nonexisting Stage: StepName \n ${stepName}")
                }
                echo "${env.TRACE_MESSAGE} ${stepName} - finished"
            } catch (Exception any) {
                env.TRACE_MESSAGE = "[JTE:ERROR:${stepName}]"
                String stackTrace = any.getStackTrace()
                GString errorMessage = "${env.buildDesc} \nError: ${any.getMessage()}"
                GString slackMessage = "${appName}: ${env.BRANCH_NAME} \nFailed at Stage : ${stepName}"
                echo "${env.TRACE_MESSAGE} ${stackTrace}"
                if (showSlackNotifications) {
                    slackSend color: "danger", channel: "${slackChannel}", message: "${slackMessage}"
                } else {
                    echo "${env.TRACE_MESSAGE} SlackMessage : \n${slackMessage}\nError : ${errorMessage}\n ${stackTrace}"
                }
                buildDescription("${env.TRACE_MESSAGE} ${errorMessage} \n StackTrace : ${stackTrace}")
                buildStatus = "Failed"
                throw any as Throwable

            }

        }
    }
    
 







// appUrl = 'health'
//         appName = 'lut'
//         //git tag prefix used for creating versions
//         tagPrefix = 'lut-'
//         applicationType = 'eks'