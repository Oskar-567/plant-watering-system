FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-Xmx180m", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]
