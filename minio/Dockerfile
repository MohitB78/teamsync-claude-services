FROM minio/minio:latest

# Create data directory
RUN mkdir -p /data

# Expose MinIO API port and Console port
EXPOSE 9000 9001

# Health check
HEALTHCHECK --interval=30s --timeout=20s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:9000/minio/health/live || exit 1

# Start MinIO server with console
CMD ["server", "/data", "--console-address", ":9001"]
