FROM maven:3.9.9-eclipse-temurin-21-alpine

# Set up non-root user matching default host UID 1000
RUN addgroup -g 1000 cka && adduser -u 1000 -G cka -D cka

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN chown -R cka:cka /app
USER cka

RUN mvn dependency:go-offline -B || true

# Copy source code
COPY --chown=cka:cka src ./src

EXPOSE 8080 5005

ENV SERVER_PORT=8080

CMD ["mvn", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
