server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'dind', image: 'odavid/jenkins-jnlp-slave:latest', envVars: [envVar(key: 'DIND', value: 'true')]
                ,command: '/usr/local/bin/wrapdocker', ttyEnabled: true , privileged: true),
        containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true , privileged: true),
        containerTemplate(name: 'node', image: 'node:8', command: 'cat', ttyEnabled: true)
] ,volumes: [
        hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')]) {

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
                    sh 'chmod 777 /var/run/docker.sock'
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
        containerTemplate(name: 'dind', image: 'odavid/jenkins-jnlp-slave:latest', envVars: [envVar(key: 'DIND', value: 'true')]
                ,command: '/usr/local/bin/wrapdocker', ttyEnabled: true , privileged: true)]) {

    node('dind-template') {
        stage('Docker dind') {
            container('dind') {
                    sh("docker ps")
            }
        }
    }
}