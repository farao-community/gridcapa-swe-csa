FROM farao/farao-computation-base:1.9.0

ARG JAR_FILE=csa-runner-app/target/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]