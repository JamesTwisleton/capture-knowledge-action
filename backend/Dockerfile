# Maven 3.9.16 on Ubuntu noble. Scanned clean: 0 critical, 0 high.
# For comparison when this was chosen, 3.9.9 on the same base carried 14 highs, and
# the -alpine variant carried 5 criticals and 59 highs. Pinned to a concrete version
# rather than the floating `3` tag so builds stay reproducible — re-scan before bumping.
FROM maven:3.9.16-eclipse-temurin-21-noble

# Run as a non-root user whose UID matches the host's first user, so files written to
# bind mounts belong to you rather than to root.
#
# Two things are noble-specific. This is Ubuntu, not Alpine, so it is useradd/groupadd
# rather than BusyBox adduser/addgroup. And Ubuntu 24.04 ships a default `ubuntu` user
# already holding UID 1000, which has to go before cka can take it.
RUN userdel -r ubuntu \
 && groupadd -g 1000 cka \
 && useradd -u 1000 -g cka -m -s /bin/bash cka \
 && mkdir -p /app /data/vault /data/db \
 && chown -R cka:cka /app /data

USER cka
WORKDIR /app

# The base image points MAVEN_CONFIG at /root/.m2, which a non-root user cannot write;
# left alone it warns on every container start.
ENV MAVEN_CONFIG=/home/cka/.m2

# pom.xml first: dependency resolution is the expensive layer, and it should only be
# invalidated by a dependency change, not by every edit to a source file.
COPY --chown=cka:cka pom.xml .
RUN mvn -B dependency:go-offline

COPY --chown=cka:cka src ./src

# 8080 application, 5005 JVM debugger. EXPOSE documents; compose publishes.
EXPOSE 8080 5005

ENV SERVER_PORT=8080

# suspend=n so the app boots without waiting for a debugger; address=*:5005 because
# the JDK binds the agent to localhost otherwise, unreachable from outside.
CMD ["mvn", "spring-boot:run", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
