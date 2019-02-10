
import groovy.json.JsonSlurper

private getLatestArtifact(serverUrl ,aqlString) {
    File aqlFile = File.createTempFile("aql-query", ".tmp")
    aqlFile.deleteOnExit()
    aqlFile << aqlString

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactorypass',
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

        def getAqlSearchUrl = "curl -u$USERNAME:$PASSWORD -X POST " + serverUrl + "/api/search/aql -T " + aqlFile.getAbsolutePath()
        echo getAqlSearchUrl
        try {
            println aqlString
            def response = getAqlSearchUrl.execute().text
            println response
            def jsonSlurper = new JsonSlurper()
            def latestArtifact = jsonSlurper.parseText("${response}")

            println latestArtifact
            return new HashMap<>(latestArtifact.results[0])
        } catch (Exception e) {
            println "Caught exception finding lastest artifact. Message ${e.message}"
            throw e as java.lang.Throwable
        }
    }
}

def executeAql(serverUrl ,aqlString) {
    return getLatestArtifact(serverUrl , aqlString)
}

def downloadArtifact(serverUrl ,repo ,artifact ,type , buildInfo ,explode) {
    def aqlString = 'items.find ({ "repo":"' + repo + '", "path":{"\$match":"' + artifact + '"},' +
            '"name":{"\$match":"*.' + type + '"}' +
            '}).include("created","path","name").sort({"\$desc":["created"]}).limit(1)'

    def artifactInfo = getLatestArtifact(serverUrl ,aqlString)
    def lastArtifact = artifactInfo.path + "/" + artifactInfo.name

    def latestVer = repo +  "/" + lastArtifact

    def downloadConfig = """{
                 "files": [
                 {
                 "pattern": "${latestVer}",
                 "flat": "true",
                 "explode": "${explode}"
                 }
                 ]
             }"""

    server.download(downloadConfig, buildInfo)
}


def pushHelmChart(repo ,artifact ,version) {

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactorypass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh "jfrog rt c beta --user ${USERNAME} --password ${PASSWORD} --url ${rtFullUrl} < /dev/null"
        sh "sed -i 's/latest/${version}/g' ${artifact}/values.yaml"
        sh "helm package ./${artifact}/"
        sh "./jfrog rt u '*.${env.BUILD_NUMBER}.tgz' ${repo} --build-name=${env.JOB_NAME} --build-number=${env.BUILD_NUMBER} -server-id beta --props='release-bundle=true'"
        sh "./jfrog rt bce ${env.JOB_NAME} ${env.BUILD_NUMBER} "
        sh "./jfrog rt dl ${repo}/${artifact}/${version}/manifest.json --build-name=${env.JOB_NAME} --build-number=${env.BUILD_NUMBER} -server-id beta"
        sh "./jfrog rt bp ${env.JOB_NAME} ${env.BUILD_NUMBER} -server-id beta"
    }

}

return this
