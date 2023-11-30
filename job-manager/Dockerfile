FROM artifactory.dev.adskengineer.net/autodeskcloud/alpine/alpine-cloudosv2-base-openjre11:latest

MAINTAINER 'chimera <chimera@autodesk.com>'

USER root
COPY --chown=ctr-user:ctr-user ./target/libs /usr/local/libs/
COPY --chown=ctr-user:ctr-user ./target/jobmanager-1.0.0.jar /usr/local/jobmanager-1.0.0.jar
COPY --chown=ctr-user:ctr-user ./alpinecosv2Entry.sh /opt/
COPY --chown=ctr-user:ctr-user REVISION REVISION
USER ctr-user

RUN chmod +x /opt/alpinecosv2Entry.sh
ENTRYPOINT ["/opt/alpinecosv2Entry.sh"]

EXPOSE 8090

CMD ["java", "-jar", "/usr/local/jobmanager-1.0.0.jar"]
