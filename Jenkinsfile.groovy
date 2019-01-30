server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'dind-template' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'dind', image: 'odavid/jenkins-jnlp-slave:latest', envVars: [envVar(key: 'DIND', value: 'true')]
                ,command: 'tiny -- /entrypoint.sh', ttyEnabled: true , privileged: true)]) {

    node('dind-template') {
        stage('Docker dind') {
            container('dind') {
                    sh("docker ps")
            }
        }
    }
}