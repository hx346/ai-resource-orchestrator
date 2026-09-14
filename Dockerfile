# syntax=docker/dockerfile:1
# ---- Frontend build ----
FROM node:22-alpine AS web
WORKDIR /web
RUN corepack enable && corepack prepare pnpm@11.20.0 --activate
COPY web/package.json web/pnpm-lock.yaml web/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY web/ ./
RUN pnpm run build

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Cache dependencies first
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Build application
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
USER app

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
