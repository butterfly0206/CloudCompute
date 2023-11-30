FROM artifactory.dev.adskengineer.net/cloudos-community/amzl2/python39

#TO RUN LOCALLY:
#docker run -dit -e APP_URL=fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com/ -e ADF_TRANSFORM=rolling <IMAGE_ID> /bin/bash
#docker exec -it <CONTAINER_ID> /bin/bash

USER root

RUN pip install pipenv
WORKDIR /app

RUN yum -y update \
    && yum -y install tar \
    && yum -y install gzip \
    && yum clean all \
    && chown ctr-user:ctr-user /app

USER ctr-user

COPY --chown=ctr-user:ctr-user test/python/Pipfile test/python/Pipfile.lock ./

RUN ["pipenv", "install", "--system"]

COPY swagger/2.0/job-manager.yaml swagger/2.0/worker-manager.yaml test/run_tests.sh \
    test/python/tests.py test/python/worker_tests.py test/python/array_job_tests.py test/python/expectations.py \
    test/python/config.py test/python/read_cosv3_test_secrets.py ./

COPY --chown=ctr-user:ctr-user test/python/jmclient ./jmclient
COPY --chown=ctr-user:ctr-user test/python/wmclient ./wmclient

# Remove Python 2 as it's causing a lot of vulnerabilities and it will never be replaced by Python 3.
USER root
RUN rpm -e yum yum-plugin-ovl yum-plugin-priorities python pyxattr python-urlgrabber python2-rpm pygpgme pyliblzma \
    amazon-linux-extras yum-metadata-parser python-pycurl python-iniparse \
    && rm -rf /usr/lib64/python2.7
USER ctr-user

ENTRYPOINT [ "/app/run_tests.sh" ]
