
import groovy.json.JsonSlurper

private getLatestArtifact (serverUrl ,repoName ,match ,type) {
    def aqlString = 'items.find ({ "repo":"' + repoName + '", "path":{"\$match":"' + match + '"},' +
            '"name":{"\$match":"*.' + type + '"}' +
            '}).include("created","path","name").sort({"\$desc":["created"]}).limit(1)'


    File aqlFile = File.createTempFile("aql-query", ".tmp")
    aqlFile.deleteOnExit()
    aqlFile << aqlString

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactorypass',
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {

        def getAqlSearchUrl = "curl -u$USERNAME:$PASSWORD -X POST " + serverUrl + "/artifactory/api/search/aql -T " + aqlFile.getAbsolutePath()
        echo getAqlSearchUrl
        try {
            def response = getAqlSearchUrl.execute().text
            def jsonSlurper = new JsonSlurper()
            def latestArtifact = jsonSlurper.parseText("${response}")
            def path = latestArtifact.results[0].path + "/" + latestArtifact.results[0].name
            return path
        } catch (Exception e) {
            println "Caught exception finding lastest artifact. Message ${e.message}"
            throw e as java.lang.Throwable
        }
    }
}

def downloadArtifact(serverUrl ,repo ,artifact ,type , buildInfo ,explode) {
    def lastArtifact = getLatestArtifact(serverUrl , repo , artifact , type)
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

return this
