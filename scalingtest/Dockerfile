#TO RUN LOCALLY: 
#   docker run -it -v $(pwd):/local -e APP_URL=fpccomp-c-uw2-sb.cosv2-c-uw2-sb.autodesk.com/ $IMAGE_ID \
#   /bin/sh -c './run_tests.sh && cp ./test-results.xml /local/'
FROM autodesk-docker-build-images.art-bobcat.autodesk.com/python36-pyinstaller-centos7:latest

RUN ["yum", "-y", "makecache", "fast"]
RUN ["yum", "-y", "install", "which"]

WORKDIR /app
COPY scalingtest/python/Pipfile .
COPY swagger/3.0/job-manager.yaml swagger/3.0/worker-manager.yaml ./
RUN ["pipenv", "install"]

COPY scalingtest/run_tests.sh ./run_tests.sh
COPY scalingtest/python/tests.py ./tests.py

ENTRYPOINT [ "/app/run_tests.sh" ]
