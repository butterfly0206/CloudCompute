# The amzl2-cloudosv2-base image has Python 3.10 installed already
FROM artifactory.dev.adskengineer.net/autodeskcloud/amzl2/amzl2-cloudosv2-base:latest

LABEL maintainer="chimera <chimera@autodesk.com>"

USER ctr-user
WORKDIR /opt
COPY worker/Pipfile* ./
RUN python -m pip install pipenv certifi
RUN python -m pipenv install

ENV PATH=/home/ctr-user/.local/bin:${PATH}

COPY --chown=ctr-user:ctr-user swagger/2.0/worker-manager.yaml worker/src/main/worker.py worker/src/main/input-schema.json ./
COPY --chown=ctr-user:ctr-user --chmod=0755 worker/app.sh ./

# Remove Python 2 as it's causing a lot of vulnerabilities and it will never be replaced by Python 3.
USER root
RUN rpm -e yum yum-plugin-ovl yum-plugin-priorities python pyxattr python-urlgrabber python2-rpm pygpgme pyliblzma \
    amazon-linux-extras yum-metadata-parser python-pycurl python-iniparse \
    && rm -rf /usr/lib64/python2.7
USER ctr-user

ENTRYPOINT ["/opt/app.sh"]
CMD ["python3", "worker.py"]
