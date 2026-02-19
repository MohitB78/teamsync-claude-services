# Collabora Online Development Edition (CODE)
# Latest version with full Office suite support
# https://www.collaboraoffice.com/code/
FROM collabora/code:24.04.9.2.1

# Collabora runs on port 9980
EXPOSE 9980

# Environment variables for Railway deployment
# SSL is handled by Railway's edge proxy
ENV DONT_GEN_SSL_CERT=1
ENV extra_params=--o:ssl.enable=false --o:ssl.termination=true --o:net.proto=IPv4
ENV dictionaries=en_US

# Allow connections from any origin (Railway handles CORS at edge)
ENV aliasgroup1=https://.*\\.railway\\.app:443,https://.*\\.teamsync\\.link:443

# Server name for logging
ENV server_name=teamsync-collabora

# Username/password for admin console (optional)
ENV username=admin
ENV password=collabora_admin_2024

# The image uses its own entrypoint - no CMD needed
