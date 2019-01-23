server = Artifactory.server "artifactory-server"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


node('generic') {

    stage ('Cleanup') {
        cleanWs()
    }

    stage('Clone sources') {
        git url: 'http://35.202.14.162/elad/app-repo.git'
    }

    stage ('Download Dependencies') {
        try {
            def pipelineUtils = load 'pipelineUtils.groovy'
            pipelineUtils.downloadArtifact("gradle-release-local" ,"*demo-gradle/*" ,"jar" ,buildInfo,false)
            pipelineUtils.downloadArtifact("npm-local" ,"*client-app*" ,"tgz" ,buildInfo ,true)
        } catch (Exception e) {
            println "Caught Exception during resolution. Message ${e.message}"
            throw e as java.lang.Throwable
        }
    }

    stage('Docker build') {
        def rtDocker = Artifactory.docker server: server

        docker.withRegistry(rtFullUrl, 'artCredId') {
            groovy.lang.GString dockerImageTag = "${rtIpAddress}/docker-app:${env.BUILD_NUMBER}"
            def dockerImageTagLatest = "${rtIpAddress}/docker-app:latest"

            buildInfo.env.capture = true

            docker.build(dockerImageTag, "--build-arg DOCKER_REGISTRY_URL=${rtIpAddress} .")
            docker.build(dockerImageTagLatest, "--build-arg DOCKER_REGISTRY_URL=${rtIpAddress} .")


            rtDocker.push(dockerImageTag ,"docker-stage-local", buildInfo)
            rtDocker.push(dockerImageTagLatest ,"docker-stage-local", buildInfo)
            server.publishBuildInfo buildInfo
        }
    }


    stage('Docker Integration Tests') {
        groovy.lang.GString tag = "${rtIpAddress}/docker-app:${env.BUILD_NUMBER}"
        docker.image(tag).withRun('-p 9191:81 -e “SPRING_PROFILES_ACTIVE=local” ') {c ->
            sleep 10
            def stdout = sh(script: 'curl "http://localhost:9191/index.html"', returnStdout: true)
            println stdout
//            if (stdout.contains("client-app")) {
//                println "*** Passed Test: " + stdout
//                println "*** Passed Test"
//                return true
//            } else {
//                println "*** Failed Test: " + stdout
//                return false
//            }
        }
    }

    stage('Helm install') {
        docker.image('docker.bintray.io/jfrog/jfrog-cli-go:latest').inside {
            sh "ls"
        }

    }

    //Scan Build Artifacts in Xray
    stage('Xray Scan') {
        if (XRAY_SCAN == "YES") {
            java.util.LinkedHashMap<java.lang.String, java.lang.Boolean> xrayConfig = [
                    'buildName' : env.JOB_NAME,
                    'buildNumber' : env.BUILD_NUMBER,
                    'failBuild' : false
            ]
            def xrayResults = server.xrayScan xrayConfig

            if (xrayResults.isFoundVulnerable()) {
                error('Stopping early… got Xray issues ')
            }
        } else {
            println "No Xray scan performed. To enable set XRAY_SCAN = YES"
        }
    }

    stage('Promote Docker image') {
        java.util.LinkedHashMap<java.lang.String, java.lang.Object> promotionConfig = [
                'buildName'  : buildInfo.name,
                'buildNumber': buildInfo.number,
                'targetRepo' : "docker-prod-local",
                'comment'    : 'This is a stable docker image',
                'status'     : 'Released',
                'sourceRepo' : 'docker-stage-local',
                'copy'       : true,
                'failFast'   : true
        ]

        server.promote promotionConfig
    }
}