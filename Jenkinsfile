// Declarative pipeline for spring-annotations-demo
// Flow: Checkout -> Build -> Lint -> Test -> SonarQube -> Quality Gate -> Package
pipeline {
    agent any                                   // run on any executor (here: the controller itself)

    parameters {
        booleanParam(name: 'RUN_SONAR', defaultValue: true,
                     description: 'Untick until SonarQube is configured in Manage Jenkins')
    }

    options {
        timestamps()
        timeout(time: 20, unit: 'MINUTES')      // kill hung builds
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    environment {
        MVN = 'mvn -B -ntp'                     // batch mode, no download-progress spam
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm                    // the repo/branch configured on the job
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'java -version && mvn -version'
                sh "${MVN} clean compile"
            }
        }

        stage('Lint') {
            // Checkstyle: fails on severity=error rules. SpotBugs: report only.
            steps {
                sh "${MVN} checkstyle:check spotbugs:spotbugs"
            }
            post {
                always {
                    recordIssues(enabledForFailure: true, tools: [
                        checkStyle(pattern: 'target/checkstyle-result.xml'),
                        spotBugs(pattern: 'target/spotbugsXml.xml')
                    ])
                }
            }
        }

        stage('Test') {
            steps {
                sh "${MVN} test"                // jacoco report is bound to the test phase
            }
            post {
                always {
                    junit allowEmptyResults: false, testResults: 'target/surefire-reports/*.xml'
                    recordCoverage(tools: [[parser: 'JACOCO', pattern: 'target/site/jacoco/jacoco.xml']])
                }
            }
        }

        stage('SonarQube Analysis') {
            when { expression { params.RUN_SONAR } }
            steps {
                // 'sonarqube' = server name in Manage Jenkins > System > SonarQube servers.
                // Injects SONAR_HOST_URL + token; no secrets in this file.
                withSonarQubeEnv('sonarqube') {
                    sh "${MVN} sonar:sonar"
                }
            }
        }

        stage('Quality Gate') {
            when { expression { params.RUN_SONAR } }
            steps {
                // Waits for Sonar's webhook callback; red build if the gate fails.
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Package') {
            steps {
                sh "${MVN} package -DskipTests"  // tests already ran above
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }
    }

    post {
        success { echo 'Pipeline green: build, lint, tests, quality gate passed.' }
        failure { echo 'Pipeline red: open the failed stage log.' }
        cleanup { cleanWs() }
    }
}
