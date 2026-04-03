FROM eclipse-temurin:17-jre-alpine

# Install timezone data and curl for health checks
RUN apk add --no-cache tzdata curl
ENV TZ=Asia/Damascus

WORKDIR /app

# Copy application
COPY target/RestaurantQrApplication-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# JVM settings for production:
# - -Xms512m / -Xmx1024m: Initial/max heap (adjust based on available RAM)
# - -XX:+UseG1GC: G1 garbage collector for low latency
# - -XX:MaxGCPauseMillis=200: Target GC pause time
# - -XX:+UseStringDeduplication: Save memory on duplicate strings
# - -XX:+HeapDumpOnOutOfMemoryError: Dump heap on OOM for debugging
# - -Djava.security.egd: Faster random number generation
ENV JAVA_OPTS="-Xms512m -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=Asia/Damascus"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
