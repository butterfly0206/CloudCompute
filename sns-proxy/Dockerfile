FROM haproxy:alpine
COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg
EXPOSE 8080
USER root
RUN apk upgrade
USER haproxy
CMD ["haproxy", "-f",  "/usr/local/etc/haproxy/haproxy.cfg", "-m", "256"]
