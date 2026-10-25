# syntax=docker/dockerfile:1

# LDES-admin: the backend, serving the frontend on / and the API on /api/v1. It needs Postgres
# (spring.datasource.*), and the Docker socket of the host to start local LDES servers and LDIOs.
FROM eclipse-temurin:25-jre-alpine

COPY ./ldes-admin-bootstrap-*.jar ./ldes-admin.jar

RUN adduser -D -u 2000 ldesadmin
USER ldesadmin

EXPOSE 8080
CMD ["java", "-jar", "ldes-admin.jar"]
