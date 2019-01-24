server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'node', image: 'node:8', command: 'cat', ttyEnabled: true)]) {

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
    }
}