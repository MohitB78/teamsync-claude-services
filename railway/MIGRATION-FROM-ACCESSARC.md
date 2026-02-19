# Migrating AccessArc Services to TeamSync Railway Project

This guide walks through moving all AccessArc services from a separate Railway project into the unified TeamSync project.

## Why Combine Projects?

1. **Shared infrastructure** - MongoDB, Redis, PostgreSQL can be shared easily
2. **Private networking** - Services can communicate via Railway private domains
3. **Cost savings** - Single project, single billing, no cross-project networking fees
4. **Simpler management** - One dashboard for all services

---

## Prerequisites

Before starting migration:

1. **Backup data** from AccessArc MongoDB
2. **Document current environment variables** for all services
3. **Note current Railway URLs** for DNS updates
4. **Ensure TeamSync project exists** on Railway

---

## Step-by-Step Migration

### Step 1: Export AccessArc Configuration

In the **AccessArc Railway project**, note down for each service:
- Environment variables
- Custom domains
- Memory/CPU settings

```bash
# For each service, export variables
railway variables --service user-service
railway variables --service department-service
# ... etc
```

### Step 2: Delete Old Services (AccessArc Project)

**CAUTION**: Only do this AFTER setting up services in TeamSync project.

In Railway Dashboard (AccessArc project):
1. Go to each service
2. Click Settings → Danger Zone → Delete Service

**Keep the databases** (MongoDB, PostgreSQL, Redis) until migration is complete.

### Step 3: Create AccessArc Services in TeamSync Project

In Railway Dashboard (TeamSync project), create these services:

#### Infrastructure (if not already present)

| Service | Type | Notes |
|---------|------|-------|
| MongoDB | Database Plugin | Use existing or create new |
| PostgreSQL | Database Plugin | For Zitadel |
| Redis | Database Plugin | Shared cache |
| Zitadel | Docker Image | `ghcr.io/zitadel/zitadel:v4.10.0` |

#### AccessArc Backend Services

Create these services via **+ New → GitHub Repo**:

| Service Name | Root Directory | Port |
|--------------|----------------|------|
| `accessarc-api-gateway` | `access-arc-backend/api-gateway` | 8080 |
| `accessarc-user-service` | `access-arc-backend/user-service` | 8081 |
| `accessarc-department-service` | `access-arc-backend/department-service` | 8082 |
| `accessarc-tenant-service` | `access-arc-backend/tenant-service` | 8083 |
| `accessarc-license-service` | `access-arc-backend/license-service` | 8084 |
| `accessarc-integration-service` | `access-arc-backend/integration-service` | 8085 |
| `accessarc-ldap-service` | `access-arc-backend/ldap-service` | 8086 |
| `accessarc-workflow-service` | `access-arc-backend/workflow-service` | 8087 |
| `accessarc-workflow-executor` | `access-arc-backend/workflow-executor-service` | 8001 |
| `accessarc-business-rules` | `access-arc-backend/business-rules-service` | 8088 |

#### AccessArc Frontend

| Service Name | Root Directory | Notes |
|--------------|----------------|-------|
| `accessarc-frontend` | `access-arc-frontend` | Next.js 16 |

### Step 4: Configure Shared Variables

In Railway Dashboard → TeamSync Project → Settings → Shared Variables:

```env
# Security
JWT_SECRET=<your-384-bit-secret>

# Database References
MONGODB_URL=${{MongoDB.MONGO_URL}}
REDIS_URL=${{Redis.REDIS_URL}}

# Zitadel
ZITADEL_URL=https://<zitadel-domain>.railway.app
ZITADEL_PROJECT_ID=<your-zitadel-project-id>
ZITADEL_CLIENT_ID=teamsync-bff
ZITADEL_ISSUER=${ZITADEL_URL}
ZITADEL_JWK_URI=${ZITADEL_ISSUER}/oauth/v2/keys

# JVM Settings
JAVA_OPTS=-Xmx512m -Xms256m
```

### Step 5: Configure Each AccessArc Service

#### accessarc-api-gateway

```env
PORT=8080
SPRING_PROFILES_ACTIVE=railway
JWT_SECRET=${{shared.JWT_SECRET}}
REDIS_URL=${{shared.REDIS_URL}}
ZITADEL_URL=${{shared.ZITADEL_URL}}
ZITADEL_ISSUER=${{shared.ZITADEL_ISSUER}}
ZITADEL_CLIENT_ID=teamsync-bff

# Service URLs (private networking within same project)
USER_SERVICE_URL=http://${{accessarc-user-service.RAILWAY_PRIVATE_DOMAIN}}:8081
DEPARTMENT_SERVICE_URL=http://${{accessarc-department-service.RAILWAY_PRIVATE_DOMAIN}}:8082
TENANT_SERVICE_URL=http://${{accessarc-tenant-service.RAILWAY_PRIVATE_DOMAIN}}:8083
LICENSE_SERVICE_URL=http://${{accessarc-license-service.RAILWAY_PRIVATE_DOMAIN}}:8084
INTEGRATION_SERVICE_URL=http://${{accessarc-integration-service.RAILWAY_PRIVATE_DOMAIN}}:8085
LDAP_SERVICE_URL=http://${{accessarc-ldap-service.RAILWAY_PRIVATE_DOMAIN}}:8086
WORKFLOW_SERVICE_URL=http://${{accessarc-workflow-service.RAILWAY_PRIVATE_DOMAIN}}:8087
WORKFLOW_EXECUTOR_URL=http://${{accessarc-workflow-executor.RAILWAY_PRIVATE_DOMAIN}}:8001

CORS_ALLOWED_ORIGINS=https://<frontend-domain>.railway.app
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
ZITADEL_CLIENT_ID=teamsync-bff
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
PORT=8080
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

### Step 6: Update TeamSync Services to Reference AccessArc

Update these TeamSync services to use the new AccessArc service URLs:

#### teamsync-workflow-execution-service

```env
# Add this variable
ACCESSARC_WORKFLOW_SERVICE_URL=http://${{accessarc-workflow-service.RAILWAY_PRIVATE_DOMAIN}}:8087
```

#### teamsync-permission-manager-service

```env
# Add this variable
ACCESSARC_DEPARTMENT_SERVICE_URL=http://${{accessarc-department-service.RAILWAY_PRIVATE_DOMAIN}}:8082
```

### Step 7: DNS Migration

1. **Custom Domains**: Move custom domains from old AccessArc services to new ones
2. **Update DNS records** if using external DNS
3. **Update frontend environment** with new API URLs

### Step 8: Verify Migration

1. Check all services are healthy:
   ```bash
   railway status
   ```

2. Test health endpoints:
   ```bash
   curl https://<accessarc-api-gateway>/actuator/health
   curl https://<teamsync-api-gateway>/actuator/health
   ```

3. Test cross-service communication:
   - Login via AccessArc → Verify Zitadel works
   - Create document in TeamSync → Verify department permissions

### Step 9: Delete Old AccessArc Project

Once everything is verified:
1. Go to old AccessArc Railway project
2. Settings → Danger Zone → Delete Project

---

## Rollback Plan

If migration fails:

1. Keep old AccessArc project running until verified
2. Point DNS back to old services
3. Debug issues in new project without affecting production

---

## Architecture After Migration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       TEAMSYNC RAILWAY PROJECT                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    SHARED INFRASTRUCTURE                             │    │
│  │  MongoDB    PostgreSQL    Redis    Zitadel    Kafka    ES    MinIO  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ACCESSARC SERVICES                                │    │
│  │  API Gateway (8080)         License Service (8084)                  │    │
│  │  User Service (8081)        Integration Service (8085)              │    │
│  │  Department Service (8082)  LDAP Service (8086)                     │    │
│  │  Tenant Service (8083)      Workflow Service (8087)                 │    │
│  │  Workflow Executor (8001)   Business Rules (8088)                   │    │
│  │  Frontend (Next.js)                                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    TEAMSYNC SERVICES                                 │    │
│  │  API Gateway (9080)         Trash Service (9088)                    │    │
│  │  Content Service (9081)     Search Service (9089)                   │    │
│  │  Folder Service (9082)      Chat/AI Service (9090)                  │    │
│  │  Storage Service (9083)     Notification Service (9091)             │    │
│  │  Sharing Service (9084)     Activity Service (9092)                 │    │
│  │  Team Service (9085)        WOPI Service (9093)                     │    │
│  │  Project Service (9086)     Settings Service (9094)                 │    │
│  │  Workflow Execution (9087)  Presence Service (9095)                 │    │
│  │  Permission Manager (9096)  Frontend (Next.js)                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Estimated Combined Costs

| Component | Est. Cost |
|-----------|-----------|
| **Infrastructure** | |
| MongoDB | ~$15/month |
| PostgreSQL | ~$8/month |
| Redis | ~$5/month |
| Zitadel | ~$15/month |
| Kafka | ~$15/month |
| Elasticsearch | ~$15/month |
| MinIO | ~$8/month |
| **AccessArc Services (10)** | ~$85/month |
| **TeamSync Services (17)** | ~$145/month |
| **Frontends (2)** | ~$20/month |
| **Total** | **~$330/month** |

*Savings vs. separate projects: ~$40-50/month (no base plan duplication, shared infra)*
