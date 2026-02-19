# OnlyOffice Document Server
# Latest stable version for Office document editing
# https://helpcenter.onlyoffice.com/installation/docs-community-install-docker.aspx
FROM onlyoffice/documentserver:8.2

# OnlyOffice runs on port 80
EXPOSE 80

# Environment variables for Railway deployment
# JWT for secure document editing sessions
ENV JWT_ENABLED=true
ENV JWT_SECRET=${JWT_SECRET:-default_jwt_secret_change_me}

# WOPI support for integration
ENV WOPI_ENABLED=true

# Use external PostgreSQL (Railway-provided)
ENV DB_TYPE=postgres
ENV DB_HOST=${DB_HOST:-postgres.railway.internal}
ENV DB_PORT=${DB_PORT:-5432}
ENV DB_NAME=${DB_NAME:-onlyoffice}
ENV DB_USER=${DB_USER:-postgres}
ENV DB_PWD=${DB_PWD}

# Use external RabbitMQ if available, otherwise use built-in
ENV AMQP_TYPE=rabbitmq

# Logging
ENV LOG_LEVEL=WARN

# The image uses its own entrypoint
