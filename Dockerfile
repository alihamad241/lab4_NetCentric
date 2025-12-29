# Use a maintained Eclipse Temurin image
FROM eclipse-temurin:17-jdk-focal

WORKDIR /app

# Copy the Fat JAR we just built
COPY target/weather-station-project-1.0-SNAPSHOT.jar app.jar

# This allows us to choose which class to run via Kubernetes args
ENTRYPOINT ["java", "-cp", "app.jar"]