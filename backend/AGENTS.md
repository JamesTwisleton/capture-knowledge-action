# Backend (`backend/`)

Spring Boot backend core service for Capture-Knowledge-Action (CKA).

## Overview
- **Framework**: Java 21, Spring Boot 3.x
- **Build Tool**: Apache Maven
- **Port**: `8080` (HTTP API), `5005` (JVM Remote Debug JDWP)

## Key Commands
- Run tests: `mvn clean test`
- Build package: `mvn clean package`
- Local dev run: `mvn spring-boot:run`

## Hot Reload & Debugging
- Hot reload uses **Spring Boot DevTools**.
- Trigger hot reload by running `mvn compile` (or editing/compiling files in IDE).
- Remote JVM debugging is exposed on port `5005` with JDWP options: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`.

## Structure
- `src/main/java/com/cka/`: Application source code, domain models, controllers, and provider implementations.
- `src/test/java/com/cka/`: Unit and integration tests.
