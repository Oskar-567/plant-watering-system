FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY target/*.jar app.jar
# Tuned for the 2 GB Raspberry Pi — see docs/superpowers/specs/2026-09-13-server-memory-tuning-design.md
ENTRYPOINT ["java", \
  "-XX:+UseSerialGC", \
  "-XX:ActiveProcessorCount=2", \
  "-XX:TieredStopAtLevel=1", \
  "-XX:ReservedCodeCacheSize=32m", \
  "-Xss512k", \
  "-Xmx180m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
