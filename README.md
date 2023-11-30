[![Build Status](https://master-4.jenkins.autodesk.com/buildStatus/icon?job=cloud-platform%2Ffpccomp%2Fmain)](https://master-4.jenkins.autodesk.com/job/cloud-platform/job/fpccomp/job/main/)
[![Portfolio Status](https://master-4.jenkins.autodesk.com/buildStatus/icon?job=cloud-platform%2Ffpccomp-app%2Fmain&subject=portfolio)](https://master-4.jenkins.autodesk.com/job/cloud-platform/job/fpccomp-app/job/main/)

# CloudOS-compute

Platform for batch style computational services

## Build dependencies

Clone embrace-gatekeeper repo locally, build it and then run:
```shell
mvn clean install -DskipTests install:install-file -Dfile=./common/target/common-0.1.0.jar -DpomFile=./common/pom.xml
```

Clone the adf-collection repo:
```shell
mkdir common/src/test/resources/adf-collection
cd common/src/test/resources/adf-collection
git clone git@git.autodesk.com:cloud-platform/adf-collection.git
```

## Update dependencies

Run update-java-dependencies.sh and make sure no beta or alpha or similar are updated. If they are, add the suffix that you
wish to ignore to rules.xml.

Some artifacts (like testng) are ignored because the minor version update broke the code. If there's a vulnerability to
be addressed on these libraries, you'll need to update the code.

## Running Redis

Start an optional redis instance testing:
`docker run --rm --detach --name redis --publish 6379:6379 redis`

## Documentation

https://pages.git.autodesk.com/cloud-platform/fpccomp
