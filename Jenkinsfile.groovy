server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'node', image: 'node:8', command: 'cat', ttyEnabled: true)
]) {

    node('jenkins-pipeline') {

        stage ('Cleanup') {
            cleanWs()
        }

        stage('Clone sources') {
            git url: 'https://github.com/eladh/docker-app-demo.git', credentialsId: 'github'
        }

        stage ('Download Dependencies') {
            try {
                def pipelineUtils = load 'pipelineUtils.groovy'
                pipelineUtils.downloadArtifact(rtFullUrl ,"gradle-local" ,"*demo-gradle/*" ,"jar" ,buildInfo,false)
                pipelineUtils.downloadArtifact(rtFullUrl ,"npm-local" ,"*client-app*" ,"tgz" ,buildInfo ,true)
            } catch (Exception e) {
                println "Caught Exception during resolution. Message ${e.message}"
                throw e as java.lang.Throwable
            }
        }

        stage('Docker build') {
            def rtDocker = Artifactory.docker server: server

            container('docker') {

                configFileProvider(
                        [configFile(fileId: 'private_key', variable: 'KEY')]) {
                    echo " =========== ^^^^^^^^^^^^ Reading222 config from pipeline script "
                    sh("mkdir -p  /etc/docker/certs.d/test-docker-reg\\:5000")
                    sh "cat ${env.KEY} >> /etc/docker/certs.d/test-docker-reg\\:5000/arti.crt"
                    echo " =========== ~~~~~~~~~~~~ ============ "
                }

                docker.withRegistry(rtFullUrl, 'artifactorypass') {
                    groovy.lang.GString dockerImageTag = "docker.artifactory.jfrog.com/docker-app:${env.BUILD_NUMBER}"
                    def dockerImageTagLatest = "docker.artifactory.jfrog.com/docker-app:latest"

                    buildInfo.env.capture = true

                    docker.build(dockerImageTag, "--build-arg DOCKER_REGISTRY_URL=docker.artifactory.jfrog.com .")
                    docker.build(dockerImageTagLatest, "--build-arg DOCKER_REGISTRY_URL=docker.artifactory.jfrog.com .")


                    rtDocker.push(dockerImageTag, "docker-local", buildInfo)
                    rtDocker.push(dockerImageTagLatest, "docker-local", buildInfo)
                    server.publishBuildInfo buildInfo
                }
            }
        }
    }




//    stage('Docker Integration Tests') {
//        groovy.lang.GString tag = "${rtIpAddress}/docker-app:${env.BUILD_NUMBER}"
//        docker.image(tag).withRun('-p 9191:81 -e “SPRING_PROFILES_ACTIVE=local” ') {c ->
//            sleep 10
//            def stdout = sh(script: 'curl "http://localhost:9191/index.html"', returnStdout: true)
//            println stdout
//            if (stdout.contains("client-app")) {
//                println "*** Passed Test: " + stdout
//                println "*** Passed Test"
//                return true
//            } else {
//                println "*** Failed Test: " + stdout
//                return false
//            }
//        }
//    }

//    stage('Helm install') {
//        docker.image('docker.bintray.io/jfrog/jfrog-cli-go:latest').inside {
//            sh "ls"
//        }
//
//    }
//
//    //Scan Build Artifacts in Xray
//    stage('Xray Scan') {
//        if (XRAY_SCAN == "YES") {
//            java.util.LinkedHashMap<java.lang.String, java.lang.Boolean> xrayConfig = [
//                    'buildName' : env.JOB_NAME,
//                    'buildNumber' : env.BUILD_NUMBER,
//                    'failBuild' : false
//            ]
//            def xrayResults = server.xrayScan xrayConfig
//
//            if (xrayResults.isFoundVulnerable()) {
//                error('Stopping early… got Xray issues ')
//            }
//        } else {
//            println "No Xray scan performed. To enable set XRAY_SCAN = YES"
//        }
//    }
//
//    stage('Promote Docker image') {
//        java.util.LinkedHashMap<java.lang.String, java.lang.Object> promotionConfig = [
//                'buildName'  : buildInfo.name,
//                'buildNumber': buildInfo.number,
//                'targetRepo' : "docker-prod-local",
//                'comment'    : 'This is a stable docker image',
//                'status'     : 'Released',
//                'sourceRepo' : 'docker-stage-local',
//                'copy'       : true,
//                'failFast'   : true
//        ]
//
//        server.promote promotionConfig
//    }


}