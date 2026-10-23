# ==============================
# Stage 1: Build frontend (Node/Vite)
# ==============================
FROM node:20-alpine AS frontend-build
WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --silent

COPY frontend/ ./
RUN npm run build

# ==============================
# Stage 2: Build backend (Maven/Java)
# ==============================
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app

# Download dependencies first (cached layer when pom.xml unchanged)
COPY pom.xml .
RUN mvn -B dependency:go-offline -q

COPY src ./src

# Copy frontend build artifacts into Spring Boot static resources
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

RUN mvn -B package -DskipTests -q

# ==============================
# Stage 3: Runtime image
# ==============================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -S app && adduser -S -G app app

COPY --from=backend-build /app/target/url-shortener-0.0.1-SNAPSHOT.jar ./app.jar

USER app

# Port is determined by PORT env var (Railway) or defaults to 8080.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
