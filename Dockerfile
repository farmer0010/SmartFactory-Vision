FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Install Python and YOLO dependencies for JPyRust
RUN apt-get update && apt-get install -y python3 python3-pip libgl1-mesa-glx
RUN pip3 install ultralytics

# Copy JPyRust requirements
ENV APP_AI_WORK_DIR=/app/jpyrust
COPY yolov8n.pt /app/yolov8n.pt

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
