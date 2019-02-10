
import groovy.json.JsonSlurper

private getLatestArtifact(serverUrl ,repoName ,artifactMatch ,type) {
    def aqlString = 'items.find ({ "repo":"' + repoName + '", "path":{"\$match":"' + artifactMatch + '"},' +
            '"name":{"\$match":' + type + '"}' +
            '}).include("created","path","name").sort({"\$desc":["created"]}).limit(1)'


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

def getLatestArtifactName(serverUrl ,repo ,artifact ,type) {
    def artifactInfo = getLatestArtifact(serverUrl , repo , artifact , type)
    return artifactInfo ? artifactInfo.name:"latest"
}

def downloadArtifact(serverUrl ,repo ,artifact ,type , buildInfo ,explode) {
    def artifactInfo = getLatestArtifact(serverUrl , repo , artifact , type)
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

return this
