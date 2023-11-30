FROM maven:3.6-jdk-11

COPY ./settings.xml /root/.m2/settings.xml

WORKDIR /fpccomp
COPY .  /fpccomp

RUN mvn clean install -B -Dmdep.analyze.skip=true -Dmaven.test.skip=true -Dmaven.repo.local=/local-repo
# will resolve test plugins such as jacoco which were ingored in the previous install
RUN mvn -B dependency:resolve dependency:resolve-plugins -Dmdep.analyze.skip=true -Dmaven.test.skip=true -Dmaven.repo.local=/local-repo