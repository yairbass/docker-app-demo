server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true , privileged: true)],
        volumes: [hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

    node('jenkins-pipeline') {

        stage('Cleanup') {
            cleanWs()
        }

        stage('Clone sources') {
            git url: 'https://github.com/eladh/docker-app-demo.git', credentialsId: 'github'
        }

        stage('Download Dependencies') {
            try {
                def pipelineUtils = load 'pipelineUtils.groovy'
                pipelineUtils.downloadArtifact(rtFullUrl, "gradle-local", "*demo-gradle/*", "jar", buildInfo, false)
                pipelineUtils.downloadArtifact(rtFullUrl, "npm-local", "*client-app*", "tgz", buildInfo, true)
            } catch (Exception e) {
                println "Caught Exception during resolution. Message ${e.message}"
                throw e as java.lang.Throwable
            }
        }

        stage('Docker build') {
            def rtDocker = Artifactory.docker server: server

            container('docker') {
                docker.withRegistry("https://docker.artifactory.jfrog.com", 'artifactorypass') {
                    dockerImageTag = "docker.artifactory.jfrog.com/docker-app:${env.BUILD_NUMBER}"
                    def dockerImageTagLatest = "docker.artifactory.jfrog.com/docker-app:latest"

                    buildInfo.env.capture = true

                    docker.build(dockerImageTag)
                    docker.build(dockerImageTagLatest)


                    rtDocker.push(dockerImageTag, "docker-local", buildInfo)
                    rtDocker.push(dockerImageTagLatest, "docker-local", buildInfo)
                    server.publishBuildInfo buildInfo
                }
            }
        }
    }
}

podTemplate(label: 'dind-template' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'dind', image: 'odavid/jenkins-jnlp-slave:latest',
                command: '/usr/local/bin/wrapdocker', ttyEnabled: true , privileged: true)]) {

    node('dind-template') {
        stage('Docker dind') {
            container('dind') {
                configFileProvider(
                        [configFile(fileId: 'private_key', variable: 'private_key')]) {
                    sh 'mkdir -p /etc/docker/certs.d/docker.artifactory.jfrog.com'
                    sh "cat ${env.private_key} >> /etc/docker/certs.d/docker.artifactory.jfrog.com/artifactory.crt"
                }

                docker.withRegistry("https://docker.artifactory.jfrog.com", 'artifactorypass') {
                    sh("docker ps")
                    tag = "docker.artifactory.jfrog.com/docker-app:${env.BUILD_NUMBER}"

                    docker.image(tag).withRun('-p 9191:81 -e “SPRING_PROFILES_ACTIVE=local” ') { c ->
                        sleep 10
                        def stdout = sh(script: 'wget "http://localhost:9191/index.html"', returnStdout: true)
                        println stdout
                        if (stdout.contains("client-app")) {
                            println "*** Passed Test: " + stdout
                            println "*** Passed Test"
                            return true
                        } else {
                            println "*** Failed Test: " + stdout
                            return false
                        }
                    }
                }
            }
        }
    }
}


podTemplate(label: 'promote-template' , cloud: 'k8s' , containers: []) {

    node('promote-template') {
        stage('Xray') {
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
}



