FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN ./mvnw clean package spring-boot:repackage -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/googlesheets-reader-0.0.1-SNAPSHOT.jar"]