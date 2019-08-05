node("${env.BUILDSERVER}") {
    try {                                                                                                           // When block ends, executing 'finally' block
        wrap([$class: 'TimestamperBuildWrapper']) {                                                                 // Build timestamps
            wrap ([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {                                       // Сolor console
                // Setting values for a build
                def buildRoot = "${env.distributives_dir}/${env.UPSTREAM}"
                def gitRoot = "${env.gitrepo_dir}/${env.UPSTREAM}_${env.JOB_NAME}"

                hybrisStack = "${env.gitrepo_dir}/${env.devopsRepoDir}/env/ansible/stacks/hybris6-stack"
                hybrisConfigName = 'ua_build_migration_pipe_st-default'
                hybrisInventoryName = 'ua/build_migration_pipe_st'
                hybrisAnsibleSshUser = 'ctco-fgls'
                // Setting values for a build and for build environment
                env.PATH="${tool 'Maven_325'}/bin/:/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:${buildRoot}/hybris/bin/platform/apache-ant-1.9.1/bin"
                env.JAVA_HOME="${tool 'java8'}"
                env.ANT_HOME="${buildRoot}/hybris/bin/platform/apache-ant-1.9.1"
                env.ANT_OPTS=' -Xms2096m -Xmx2192m -Dfile.encoding=UTF-8'
                env.MAVEN_OPTS = ' -DskipTests=true -Xms512m -Xmx1024m -XX:MaxPermSize=512m'
                env.SONAR_RUNNER_OPTS='-Xmx1024m -XX:MaxPermSize=512m'
                env.ATMO_HYBRIS_HOME="${buildRoot}/hybris"
                env.PYTHONUNBUFFERED=1
                // Loading all of functions, credentials, etc
                stage ('Load functions') {
                    parallel 'creds' :{
                        creds = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/credentials.groovy"
                    }, 'hybrisClean' :{
                        hybrisClean = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/clean.groovy"
                    }, 'hybrisBuildUtils' :{
                        hybrisBuildUtils = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/buildutils.groovy"
                    }, 'hybrisBuild' :{
                        hybrisBuild = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/build.groovy"
                    }, 'hybrisPackage' :{
                        hybrisPackage = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/package.groovy"
                    }, 'hybrisTest' :{
                        hybrisTest = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/hybristest.groovy"
                    }, 'hybrisSnapshot' :{
                        hybrisSnapshot = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/stages/hybris/snapshot.groovy"
                    }, 'flowVars' :{
                        flowVars = load "${env.gitrepo_dir}/${env.devopsRepoDir}/env/jenkins/jobs/${env.UPSTREAM}/flowvars.groovy"
                    }
                    creds.getGitConnection()
                    creds.getEpamArtifactoryConnection()
                    creds.getFGLArtifactoryConnection()
                    flowVars.getHybrisFlowVars()
                }

                dir(gitRoot) {
                    stage ('Git Checkout') {
                        sh 'find hybris-ext/custom-ext/ -name *java -exec rm -f {} \\;'
                        retry(2) {
                            checkout([
                                    $class: 'GitSCM',
                                    branches: [[name: "refs/heads/${env.BRANCH}"]],
                                    doGenerateSubmoduleConfigurations: false,
                                    extensions: [[$class: 'DisableRemotePoll']],
                                    submoduleCfg: [],
                                    changelog: false,
                                    userRemoteConfigs: [[credentialsId: "${creds.gitCredentialsId}", url: "${creds.gitUrl}"]]
                            ])
                        }
                    }
                }

                dir(buildRoot + '/hybris') {
                    artifactName = '1.' + "${env.BRANCH}.${env.VERSION}"
                    println 'artifactName: ' + artifactName

                    stage ('Hybris Clean') {
                        hybrisClean.linkProperties(gitRoot)
                        hybrisClean.antClean(gitRoot)
                        // if ( env.BRANCH == 'ecomm' ) { sh 'if [ -f "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/AuditMedia.java" ]; then rm "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/AuditMedia.java"; fi' }
                        if ( env.BRANCH == 'fcss' || env.BRANCH == 'ppe' || env.BRANCH == 'develop' || env.BRANCH == 'release-freeze-52' ) { 
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/DefaultVariantProduct.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/Dimension.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/DimensionValue.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/GiftCardVariantProduct.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/MerchandiseVariantProduct.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/PromoCardVariantProduct.java"'
                        }
                        if ( env.BRANCH == 'mscb' ){
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/ItemPromotionInfo.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/PromotionConsumedEntry.java"'
                            sh 'rm -rf "hybris-ext/custom-ext/fglcore/src/com/fglsports/core/jalo/PromotionInfo.java"'
                            sh "${tool 'Maven_325'}/bin/mvn -DSNAPSHOT_VERSION=${artifactName} -Dversion=${artifactName} -Durl=${creds.artifFGLSUrl}/${creds.artifFGLSReleases} -DgroupId='marks.hybris' -Dfile=hybris-ext/custom-ext/fglcommercewebservices/resources/wadl/dto.xsd -DartifactId=multi-tenancy-application.xsd -Dpackaging=xsd -DgeneratePom=true -DrepositoryId=${creds.artifFGLSReleases} deploy:deploy-file -U"
                            sh "${tool 'Maven_325'}/bin/mvn -DSNAPSHOT_VERSION=LATEST -Dversion=LATEST -Durl=${creds.artifFGLSUrl}/${creds.artifFGLSReleases} -DgroupId='marks.hybris' -Dfile=hybris-ext/custom-ext/fglcommercewebservices/resources/wadl/dto.xsd -DartifactId=multi-tenancy-application.xsd -Dpackaging=xsd -DgeneratePom=true -DrepositoryId=${creds.artifFGLSReleases} deploy:deploy-file -U"
                        }
                    }

                    dir(buildRoot + '/hybris/utils') {
                        stage ('Hybris BuildUtils') {
                            hybrisBuildUtils.deploy("${tool 'Maven_325'}", "${creds.artifFGLSUrl}", "${creds.artifFGLSReleases}")
                        }
                    }

                    stage ('Hybris Build') {
                        hybrisBuild.build(gitRoot)
                    }

                    dir(hybrisStack) {
                        stage ('Hybris Package') {
                            hybrisPackage.generateConfig2(buildRoot, hybrisConfigName, "${env.devopsRepoDir}")
                            hybrisPackage.hybrisPackaging(gitRoot, buildRoot, hybrisInventoryName, hybrisAnsibleSshUser)
                        }
                    }

                    dir(buildRoot + '/hybris/bin/platform')   {
                        stage ('Hybris Test') {
                            if ( env.TESTS == 'false' ) { println 'Running tests were skipped' }
                            else {
                                println 'Running tests'
                                /*
                                    try {
                                        hybrisTest.antJacoco(buildRoot, gitRoot)
                                    } catch(err) {
                                        println 'Error: ' + err
                                        currentBuild.result = 'FAILURE'
                                        throw err
                                    } finally {
                                        println 'NOT Executing JUnitResultArchiver...'
                                        //step([$class: 'JUnitResultArchiver', testResults: "TESTS-*.xml"])
                                    }
                                */
                            }
                        }

                        stage ('Hybris Snapshot') {
                            sh "pwd"
                            hybrisSnapshot.snapshotUploadEpam("${tool 'Maven_325'}", "${artifactName}", "${creds.artifFGLSUrl}", "${creds.artifFGLSReleases}", "${flowVars.artifHybrisGroup}")
                            if ( env.FGLUPLOAD == 'true' ) {
                                hybrisSnapshot.snapshotUploadFGL("${tool 'Maven_325'}", "${artifactName}", "${creds.artifFGLUrl}", "${creds.artifFGLReleases}", "${flowVars.artifHybrisGroup}")
                                println 'Backup FGL artifact to EPAM Artifactory'
                                hybrisSnapshot.snapshotUploadEpam("${tool 'Maven_325'}", "${artifactName}", "${creds.artifFGLSUrl}", "${creds.artifFGLSReleases}", "${flowVars.artifHybrisGroupBackup}")
                            }
                        }
                    }
                }
            }
        }
    } finally {
        println 'Current Build Result: ' + currentBuild.result + '\nCommiters are: ' + "${COMMITERS}"
        step([$class: 'Mailer', notifyEveryUnstableBuild: true, sendToIndividuals: true, recipients: "${COMMITERS}"])
    }
}

