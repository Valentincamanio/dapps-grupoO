# Valoración de mercado de jugadores de fútbol

TP de Desarrollo de Aplicaciones — 2do Semestre 2026

## Cómo levantarlo

Requisitos: Java 21. Nada más — la base es embebida.

    cd backend
    ./gradlew bootRun

- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Consola H2: http://localhost:8080/h2-console

## Tests

    cd backend
    ./gradlew test

## Estructura

    backend/   Spring Boot 4.1.1 + Java 21 + Gradle
    frontend/  React + Vite (entrega 2)