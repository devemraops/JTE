# JTE
jte-training


                        #echo \"${env.TRACE_MESSAGE} deploying to ${env.releaseEnv} ${applicationType}\";
                        #export DOCKER_BUILDKIT=1;
                        #echo \"${env.TRACE_MESSAGE} Logging to ECR on podman container\";
                        #echo \"${env.TRACE_MESSAGE} build via podman with build --target ${dockerStageTest} -t \"${env.fullECRRepoName}:testing\" ${env.dockerBuildArgs} -f ${dockerfileName} ${dockerfilePath}\";
                        #echo \"${env.TRACE_MESSAGE} Prune run\";
                        #echo \"${env.TRACE_MESSAGE} Image ${env.fullECRRepoName}:${env.versionNumber} updated\";
                        #echo \"${env.TRACE_MESSAGE} Need to create a new deployment in ${applicationType} with image ${env.fullECRRepoName}:${env.versionNumber}\";
                        #echo \n"${env.TRACE_MESSAGE} Image built run\";