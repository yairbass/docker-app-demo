server = Artifactory.server "artifactory"
rtFullUrl = server.url
rtIpAddress = rtFullUrl - ~/^http?.:\/\// - ~/\/artifactory$/

buildInfo = Artifactory.newBuildInfo()


podTemplate(label: 'jenkins-pipeline' , cloud: 'k8s' , containers: [
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


        stage('Docker Integration Tests') {
            docker.image("docker:dind").withRun('-d ') { c ->
                sh 'docker ps'
            }
        }

    }
}




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


//    }