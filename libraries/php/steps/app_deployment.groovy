void call() {
    String stepName = 'build'
    String accountId = config?.accountId ?: '541906215541'
    String region = config?.region ?: 'us-east-1'
    String ecrRepoName = config?.ecrRepoName ?: 'lut'
    String appUrl = config?.appUrl ?: 'health'
    String appName = config?.appName ?: 'lut'
    String tagPrefix = config?.tagPrefix ?: 'lut-'
    String applicationType = config?.applicationType ?: 'eks'
    String showSlackNotifications = config?.showSlackNotifications == true
    String slackChannel = config?.slackChannel?: '#jenkins-build-notifications'
    String varScript = config?.varScript? 'printenv;' : ""
    String loglevel = config?.podman?.loglevel ?: 'debug'
    String deployReleaseStage = config?.podman?.deployReleaseStage ?: 'release'
    String deployStage = config?.podman?.deployStage ?: 'deployer'
    String dockerStageTest = config?.podman?.dockerStageTest ?: 'tester'
    String dockerContainer = config?.podman?.container ?: 'podman-container'
    String jteVersion = config?.jteVersion ?: '1.0.0'
    String dockerfileName = config?.docker?.fileName?: 'docker/php-fpm/Dockerfile'
    String dockerfilePath = config?.docker?.filePath?: 'docker/php-fpm/.'
    String dockerStageDeploy =config?.docker?.deployStage?: 'final'
    // String dockerContainer = config?.dockerContainer?:'podman'
    String command = config?.command?: 'ls'
    String dockerLogLevel = config?.dockerLogLevel?: 'debug' //warn
    String codacyApiToken = config?.codacyApiToken?: 'CODACY_PROJECT_TOKEN'

    stage(stepName) {
        try {
            env.TRACE_MESSAGE = "[JTE:${stepName}]"
            echo "${env.TRACE_MESSAGE}- Started for ${applicationType}"
            if (applicationType == 'eks' || applicationType == 'ecr' || applicationType == 'ecs') {
                GString ecrUrl = "<https://${region}.console.aws.amazon.com/ecr/repositories/private/${accountId}/${ecrRepoName}?region=${region}|ECR>"
                }
            // if ((env.BRANCH_NAME == env.masterBranch || env.TAG_NAME) && env.releaseEnv == 'qa') {
                // echo "${env.TRACE_MESSAGE} ${env.buildDesc}"
                //only delete the latest image if there is one
                // if (env.latestDigest != 'none') {
                //     ecrDeleteImage(repositoryName: ecrRepoName, registryIds: [accountId], imageIds: [['imageDigest': env.latestDigest, 'imageTag': env.versionNumber]])
                // }
                properties([
                    parameters([
                        string(defaultValue: '', name: 'NEW_RELIC_AGENT_VERSION'),
                        string(defaultValue: '', name: 'IMAGE_RELEASE_TAG', description: 'what is the image tag'),
                        string(defaultValue: '', name: 'NEW_RELIC_NAME',  description: 'the name will be display on the NR UI')
                        // string(defaultValue: '11', description: '', name: 'Version', trim: false)
                    ])
                ])

                if (stepName == 'build') {
                    
                    def scmVars = checkout scm
                    container(dockerContainer) {
                        def login = ecrLogin(registryIds: [accountId]).replace('docker','podman')
                        String dockerInfo = dockerLogLevel == 'debug' ? 'podman info --debug' : 'podman version'
                        echo "${env.TRACE_MESSAGE} Logged into ECR"
                        sh(script: """#!/bin/bash
                            set -e +o pipefail;                       
                            ${login} &&
                            podman system prune -a --force &&
                            podman build -t 541906215541.dkr.ecr.us-east-1.amazonaws.com/lut:${params.IMAGE_RELEASE_TAG}.${BUILD_ID} . &&
                            podman push 541906215541.dkr.ecr.us-east-1.amazonaws.com/lut:${params.IMAGE_RELEASE_TAG}.${BUILD_ID}
                        """, label: 'create image latest')
                    //     env.imageDigest = sh(returnStdout: true, script: """#!/bin/bash
                    //         podman image inspect ${env.fullECRRepoName}:${env.versionNumber} -f '{{join.RepoDigest \",\"}}'
                    //         """, label: 'Get digest in place sync').trim()
                     }
                     
                }
                // env.deployed = true
                // env.builDesc += "\n${ecrUrl}"
                // buildDescription("Updated Image : ${ecrRepoName}:${env.versionNumber} \nCommit : ${env.GIT_COMMIT}\nEnvironment: ${env.releaseEnv}\n")
            // } //else if (env.TAG_NAME) {
                // echo "${env.TRACE_MESSAGE} ${env.buildDesc}"
                // container(dockerContainer) {
                //     def login = ecrLogin(registryIds: [accountId]).replace('docker','podman')
                //     if (env.deployableEnvs != 'any') {
                //         sh(script: """#!/bin/bash
                //             set -e +o pipefail;
                //             ${env.VERBOSE_CI}
                //             export DOCKER_BUILDKIT=1;
                //             ${login} 1> /dev/null;
                //             podman system prune -a --force;
                //             podman build --target ${dockerStageDeploy} -t \"${env.fullECRRepoName}:${env.versionNumber}\" ${env.dockerBuildArgs} -f ${dockerfileName} ${dockerfilePath};
                //             """, label: 'Update Release Environment')
                //     } else {
                //         echo "${env.TRACE_MESSAGE} Image ${ecrRepoName}:${versionNumber} already exists"
                //     }
                // }
                  if (showSlackNotifications) {
                    String slackIcon = successSlackIcon()
                    slackSend color: 'good', channel: "${slackChannel}", message: "${env.buildDesc} ${slackIcon}"
                  }
               
        } catch (Exception any) {
            env.TRACE_MESSAGE = "[JTE:ERROR:${stepName}]"
            String stackTrace = any.getStackTrace()
            GString errorMessage = "${env.buildDesc} \nError: ${any.getMessage()}"
            GString slackMessage = "${appName}: ${env.BRANCH_NAME} \nFailed at Stage : ${stepName}"
            // echo "${env.TRACE_MESSAGE} ${stackTrace}"
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

static String successSlackIcon() {
    List<String> icons = [':unicorn_face:', ':beer:', ':bee:', ':man_dancing:', ':boogie-wookie:']
    return icons[new Random().nextInt(icons.size())]
}

// def notifyProductionDeploy() {
//   def icons = [":unicorn_face:", ":beer:", ":bee:", ":man_dancing:",
//     ":party_parrot:", ":ghost:", ":dancer:", ":scream_cat:"]
//   def randomIndex = (new Random()).nextInt(icons.size())

//   def message = "@here Build <${env.BUILD_URL}|${currentBuild.displayName}> " +
//     "successfuly deployed to the production ${icons[randomIndex]}"
// slackSend(message: message, channel: '#channel', color: 'good', token: 'token')
// }