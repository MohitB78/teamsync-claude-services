# Unified Platform Railway Deployment - Complete Guide

This guide covers deploying **both AccessArc and TeamSync** in a single Railway project.

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Infrastructure Setup](#infrastructure-setup)
4. [AccessArc Services](#accessarc-services)
5. [TeamSync Services](#teamsync-services)
6. [Environment Variables](#environment-variables)
7. [Post-Deployment Configuration](#post-deployment-configuration)
8. [Monitoring & Troubleshooting](#monitoring--troubleshooting)
9. [Estimated Costs](#estimated-costs)

---

## Overview

This Railway project contains **two integrated platforms**:

### AccessArc (Admin Platform) - 10 Services

| Service | Port | Purpose |
|---------|------|---------|
| accessarc-api-gateway | 8080 | Entry point, routing |
| accessarc-user-service | 8081 | User management |
| accessarc-department-service | 8082 | Department management |
| accessarc-tenant-service | 8083 | Multi-tenancy |
| accessarc-license-service | 8084 | License management |
| accessarc-integration-service | 8085 | Third-party integrations |
| accessarc-ldap-service | 8086 | Directory sync |
| accessarc-workflow-service | 8087 | Workflow builder |
| accessarc-workflow-executor | 8001 | AI workflow execution (Python) |
| accessarc-business-rules | 8088 | Business rules engine |

### TeamSync (Document Platform) - 17 Services

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 9080 | Entry point, routing, JWT validation |
| Content Service | 9081 | Document metadata, versions, tags |
| Folder Service | 9082 | Folder hierarchy, breadcrumbs |
| Storage Service | 9083 | File storage (S3/MinIO) |
| Sharing Service | 9084 | Permissions, public links |
| Team Service | 9085 | Team management |
| Project Service | 9086 | Project tracking |
| Workflow Execution | 9087 | Execute AccessArc workflows |
| Trash Service | 9088 | Soft delete, 30-day retention |
| Search Service | 9089 | NLP search, RAG |
| Chat/AI Service | 9090 | DocuTalk AI chatbot |
| Notification Service | 9091 | Push, email, in-app |
| Activity Service | 9092 | Audit trail, activity feed |
| WOPI Service | 9093 | Office editing (Collabora) |
| Settings Service | 9094 | User preferences |
| Presence Service | 9095 | Online status, editing presence |
| Permission Manager | 9096 | Drive-level RBAC |

### Shared Infrastructure

Both platforms share:
- **MongoDB** - Primary database (separate databases per service)
- **PostgreSQL** - Zitadel database
- **Redis** - Caching and sessions
- **Zitadel** - OAuth2/OIDC identity provider

TeamSync-specific infrastructure:
- **Kafka** - Event streaming (KRaft mode)
- **Elasticsearch** - Full-text search
- **MinIO/S3** - Object storage

---

## Prerequisites

### 1. Railway Account
- Sign up at https://railway.app
- Railway Pro plan recommended ($20/month + usage)

### 2. Railway CLI
```bash
npm install -g @railway/cli
railway login
```

### 4. Git Repository
- Push your code to GitHub (Railway supports GitHub integration)

---

## Infrastructure Setup

### Step 1: Create TeamSync Project

```bash
cd teamsync-backend
railway init --name teamsync
```

Or via Railway Dashboard: https://railway.app/dashboard → New Project

### Step 2: Infrastructure Services

TeamSync requires these additional services beyond AccessArc:

#### Kafka (via Docker Image)
1. Click **+ New** → **Docker Image**
2. Image: `apache/kafka:3.8.0` (Official Apache Kafka image)
3. Set environment variables:
```env
KAFKA_NODE_ID=1
KAFKA_PROCESS_ROLES=broker,controller
KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093
KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://${{RAILWAY_PRIVATE_DOMAIN}}:9092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
KAFKA_AUTO_CREATE_TOPICS_ENABLE=true
CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk
```

**Alternative images** (if apache/kafka doesn't work):
- `apache/kafka:3.7.0`
- `confluentinc/cp-kafka:7.6.0`

#### Elasticsearch (via Docker Image)
1. Click **+ New** → **Docker Image**
2. Image: `docker.elastic.co/elasticsearch/elasticsearch:8.11.0`
3. Set environment variables:
```env
discovery.type=single-node
xpack.security.enabled=false
ES_JAVA_OPTS=-Xms512m -Xmx512m
```

#### MinIO (via Docker Image) - For S3-compatible storage
1. Click **+ New** → **Docker Image**
2. Image: `minio/minio:latest`
3. Start command: `server /data --console-address :9001`
4. Set environment variables:
```env
MINIO_ROOT_USER=teamsync-admin
MINIO_ROOT_PASSWORD=<your-secure-password>
```

**Alternative**: Use AWS S3 or GCS directly with appropriate IAM credentials.

### Step 3: Deploy Zitadel

Create Zitadel service:
1. Click **+ New** → **Docker Image**
2. Image: `ghcr.io/zitadel/zitadel:v4.10.0`
3. Start command: `start-from-init --masterkeyFromEnv`
4. Set environment variables:
```env
ZITADEL_MASTERKEY=<generate-32-byte-random-key>
ZITADEL_DATABASE_POSTGRES_HOST=${{Postgres.PGHOST}}
ZITADEL_DATABASE_POSTGRES_PORT=${{Postgres.PGPORT}}
ZITADEL_DATABASE_POSTGRES_DATABASE=zitadel
ZITADEL_DATABASE_POSTGRES_USER_USERNAME=${{Postgres.PGUSER}}
ZITADEL_DATABASE_POSTGRES_USER_PASSWORD=${{Postgres.PGPASSWORD}}
ZITADEL_DATABASE_POSTGRES_USER_SSL_MODE=disable
ZITADEL_EXTERNALDOMAIN=<your-zitadel-domain>.railway.app
ZITADEL_EXTERNALPORT=443
ZITADEL_EXTERNALSECURE=true
```

---

## AccessArc Services

Deploy AccessArc services via **+ New → GitHub Repo**, setting root directory for each:

| Service Name | Root Directory | Port |
|--------------|----------------|------|
| accessarc-api-gateway | `access-arc-backend/api-gateway` | 8080 |
| accessarc-user-service | `access-arc-backend/user-service` | 8081 |
| accessarc-department-service | `access-arc-backend/department-service` | 8082 |
| accessarc-tenant-service | `access-arc-backend/tenant-service` | 8083 |
| accessarc-license-service | `access-arc-backend/license-service` | 8084 |
| accessarc-integration-service | `access-arc-backend/integration-service` | 8085 |
| accessarc-ldap-service | `access-arc-backend/ldap-service` | 8086 |
| accessarc-workflow-service | `access-arc-backend/workflow-service` | 8087 |
| accessarc-workflow-executor | `access-arc-backend/workflow-executor-service` | 8001 |
| accessarc-business-rules | `access-arc-backend/business-rules-service` | 8088 |
| accessarc-frontend | `access-arc-frontend` | 3000 |

---

## TeamSync Services

### Option A: GitHub Integration (Recommended)

1. Push code to GitHub
2. In Railway Dashboard, click **+ New** → **GitHub Repo**
3. Select your repository
4. Configure root directory for each service (e.g., `teamsync-backend/content-service`)

### Option B: CLI Deployment

```bash
# From teamsync-backend directory
./railway/deploy.sh
```

### Option C: Manual Deployment

Deploy services in this order:

```bash
# Phase 1: Core Services
cd content-service && railway up
cd ../folder-service && railway up
cd ../storage-service && railway up
cd ../sharing-service && railway up

# Phase 2: Collaboration Services
cd ../team-service && railway up
cd ../project-service && railway up
cd ../workflow-execution-service && railway up
cd ../trash-service && railway up

# Phase 3: Search & AI Services
cd ../search-service && railway up
cd ../chat-service && railway up

# Phase 4: Supporting Services
cd ../notification-service && railway up
cd ../activity-service && railway up
cd ../wopi-service && railway up
cd ../settings-service && railway up
cd ../presence-service && railway up

# Phase 5: Permission Manager (depends on all services)
cd ../permission-manager-service && railway up

# Phase 6: API Gateway (last)
cd ../api-gateway && railway up
```

---

## Environment Variables

### Shared Variables (All Services)

Set these as **Shared Variables** in Railway:

```env
# Security
JWT_SECRET=<generate-384-bit-secret-with-openssl-rand-base64-48>

# Spring Profile
SPRING_PROFILES_ACTIVE=railway

# Database References
MONGODB_URL=${{MongoDB.MONGO_URL}}
REDIS_URL=${{Redis.REDIS_URL}}

# Zitadel
ZITADEL_URL=https://<zitadel-domain>.railway.app
ZITADEL_PROJECT_ID=<your-zitadel-project-id>
ZITADEL_CLIENT_ID=teamsync-bff
ZITADEL_ISSUER=${ZITADEL_URL}
ZITADEL_JWK_URI=${ZITADEL_ISSUER}/oauth/v2/keys

# Kafka (TeamSync)
KAFKA_BOOTSTRAP_SERVERS=${{kafka.RAILWAY_PRIVATE_DOMAIN}}:9092

# Elasticsearch (TeamSync)
ELASTICSEARCH_URI=http://${{elasticsearch.RAILWAY_PRIVATE_DOMAIN}}:9200

# MinIO/S3 Storage (TeamSync)
STORAGE_PROVIDER=minio
MINIO_ENDPOINT=http://${{minio.RAILWAY_PRIVATE_DOMAIN}}:9000
MINIO_ACCESS_KEY=teamsync-admin
MINIO_SECRET_KEY=<your-minio-password>

# JVM Settings
JAVA_OPTS=-Xmx512m -Xms256m
```

---

### AccessArc Environment Variables

#### accessarc-api-gateway

```env
PORT=8080
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_CLIENT_ID=teamsync-bff

# Service URLs (private networking)
USER_SERVICE_URL=http://${{accessarc-user-service.RAILWAY_PRIVATE_DOMAIN}}:8081
DEPARTMENT_SERVICE_URL=http://${{accessarc-department-service.RAILWAY_PRIVATE_DOMAIN}}:8082
TENANT_SERVICE_URL=http://${{accessarc-tenant-service.RAILWAY_PRIVATE_DOMAIN}}:8083
LICENSE_SERVICE_URL=http://${{accessarc-license-service.RAILWAY_PRIVATE_DOMAIN}}:8084
INTEGRATION_SERVICE_URL=http://${{accessarc-integration-service.RAILWAY_PRIVATE_DOMAIN}}:8085
LDAP_SERVICE_URL=http://${{accessarc-ldap-service.RAILWAY_PRIVATE_DOMAIN}}:8086
WORKFLOW_SERVICE_URL=http://${{accessarc-workflow-service.RAILWAY_PRIVATE_DOMAIN}}:8087
WORKFLOW_EXECUTOR_URL=http://${{accessarc-workflow-executor.RAILWAY_PRIVATE_DOMAIN}}:8001

CORS_ALLOWED_ORIGINS=https://<accessarc-frontend-domain>.railway.app
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-user-service

```env
PORT=8081
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/users?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
TENANT_SERVICE_URL=http://${{accessarc-tenant-service.RAILWAY_PRIVATE_DOMAIN}}:8083
LICENSE_SERVICE_URL=http://${{accessarc-license-service.RAILWAY_PRIVATE_DOMAIN}}:8084
INTEGRATION_SERVICE_URL=http://${{accessarc-integration-service.RAILWAY_PRIVATE_DOMAIN}}:8085
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-department-service

```env
PORT=8082
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/departments?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
USER_SERVICE_URL=http://${{accessarc-user-service.RAILWAY_PRIVATE_DOMAIN}}:8081
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-tenant-service

```env
PORT=8083
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/tenants?authSource=admin
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
KEYCLOAK_CLIENT_ID=accessarc-frontend
KEYCLOAK_ADMIN_USERNAME=${{shared.KEYCLOAK_ADMIN_USERNAME}}
KEYCLOAK_ADMIN_PASSWORD=${{shared.KEYCLOAK_ADMIN_PASSWORD}}
USER_SERVICE_URL=http://${{accessarc-user-service.RAILWAY_PRIVATE_DOMAIN}}:8081
DEPARTMENT_SERVICE_URL=http://${{accessarc-department-service.RAILWAY_PRIVATE_DOMAIN}}:8082
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-license-service

```env
PORT=8084
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/licenses?authSource=admin
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-integration-service

```env
PORT=8085
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/integrations?authSource=admin
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-ldap-service

```env
PORT=8086
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/ldap?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-workflow-service

```env
PORT=8087
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/accessarc?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
WORKFLOW_EXECUTOR_URL=http://${{accessarc-workflow-executor.RAILWAY_PRIVATE_DOMAIN}}:8001
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-workflow-executor (Python)

```env
PORT=8001
MONGODB_URI=${{shared.MONGODB_URL}}/accessarc?authSource=admin
MONGODB_DATABASE=accessarc
REDIS_URL=${{shared.REDIS_URL}}
WORKFLOW_SERVICE_URL=http://${{accessarc-workflow-service.RAILWAY_PRIVATE_DOMAIN}}:8087
USER_SERVICE_URL=http://${{accessarc-user-service.RAILWAY_PRIVATE_DOMAIN}}:8081
EMAIL_SERVICE_URL=http://${{accessarc-integration-service.RAILWAY_PRIVATE_DOMAIN}}:8085
MAX_EXECUTION_TIME=300
MAX_RETRIES=3
```

#### accessarc-business-rules

```env
PORT=8088
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
MONGODB_URL=${{shared.MONGODB_URL}}/accessarc?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
JAVA_OPTS=-Xmx512m -Xms256m
```

#### accessarc-frontend

```env
NEXT_PUBLIC_API_URL=https://<accessarc-api-gateway-domain>.railway.app
NEXT_PUBLIC_ZITADEL_URL=${{shared.ZITADEL_URL}}
NEXT_PUBLIC_ZITADEL_PROJECT_ID=${{shared.ZITADEL_PROJECT_ID}}
NEXT_PUBLIC_ZITADEL_CLIENT_ID=teamsync-bff
```

---

### TeamSync Environment Variables

#### teamsync-api-gateway (9080)

```env
PORT=9080
SPRING_PROFILES_ACTIVE=railway

# Service URLs (Railway private networking)
CONTENT_SERVICE_URL=http://${{teamsync-content-service.RAILWAY_PRIVATE_DOMAIN}}:9081
FOLDER_SERVICE_URL=http://${{teamsync-folder-service.RAILWAY_PRIVATE_DOMAIN}}:9082
STORAGE_SERVICE_URL=http://${{teamsync-storage-service.RAILWAY_PRIVATE_DOMAIN}}:9083
SHARING_SERVICE_URL=http://${{teamsync-sharing-service.RAILWAY_PRIVATE_DOMAIN}}:9084
TEAM_SERVICE_URL=http://${{teamsync-team-service.RAILWAY_PRIVATE_DOMAIN}}:9085
PROJECT_SERVICE_URL=http://${{teamsync-project-service.RAILWAY_PRIVATE_DOMAIN}}:9086
WORKFLOW_EXECUTION_SERVICE_URL=http://${{teamsync-workflow-execution-service.RAILWAY_PRIVATE_DOMAIN}}:9087
TRASH_SERVICE_URL=http://${{teamsync-trash-service.RAILWAY_PRIVATE_DOMAIN}}:9088
SEARCH_SERVICE_URL=http://${{teamsync-search-service.RAILWAY_PRIVATE_DOMAIN}}:9089
CHAT_SERVICE_URL=http://${{teamsync-chat-service.RAILWAY_PRIVATE_DOMAIN}}:9090
NOTIFICATION_SERVICE_URL=http://${{teamsync-notification-service.RAILWAY_PRIVATE_DOMAIN}}:9091
ACTIVITY_SERVICE_URL=http://${{teamsync-activity-service.RAILWAY_PRIVATE_DOMAIN}}:9092
WOPI_SERVICE_URL=http://${{teamsync-wopi-service.RAILWAY_PRIVATE_DOMAIN}}:9093
SETTINGS_SERVICE_URL=http://${{teamsync-settings-service.RAILWAY_PRIVATE_DOMAIN}}:9094
PRESENCE_SERVICE_URL=http://${{teamsync-presence-service.RAILWAY_PRIVATE_DOMAIN}}:9095
PERMISSION_MANAGER_SERVICE_URL=http://${{teamsync-permission-manager-service.RAILWAY_PRIVATE_DOMAIN}}:9096

# CORS
CORS_ALLOWED_ORIGINS=https://<frontend-domain>.railway.app,http://localhost:3000

JAVA_OPTS=-Xmx512m -Xms256m
```

### Content Service (9081)

```env
PORT=9081
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_content?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
STORAGE_PROVIDER=${{shared.STORAGE_PROVIDER}}
MINIO_ENDPOINT=${{shared.MINIO_ENDPOINT}}
MINIO_ACCESS_KEY=${{shared.MINIO_ACCESS_KEY}}
MINIO_SECRET_KEY=${{shared.MINIO_SECRET_KEY}}
JAVA_OPTS=-Xmx512m -Xms256m
```

### Folder Service (9082)

```env
PORT=9082
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_folders?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx512m -Xms256m
```

### Storage Service (9083)

```env
PORT=9083
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_storage?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
STORAGE_PROVIDER=${{shared.STORAGE_PROVIDER}}
MINIO_ENDPOINT=${{shared.MINIO_ENDPOINT}}
MINIO_ACCESS_KEY=${{shared.MINIO_ACCESS_KEY}}
MINIO_SECRET_KEY=${{shared.MINIO_SECRET_KEY}}
JAVA_OPTS=-Xmx768m -Xms256m
```

### Sharing Service (9084)

```env
PORT=9084
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_sharing?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
PERMISSION_MANAGER_SERVICE_URL=http://${{teamsync-permission-manager-service.RAILWAY_PRIVATE_DOMAIN}}:9096
JAVA_OPTS=-Xmx512m -Xms256m
```

### Team Service (9085)

```env
PORT=9085
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_teams?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx512m -Xms256m
```

### Project Service (9086)

```env
PORT=9086
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_projects?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx512m -Xms256m
```

### Workflow Execution Service (9087)

```env
PORT=9087
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_workflows?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
# AccessArc Workflow Service
ACCESSARC_WORKFLOW_SERVICE_URL=http://${{accessarc-workflow-service.RAILWAY_PRIVATE_DOMAIN}}:8087
JAVA_OPTS=-Xmx512m -Xms256m
```

### Trash Service (9088)

```env
PORT=9088
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_trash?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
PERMISSION_MANAGER_SERVICE_URL=http://${{teamsync-permission-manager-service.RAILWAY_PRIVATE_DOMAIN}}:9096
JAVA_OPTS=-Xmx512m -Xms256m
```

### Search Service (9089)

```env
PORT=9089
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_search?authSource=admin
ELASTICSEARCH_URI=${{shared.ELASTICSEARCH_URI}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx768m -Xms256m
```

### Chat/AI Service (9090)

```env
PORT=9090
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_chat?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
# AI/LLM Configuration (optional)
OPENAI_API_KEY=<your-openai-key>
JAVA_OPTS=-Xmx512m -Xms256m
```

### Notification Service (9091)

```env
PORT=9091
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_notifications?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
# SendGrid Email
SENDGRID_API_KEY=<your-sendgrid-key>
JAVA_OPTS=-Xmx512m -Xms256m
```

### Activity Service (9092)

```env
PORT=9092
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_activity?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx512m -Xms256m
```

### WOPI Service (9093)

```env
PORT=9093
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_wopi?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
MINIO_ENDPOINT=${{shared.MINIO_ENDPOINT}}
MINIO_ACCESS_KEY=${{shared.MINIO_ACCESS_KEY}}
MINIO_SECRET_KEY=${{shared.MINIO_SECRET_KEY}}
# Collabora/OnlyOffice URL
WOPI_CLIENT_URL=<your-collabora-url>
JAVA_OPTS=-Xmx512m -Xms256m
```

### Settings Service (9094)

```env
PORT=9094
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_settings?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx256m -Xms128m
```

### Presence Service (9095)

```env
PORT=9095
SPRING_PROFILES_ACTIVE=railway
REDIS_URL=${{shared.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
JAVA_OPTS=-Xmx256m -Xms128m
```

### Permission Manager Service (9096)

```env
PORT=9096
SPRING_PROFILES_ACTIVE=railway
SPRING_MONGODB_URI=${{shared.MONGODB_URL}}/teamsync_permissions?authSource=admin
REDIS_URL=${{shared.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{shared.KAFKA_BOOTSTRAP_SERVERS}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_JWK_URI=${{shared.ZITADEL_JWK_URI}}
# AccessArc Department Service (for drive sync)
ACCESSARC_DEPARTMENT_SERVICE_URL=http://${{accessarc-department-service.RAILWAY_PRIVATE_DOMAIN}}:8082
JAVA_OPTS=-Xmx512m -Xms256m
```

---

## Post-Deployment Configuration

### 1. Create Kafka Topics

Connect to Kafka and create required topics:

```bash
# TeamSync event topics
kafka-topics.sh --create --topic teamsync.content.events --bootstrap-server localhost:9092
kafka-topics.sh --create --topic teamsync.storage.events --bootstrap-server localhost:9092
kafka-topics.sh --create --topic teamsync.sharing.events --bootstrap-server localhost:9092
kafka-topics.sh --create --topic teamsync.activity.events --bootstrap-server localhost:9092
kafka-topics.sh --create --topic teamsync.notifications.send --bootstrap-server localhost:9092
kafka-topics.sh --create --topic teamsync.search.index --bootstrap-server localhost:9092
```

Note: If `KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true`, topics will be auto-created.

### 2. Create MinIO Buckets

```bash
# Connect to MinIO console or use mc CLI
mc alias set teamsync http://<minio-url>:9000 teamsync-admin <password>
mc mb teamsync/teamsync-documents
mc mb teamsync/teamsync-thumbnails
mc mb teamsync/teamsync-temp
```

### 3. Create Elasticsearch Indices

```bash
# Content index
curl -X PUT "http://<elasticsearch-url>:9200/teamsync-content" -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "tenantId": { "type": "keyword" },
      "driveId": { "type": "keyword" },
      "name": { "type": "text", "analyzer": "standard" },
      "content": { "type": "text", "analyzer": "standard" },
      "tags": { "type": "keyword" },
      "createdAt": { "type": "date" }
    }
  }
}'
```

### 4. Add Custom Domains

In Railway Dashboard → Service → Settings → Domains:
- Add custom domain for API Gateway
- Configure SSL (Railway provides free certificates)

### 5. Configure CORS

Update `CORS_ALLOWED_ORIGINS` to include your frontend domain.

---

## Monitoring & Troubleshooting

### View Logs

```bash
# All services
railway logs

# Specific service
railway logs --service teamsync-content-service
```

### Check Status

```bash
railway status
```

### Health Endpoints

All services expose health at `/actuator/health`:

| Service | Health Endpoint |
|---------|-----------------|
| API Gateway | `https://<gateway-domain>/actuator/health` |
| Content Service | `http://internal:9081/actuator/health` |
| Storage Service | `http://internal:9083/actuator/health` |
| Search Service | `http://internal:9089/actuator/health` |

### Common Issues

**Service can't connect to Kafka:**
- Verify Kafka is healthy: `railway logs --service teamsync-kafka`
- Check `KAFKA_BOOTSTRAP_SERVERS` uses Railway private domain

**Elasticsearch connection failed:**
- Check Elasticsearch health: `curl http://<es-url>:9200/_cluster/health`
- Verify `xpack.security.enabled=false` for development

**Storage upload fails:**
- Verify MinIO credentials
- Check bucket exists
- Verify file size limits in Storage Service

**Permission denied:**
- Check Zitadel token is valid
- Verify user has required role
- Check Permission Manager Service logs

---

## Estimated Costs

### Railway Pricing (Pro Plan)

| Resource | Cost |
|----------|------|
| Base | $20/month |
| Compute | ~$0.000463/vCPU/min |
| Memory | ~$0.000231/GB/min |
| Network | $0.10/GB egress |

### Unified Platform Costs (Single Railway Project)

| Component | Memory | vCPU | Est. Cost |
|-----------|--------|------|-----------|
| **Shared Infrastructure** | | | |
| MongoDB | 1GB | 0.5 | ~$15 |
| PostgreSQL | 512MB | 0.25 | ~$8 |
| Redis | 256MB | 0.25 | ~$5 |
| Zitadel | 1GB | 0.5 | ~$15 |
| Kafka | 1GB | 0.5 | ~$15 |
| Elasticsearch | 1GB | 0.5 | ~$15 |
| MinIO | 512MB | 0.25 | ~$8 |
| **AccessArc Services (10)** | | | |
| API Gateway | 512MB | 0.5 | ~$10 |
| User Service | 512MB | 0.5 | ~$10 |
| Department Service | 512MB | 0.5 | ~$10 |
| Tenant Service | 512MB | 0.25 | ~$8 |
| License Service | 512MB | 0.25 | ~$8 |
| Integration Service | 256MB | 0.25 | ~$5 |
| LDAP Service | 512MB | 0.25 | ~$8 |
| Workflow Service | 512MB | 0.5 | ~$10 |
| Workflow Executor | 512MB | 0.5 | ~$10 |
| Business Rules | 512MB | 0.5 | ~$10 |
| **TeamSync Services (17)** | | | |
| API Gateway | 512MB | 0.5 | ~$10 |
| Content Service | 512MB | 0.5 | ~$10 |
| Folder Service | 512MB | 0.25 | ~$8 |
| Storage Service | 768MB | 0.5 | ~$12 |
| Sharing Service | 512MB | 0.5 | ~$10 |
| Team Service | 512MB | 0.25 | ~$8 |
| Project Service | 512MB | 0.25 | ~$8 |
| Workflow Execution | 512MB | 0.25 | ~$8 |
| Trash Service | 512MB | 0.25 | ~$8 |
| Search Service | 768MB | 0.5 | ~$12 |
| Chat/AI Service | 512MB | 0.5 | ~$10 |
| Notification Service | 512MB | 0.25 | ~$8 |
| Activity Service | 512MB | 0.25 | ~$8 |
| WOPI Service | 512MB | 0.25 | ~$8 |
| Settings Service | 256MB | 0.25 | ~$5 |
| Presence Service | 256MB | 0.25 | ~$5 |
| Permission Manager | 512MB | 0.5 | ~$10 |
| **Frontends (2)** | | | |
| AccessArc Frontend | 512MB | 0.25 | ~$8 |
| TeamSync Frontend | 512MB | 0.25 | ~$8 |
| **Total** | | | **~$330/month** |

### Savings vs. Separate Projects

| Configuration | Est. Cost |
|---------------|-----------|
| Two separate projects | ~$370/month |
| Unified project | ~$330/month |
| **Savings** | **~$40/month** |

*Savings from: no duplicate base plan, shared infrastructure, private networking*

*Note: Costs vary based on actual usage. Enable Railway's usage alerts.*

---

## Security Recommendations

1. **Never commit secrets** - Use Railway's environment variables
2. **Use Railway private networking** for service-to-service communication
3. **Enable SSL/TLS** - Railway provides free SSL certificates
4. **Secure MinIO** with strong credentials
5. **Configure CORS** to only allow your frontend domain
6. **Rotate credentials** periodically
7. **Enable Elasticsearch security** for production (xpack.security.enabled=true)

---

## Support

- Railway Docs: https://docs.railway.app
- Railway Discord: https://discord.gg/railway
- TeamSync Issues: Check project repository
