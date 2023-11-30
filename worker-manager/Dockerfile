FROM artifactory.dev.adskengineer.net/autodeskcloud/alpine/alpine-cloudosv2-base-openjre11:latest

COPY --chown=ctr-user:ctr-user ./target/libs /usr/local/libs/
COPY --chown=ctr-user:ctr-user ./target/workermanager-1.0.0.jar /usr/local/workermanager-1.0.0.jar
COPY --chown=ctr-user:ctr-user ./alpinecosv2Entry.sh /opt/
COPY --chown=ctr-user:ctr-user REVISION REVISION

RUN chmod +x /opt/alpinecosv2Entry.sh
ENTRYPOINT ["/opt/alpinecosv2Entry.sh"]

EXPOSE 8091

CMD ["java", "-jar", "/usr/local/workermanager-1.0.0.jar"]

