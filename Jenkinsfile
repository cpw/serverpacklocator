@Library('forge-shared-library')_

pipeline {
    agent {
        docker {
            image 'gradlewrapper:latest'
            args '-v gradlecache:/gradlecache'
        }
    }
    environment {
        GRADLE_ARGS = '-Dorg.gradle.daemon.idletimeout=5000 -Preckon.scope=patch'
    }

    stages {
        stage('fetch') {
            steps {
                checkout scm
            }
        }
        stage('buildandtest') {
            steps {
                sh './gradlew ${GRADLE_ARGS} --refresh-dependencies --continue build test'
                script {
                    env.MYVERSION = sh(returnStdout: true, script: './gradlew properties -q | grep "version:" | awk \'{print $2}\'').trim()
                }
            }
            post {
                success {
                    writeChangelog(currentBuild, 'build/changelog.txt')
                    archiveArtifacts artifacts: 'build/changelog.txt', fingerprint: false
                }
            }
        }
        stage('publish') {
            when {
                not {
                    changeRequest()
                }
            }
            environment {
                FORGE_MAVEN = credentials('forge-maven-cpw-user')
            }
            steps {
                sh './gradlew ${GRADLE_ARGS} publish -PcpwMavenUser=${FORGE_MAVEN_USR} -PcpwMavenPassword=${FORGE_MAVEN_PSW}'
                sh 'curl --user ${FORGE_MAVEN} http://files.minecraftforge.net/maven/manage/promote/latest/cpw.mods.forge.serverpacklocator/${BUILD_NUMBER}'
            }
        }
    }
    post {
        always {
          archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true
          jacoco sourcePattern: '**/src/*/java'
        }
    }
}