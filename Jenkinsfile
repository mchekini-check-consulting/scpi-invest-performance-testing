node("ci-node") {

    stage("checkout") {
        checkout scm
    }

    stage("Performance Testing") {
        sh "chmod 777 mvnw"
        sh "./mvnw gatling:test -DuserName=$userName -Dpassword=$password -DnbUsers=$nbUsers -Dduration=$duration -Denvironment=$environment"
    }

    stage("Archive Report") {
        gatlingArchive()
    }


}