#!/usr/bin/env groovy

// Assume that the zip artefact has the same name as the Jenkins job
String target_file = "${env.JOB_BASE_NAME}.zip"

pipeline {
    agent { label 'docker' }

    stages {
        stage('Build artefact') {
            agent {
                dockerfile {
                    // Cache sbt dependencies
                    args "-v /tmp/.sbt:/root/.sbt -u root:root"
                }
            }
            steps {
                sh(script: "sbt test")
                sh(script: "sbt assembly")
                stash(
                    name: 'artefact',
                    includes: target_file
                )
            }
        }
        stage('Generate sha256') {
            steps {
                unstash(name: 'artefact')
                sh("openssl dgst -sha256 -binary ${target_file} | openssl enc -base64 > ${env.JOB_BASE_NAME}.zip.base64sha256")
                stash(name: 'base64sha256', includes: "${env.JOB_BASE_NAME}.zip.base64sha256")
            }
        }
        stage('Upload to s3') {
            steps {
                unstash('base64sha256')
                script {
                    hash = sh(returnStdout: true, script: "cat ${env.JOB_BASE_NAME}.zip.base64sha256").trim().replaceAll("[^A-Za-z0-9]","").substring(0, 8)                }
                sh(
                    """
                    aws s3 cp ${target_file} \
                        s3://mdtp-lambda-functions-integration/${env.JOB_BASE_NAME}/${env.JOB_BASE_NAME}_${hash}.zip \
                        --acl=bucket-owner-full-control --only-show-errors
                    aws s3 cp ${env.JOB_BASE_NAME}.zip.base64sha256 \
                        s3://mdtp-lambda-functions-integration/${env.JOB_BASE_NAME}/${env.JOB_BASE_NAME}_${hash}.zip.base64sha256 \
                        --content-type text/plain --acl=bucket-owner-full-control --only-show-errors
                    """
                )
            }
        }
        stage('Deploy to Integration') {
            steps {
                unstash('base64sha256')
                script {
                    hash = sh(returnStdout: true, script: "cat ${env.JOB_BASE_NAME}.zip.base64sha256").trim().replaceAll("[^A-Za-z0-9]","").substring(0, 8)
                }
                build(
                    job: 'api-platform-admin-api/deploy_lambda_version',
                    parameters: [
                        [$class: 'StringParameterValue', name: 'ARTEFACT', value: env.JOB_BASE_NAME],
                        [$class: 'StringParameterValue', name: 'HASH', value: hash],
                        [$class: 'BooleanParameterValue', name: 'ACTIVATE_INTEGRATION', value: true],
                    ]
                )
            }
        }
    }
}
