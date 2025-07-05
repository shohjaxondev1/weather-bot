# 1. Build qismi: Maven + JDK 21
FROM maven:3.9.6-eclipse-temurin-21 AS build
LABEL authors="shohjaxon"

# 2. Loyiha fayllarini konteynerga nusxalaymiz
COPY . /app
WORKDIR /app

# 3. Jar faylni yaratamiz
RUN mvn clean package

# 4. Runtime uchun faqat JDK 21 kerak
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/Bot12-1.0-SNAPSHOT-jar-with-dependencies.jar /app/bot.jar

# 5. Botni ishga tushirish komandasi
CMD ["java", "-jar", "bot.jar"]
