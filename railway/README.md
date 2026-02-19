# TeamSync Railway Deployment

Quick start guide for deploying TeamSync to Railway.

## Prerequisites

1. **AccessArc deployed** - TeamSync requires AccessArc infrastructure (MongoDB, Redis, Keycloak)
2. **Railway CLI installed**: `npm install -g @railway/cli`
3. **Logged into Railway**: `railway login`

## Quick Start

### 1. Deploy via Script

```bash
cd teamsync-backend
./railway/deploy.sh
```

### 2. Deploy via GitHub Integration

1. Push code to GitHub
2. In Railway Dashboard → New → GitHub Repo
3. Select repository
4. Configure root directory per service

## Services Overview

| Service | Port | Database |
|---------|------|----------|
| API Gateway | 9080 | Redis |
| Content Service | 9081 | MongoDB |
| Folder Service | 9082 | MongoDB |
| Storage Service | 9083 | MongoDB + MinIO |
| Sharing Service | 9084 | MongoDB + Redis |
| Team Service | 9085 | MongoDB |
| Project Service | 9086 | MongoDB |
| Workflow Execution | 9087 | MongoDB |
| Trash Service | 9088 | MongoDB |
| Search Service | 9089 | MongoDB + Elasticsearch |
| Chat/AI Service | 9090 | MongoDB |
| Notification Service | 9091 | MongoDB |
| Activity Service | 9092 | MongoDB |
| WOPI Service | 9093 | MongoDB + Redis |
| Settings Service | 9094 | MongoDB |
| Presence Service | 9095 | Redis |
| Permission Manager | 9096 | MongoDB + Redis |

## Required Infrastructure

### From AccessArc (shared)
- MongoDB
- Redis
- Keycloak

### TeamSync-specific (new)
- Kafka (bitnami/kafka:4.1.1)
- Elasticsearch (elasticsearch:8.11.0)
- MinIO (minio/minio:latest)

## Environment Variables

Set `SPRING_PROFILES_ACTIVE=railway` for all services.

See [DEPLOYMENT.md](./DEPLOYMENT.md) for complete configuration.

## Estimated Cost

~$185-220/month (TeamSync only)
~$315-370/month (AccessArc + TeamSync)

## Support

See full documentation in [DEPLOYMENT.md](./DEPLOYMENT.md)
