# BUILD STAGE

FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew

RUN ./gradlew :server:shadowJar --no-daemon


# RUNTIME STAGE

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/server/build/libs/server-all.jar app.jar

ENV PORT=8080

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]