#!groovy
@Library('PSL@LKG') _

properties([
    [$class: 'ParametersDefinitionProperty', parameterDefinitions: [
    [$class      : 'BooleanParameterDefinition',
     defaultValue: false,
     description : '[Build] Publish artifacts (main will always publish; others are optional)',
     name        : 'PUBLISH_ARTIFACTS'
    ],
    [$class      : 'BooleanParameterDefinition',
     defaultValue: false,
     description : '[Build] Update build image (will only push manually)',
     name        : 'UPDATE_BUILD_IMAGE'
    ],
    [$class      : 'BooleanParameterDefinition',
     defaultValue: false,
     description : '[Deploy] Deploy Lambdas using Cloud OS v3 Lambda Pipeline',
     name        : 'DEPLOY_LAMBDA'
    ]

]]])

def triggering_branches = ['main']
def publish_artifacts = (env.BRANCH_NAME in triggering_branches) || params.PUBLISH_ARTIFACTS
def update_build_image = params.UPDATE_BUILD_IMAGE

def get_aws_credentials(vaultToken) {
    docker.image("vault:0.7.3").pull()
    docker.image("vault:0.7.3").inside("-u root") {
        sh """
            export VAULT_ADDR=https://civ1.dv.adskengineer.net:8200
            set +x
            export VAULT_TOKEN=${vaultToken}
            set -x
            export VAULT_SKIP_VERIFY=0
            rm -rf raw_credentials aws_credentials || true
            vault write cosv2-c-uw2-sb/cosv2-c-uw2-sb/aws/sts/deploy ttl=30m > raw_credentials
            echo '[cosv2-c-uw2-sb]' > aws_credentials
            echo -n 'aws_access_key_id=' >> aws_credentials
            cat raw_credentials | grep access_key | awk '{print \$2}' >> aws_credentials
            echo -n 'aws_secret_access_key=' >> aws_credentials
            cat raw_credentials | grep secret_key | awk '{print \$2}' >> aws_credentials
            echo -n 'aws_session_token=' >> aws_credentials
            cat raw_credentials | grep security_token | awk '{print \$2}' >> aws_credentials
            chown 1001:1001 aws_credentials
        """
    }
}

// If specific, first update the build image
// Then build compute. Then fan out and build a bunch of containers.

node('aws-centos') {
    stage('Sync') {
        checkout scm    //pull down the source
    }
    stage('ADF pull') {
        stage("Download adf-collection test dependency") {
            dir('common/src/test/resources/adf-collection') {
                git branch: "main", url: 'https://git.autodesk.com/cloud-platform/adf-collection.git', credentialsId: 'ors_git_service_account'
            }
            sh 'ls -alt'
        }
    }

    // set up
    def common_cd = new cloudos.common_portfolio(steps, env, docker, Artifactory)
    def common_ci = new ors.ci.common_ci(steps, env, docker)
    def common_scm = new ors.utils.common_scm(steps, env)
    def common_sonar = new ors.utils.CommonSonar(steps, env, docker)
    def common_harmony = new ors.security.common_harmony(steps, env, Artifactory, scm)
    def app_package_and_or_publish = publish_artifacts ? common_ci.&app_package_and_publish : common_ci.&app_package

    // clean workspace
    common_scm.clean_workspace()

    def pwd = pwd() //get workspace root dir

    // define vars needed get the credentials from vault
    def ors.utils.CommonVault commonVault = new ors.utils.CommonVault(steps, env, docker)
    def vaultToken = commonVault.get_vault_token('cosv2-service-user', '1h')
    def groovy.lang.GString awsCredentialsFile = "${pwd}/aws_credentials"

    stage('Update build image') {
        if (update_build_image) {
            common_ci.app_package_and_publish(
                    dockerfile_path: 'Dockerfile',
                    deployable_image: common_cd.get_deployable_image() + "_build_image",
                    update_latest: true,
                    run_image_signing: false
            )
        }
    }
    stage('Get temporary AWS credentials from Vault') {
        get_aws_credentials(vaultToken)
    }
    def groovy.lang.Closure build_cmd = {
        try {
            stage('Compile') {
                sh """
                mkdir -p ${pwd}/local-repo
                set +x
                export VAULT_TOKEN=${vaultToken}
                set -x
                export AWS_CREDENTIAL_PROFILES_FILE=${awsCredentialsFile}
                mvn -B clean install -Dmaven.repo.local=/local-repo
                """
            }
        } finally {
            junit keepLongStdio: true, allowEmptyResults: true, testResults: '**/target/*-reports/*.xml'
            // jacoco classPattern: '**/target/classes', sourcePattern: '**/src/*/java', sourceExclusionPattern: '**/src/test/java', execPattern: 'coverage/jacoco.exec'
            publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'coverage/target/coverage-report/html', reportFiles: 'index.html', reportName: 'Coverage Report (aggregate)', reportTitles: ''])
            stage('Sonar Scan') {
                archiveArtifacts 'coverage/target/coverage-report/coverage-report.xml'
                sh 'ls -alR coverage/'
                common_sonar.do_maven_scan(debug: true, trunk: "main", more_args: "-Dsonar.exclusions=**/gen/**/*,**/model/**/*,test-common/**/* -Dmaven.repo.local=/local-repo -Dsonar.coverage.jacoco.xmlReportPaths=${pwd}/coverage/target/coverage-report/coverage-report.xml")
            }
        }
    }
    if (!(env.BRANCH_NAME.startsWith("PR-") || triggering_branches.contains(env.BRANCH_NAME) || publish_artifacts)) {
        echo "Skipping stages for branches that are not PR or selected PUBLISH_ARTIFACTS"
    } else {
        parallel(
            failFast: true,
            'Application and other containers': {
                stage("Build Job Manager and Worker Manager and lambda functions") {
                    sh """
                    echo ${env.BRANCH_NAME}-${common_scm.hash()} > REVISION
                    echo ${env.BRANCH_NAME}-${common_scm.hash()} > job-manager/REVISION
                    echo ${env.BRANCH_NAME}-${common_scm.hash()} > worker-manager/REVISION
                """
                    try {
                        common_ci.app_build(  //perform the build using PSL
                                docker_registry: 'https://artifactory.dev.adskengineer.net/artifactory/docker-local-v2/',
                                docker_credentials: 'cosv2_artifactory_user',
                                build_image: "artifactory.dev.adskengineer.net/autodeskcloud/fpccomp_build_image:latest",
                                build_script: build_cmd,
                        )
                        //archive the test results
                        step([$class: 'JUnitResultArchiver', testResults: '**/target/*-reports/*.xml', keepLongStdio: true])
                    }
                    catch (e) {
                        //archive the test results
                        step([$class: 'JUnitResultArchiver', testResults: '**/target/*-reports/*.xml', keepLongStdio: true])
                        error("FAILED TO BUILD APP!: " + e)
                    }
                    finally {
                        archiveArtifacts '**/surefire-reports/*.txt'
                    }

                }
                parallel(
                    failFast: true,
                    'Batch worker build': {
                        stage("Build Batch Worker for Testing") {
                            dir('.') {
                                withEnv(["DOCKER_BUILDKIT=1"]) {
                                    app_package_and_or_publish(
                                            dockerfile_path: 'worker/Dockerfile',
                                            deployable_image: common_cd.get_deployable_image() + "_worker",
                                            run_image_signing: true
                                    )
                                }
                            }
                        }
                    },
                    'Batch service integration worker': {
                        stage("Build Batch Service Integration Worker for Testing") {
                            dir('.') {
                                withEnv(["DOCKER_BUILDKIT=1"]) {
                                    app_package_and_or_publish(
                                            dockerfile_path: 'worker/Dockerfile',
                                            deployable_image: common_cd.get_deployable_image() + "_siworker",
                                            run_image_signing: true
                                    )
                                }
                            }
                        }
                    },
                    'Build Release-Test Component': {
                        stage("Build Release-Test Component") {
                            dir('.') {
                                app_package_and_or_publish(
                                        dockerfile_path: 'test/Dockerfile',
                                        deployable_image: common_cd.get_deployable_image() + "-test",
                                        run_image_signing: true
                                )
                            }
                        }
                    },
                    'Publish job manager docker container': {
                        stage("Publish deployable image job-manager") {
                            sh "cp common/alpinecosv2Entry.sh ./job-manager"
                            sh "cp common/newrelic.yml ./job-manager"
                            app_package_and_or_publish(dockerfile_path: './job-manager/Dockerfile',
                                    docker_context_path: './job-manager',
                                    deployable_image: common_cd.get_deployable_image() + "_jm",
                                    run_scan: true,
                                    run_image_signing: true)
                        }
                    },
                    'Publish worker manager docker container': {
                        stage("Publish deployable image worker-manager") {
                            sh "cp common/alpinecosv2Entry.sh ./worker-manager"
                            sh "cp common/newrelic.yml ./worker-manager"
                            app_package_and_or_publish(dockerfile_path: './worker-manager/Dockerfile',
                                    docker_context_path: './worker-manager',
                                    deployable_image: common_cd.get_deployable_image() + "_wm",
                                    run_scan: true,
                                    run_image_signing: true)
                        }
                    },
                    'Publish relay': {
                        stage("Publish deployable image relay") {
                            sh "cp common/newrelic.yml ./sns-proxy"
                            app_package_and_or_publish(dockerfile_path: './sns-proxy/Dockerfile',
                                    docker_context_path: './sns-proxy',
                                    deployable_image: common_cd.get_deployable_image() + "_relay",
                                    run_scan: true,
                                    run_image_signing: true)
                        }
                    },
                    'Publish job completion lambda': {
                        if (publish_artifacts) {
                            stage("Publish job completion lambda") {
                                dir("completion-lambda/target")
                                        {
                                            common_ci.artifact_publish(artifact_name: "completion.jar", artifact_local_path: 'completion-lambda-1.0.0.jar', Artifactory: Artifactory)
                                        }
                            }
                        }
                    },
                    'Publish job test lambda': {
                        if (publish_artifacts) {
                            stage("Publish Job test lambda") {
                                dir("test-lambda/target")
                                        {
                                            common_ci.artifact_publish(artifact_name: "testjobs.jar", artifact_local_path: 'test-lambda-1.0.0.jar', Artifactory: Artifactory)
                                        }
                            }
                        }
                    },
                    'Publish cleanup lambda': {
                        if (publish_artifacts) {
                            stage("Publish resource cleanup lambda") {
                                dir("cleanup-lambda") {
                                    sh "zip -j clean_resources.zip ./clean_resources.py"
                                    common_ci.artifact_publish(artifact_name: "clean_resources.zip", artifact_local_path: "clean_resources.zip", Artifactory: Artifactory)
                                }
                            }
                        }
                    },
                    'Harmony scan': {
                        stage('Third Party Libraries Scan') {
                            sh "mkdir ${env.WORKSPACE}/harmony"
                            sh "mkdir ${env.WORKSPACE}/harmony/worker-manager"
                            sh "mkdir ${env.WORKSPACE}/harmony/job-manager"
                            sh "mkdir ${env.WORKSPACE}/harmony/test-lambda"
                            sh "mkdir ${env.WORKSPACE}/harmony/completion-lambda"
                            sh "cp -R ${env.WORKSPACE}/worker-manager/target ${env.WORKSPACE}/harmony/worker-manager"
                            sh "cp -R ${env.WORKSPACE}/job-manager/target ${env.WORKSPACE}/harmony/job-manager"
                            sh "cp -R ${env.WORKSPACE}/test-lambda/target ${env.WORKSPACE}/harmony/test-lambda"
                            sh "cp -R ${env.WORKSPACE}/test-lambda/target ${env.WORKSPACE}/harmony/completion-lambda"
                            sh "rm -rf ${env.WORKSPACE}/harmony/worker-manager/target/surefire-reports"
                            sh "rm -rf ${env.WORKSPACE}/harmony/job-manager/target/surefire-reports"
                            sh "rm -rf ${env.WORKSPACE}/harmony/test-lambda/target/surefire-reports"
                            sh "rm -rf ${env.WORKSPACE}/harmony/completion-lambda/target/surefire-reports"
                            common_harmony.run_scan(
                                    "repository": "cloud-platform/fpccomp",
                                    "product_output": "${env.WORKSPACE}/harmony",
                                    "branch": env.BRANCH_NAME,
                                    "fail_on_oast": false,
                                    // The following are optional
                                    // Add user emails here to get access to https://whitesource.autodesk.com
                                    "report_access": "santiago.gomez@autodesk.com,dave.watt@autodesk.com,neeharika.taneja@autodesk.com,milind.mistry@autodesk.com,jeffrey.klug@autodesk.com"
                            )
                        }
                    },
                    'Generate documentation': {
                        if (env.BRANCH_NAME in triggering_branches) {
                            stage('Build API Documentation') {
                                build job: '/cloud-platform/fpccomp/gh-pages', wait: true
                            }
                        }
                    }
                )
            }
        )
    }
    stage("Run tests") {
        echo("Need to run tests here")
        def testImage = common_cd.get_deployable_image() + "-test"
    }
    stage("Deploy FPCCOMP Cloud OS v3 Lambda(s)") {
        if (params.DEPLOY_LAMBDA || publish_artifacts) {
            // Load the CloudOSv3 functions for lambda pipeline
            def cloudos = get_common_cosv3('v0.10')
            def serviceID = cloudos.getServiceID('cosv3-adf/app-lambda/.cloudos')
            sh "zip -j clean_resources.zip ./cleanup-lambda/clean_resources.py"
            def services = [
                'completionLambda': 'completion-lambda/target/completion-lambda-1.0.0.jar',
                'testJobsLambda': 'test-lambda/target/test-lambda-1.0.0.jar',
                'cleanupLambda': 'clean_resources.zip'
            ]
            cloudos.deploy(services, 'cosv3-adf/app-lambda/.cloudos')
        }
    }
    stage("Trigger Application CI if we published new containers") {
        if (publish_artifacts) {
            build job: '/cloud-platform/fpccomp-app/main', wait: false
        }
    }
}

def get_common_cosv3(verTag='') {
  def folder = 'common-functions'
  def commonGitURL = "https://git.autodesk.com/forge-cd-services/cosv3-groovy.git"
  sh "rm -rf ${folder} || true"
  dir(folder) {
    git url: commonGitURL, credentialsId: 'local-svc_p_ors'
    sh "git checkout tags/${verTag}"
    return load("cosv3.groovy")
  }
}
