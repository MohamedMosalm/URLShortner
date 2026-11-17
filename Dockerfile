FROM eclipse-temurin:21-jdk-alpine AS build
RUN wget -qO- https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz | tar -xz -C /opt
ENV PATH="/opt/apache-maven-3.9.9/bin:$PATH"
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/url-shortener.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
