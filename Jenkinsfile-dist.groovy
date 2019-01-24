server = Artifactory.server "artifactory-server"
rtFullUrl = server.url


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:v2.6.0', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:latest', command: 'cat', ttyEnabled: true)
],
        volumes:[
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {


    node('jenkins-pipeline') {

        stage('Cleanup') {
            cleanWs()
        }

        stage('Clone sources') {
            git url: 'http://35.202.14.162/elad/app-repo.git'
        }

        stage('Run kubectl') {
            container('kubectl') {
                sh "kubectl get pods"
            }
        }

        stage('Helm install') {
            container('helm') {
                sh 'helm ls'
            }


        }
    }
}