FROM eclipse-temurin:17-jre

WORKDIR /app
ARG OCI_REVISION
ARG OCI_SOURCE
LABEL org.opencontainers.image.revision="${OCI_REVISION}" \
      org.opencontainers.image.source="${OCI_SOURCE}"
RUN test -n "${OCI_REVISION}" \
    && test "${OCI_REVISION}" != "unknown" \
    && test -n "${OCI_SOURCE}" \
    && test "${OCI_SOURCE}" != "unknown"
COPY target/rippleguard-audit-replay-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
