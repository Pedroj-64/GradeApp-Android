FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/gradify-backend-1.0.0.jar app.jar
ENV GEMINI_API_KEY=""
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
