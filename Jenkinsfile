pipeline {
  agent any
  stages {
    stage('pull code') {
      steps {
        git(url: 'https://github.com/myqonnt/bk-repo.git', branch: 'master')
      }
    }

    stage('build') {
      steps {
        withGradle()
        sh '''cd src/backend/
./gradlew bootJar'''
      }
    }

  }
}