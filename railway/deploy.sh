#!/bin/bash

# Unified Platform Railway Deployment Script
# Deploys both AccessArc and TeamSync services to a single Railway project

set -e

echo "=========================================="
echo "  Unified Platform Deployment Script"
echo "  (AccessArc + TeamSync)"
echo "=========================================="

# Check if railway CLI is installed
if ! command -v railway &> /dev/null; then
    echo "Error: Railway CLI is not installed."
    echo "Install it with: npm install -g @railway/cli"
    exit 1
fi

# Check if logged in
if ! railway whoami &> /dev/null; then
    echo "Error: Not logged into Railway."
    echo "Run: railway login"
    exit 1
fi

# Get directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEAMSYNC_BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$TEAMSYNC_BACKEND_DIR")"
ACCESSARC_BACKEND_DIR="$PROJECT_ROOT/access-arc-backend"
ACCESSARC_FRONTEND_DIR="$PROJECT_ROOT/access-arc-frontend"
TEAMSYNC_FRONTEND_DIR="$PROJECT_ROOT/teamsync-frontend"

echo ""
echo "Project root: $PROJECT_ROOT"
echo "AccessArc backend: $ACCESSARC_BACKEND_DIR"
echo "TeamSync backend: $TEAMSYNC_BACKEND_DIR"
echo ""

# Function to deploy a service
deploy_service() {
    local service_name=$1
    local service_dir=$2

    echo ""
    echo "----------------------------------------"
    echo "Deploying: $service_name"
    echo "----------------------------------------"

    if [ ! -d "$service_dir" ]; then
        echo "Warning: Directory not found: $service_dir"
        echo "Skipping $service_name"
        return 1
    fi

    cd "$service_dir"

    # Link to service if not already linked
    railway service "$service_name" 2>/dev/null || railway link --service "$service_name" 2>/dev/null || true

    # Deploy
    railway up --detach

    echo "✓ $service_name deployment initiated"
}

# Show deployment options
echo ""
echo "What would you like to deploy?"
echo "  1) Full platform (AccessArc + TeamSync)"
echo "  2) AccessArc only"
echo "  3) TeamSync only"
echo ""
read -p "Enter choice (1/2/3): " deploy_choice

# Create project if needed
echo ""
echo "Step 1: Initialize Railway Project"
echo "----------------------------------------"

cd "$TEAMSYNC_BACKEND_DIR"
if [ ! -f ".railway/config.json" ]; then
    echo "Initializing new Railway project..."
    railway init --name unified-platform
else
    echo "Railway project already initialized"
fi

# Infrastructure check
echo ""
echo "Step 2: Verify Infrastructure"
echo "----------------------------------------"
echo ""
echo "Required infrastructure plugins (add via Railway Dashboard):"
echo "  1. MongoDB - Primary database"
echo "  2. PostgreSQL - Zitadel database"
echo "  3. Redis - Caching"
echo ""
echo "For TeamSync, also add:"
echo "  4. Kafka (Docker: apache/kafka:3.8.0)"
echo "  5. Elasticsearch (Docker: elasticsearch:8.11.0)"
echo "  6. MinIO (Docker: minio/minio:latest)"
echo ""
read -p "Have you added the required infrastructure? (y/n): " infra_confirm
if [ "$infra_confirm" != "y" ]; then
    echo "Please add infrastructure plugins and run this script again."
    exit 1
fi

# Zitadel check
echo ""
echo "Step 3: Verify Zitadel"
echo "----------------------------------------"
echo ""
echo "Zitadel should be deployed as a Docker image service."
echo "Image: ghcr.io/zitadel/zitadel:v4.7-latest"
echo ""
read -p "Is Zitadel deployed and configured? (y/n): " zitadel_confirm
if [ "$zitadel_confirm" != "y" ]; then
    echo "Please deploy Zitadel first."
    exit 1
fi

# Deploy AccessArc if selected
if [ "$deploy_choice" == "1" ] || [ "$deploy_choice" == "2" ]; then
    echo ""
    echo "========================================"
    echo "  Deploying AccessArc Services"
    echo "========================================"

    # Core services
    echo ""
    echo "Phase 1: AccessArc Core Services"
    echo "----------------------------------------"

    deploy_service "accessarc-user-service" "$ACCESSARC_BACKEND_DIR/user-service"
    deploy_service "accessarc-department-service" "$ACCESSARC_BACKEND_DIR/department-service"
    deploy_service "accessarc-tenant-service" "$ACCESSARC_BACKEND_DIR/tenant-service"
    deploy_service "accessarc-license-service" "$ACCESSARC_BACKEND_DIR/license-service"

    # Supporting services
    echo ""
    echo "Phase 2: AccessArc Supporting Services"
    echo "----------------------------------------"

    deploy_service "accessarc-integration-service" "$ACCESSARC_BACKEND_DIR/integration-service"
    deploy_service "accessarc-ldap-service" "$ACCESSARC_BACKEND_DIR/ldap-service"
    deploy_service "accessarc-workflow-service" "$ACCESSARC_BACKEND_DIR/workflow-service"
    deploy_service "accessarc-workflow-executor" "$ACCESSARC_BACKEND_DIR/workflow-executor-service"
    deploy_service "accessarc-business-rules" "$ACCESSARC_BACKEND_DIR/business-rules-service"

    # API Gateway
    echo ""
    echo "Phase 3: AccessArc API Gateway"
    echo "----------------------------------------"

    deploy_service "accessarc-api-gateway" "$ACCESSARC_BACKEND_DIR/api-gateway"

    # Frontend
    echo ""
    echo "Phase 4: AccessArc Frontend"
    echo "----------------------------------------"

    deploy_service "accessarc-frontend" "$ACCESSARC_FRONTEND_DIR"

    echo ""
    echo "✓ AccessArc deployment complete"
fi

# Deploy TeamSync if selected
if [ "$deploy_choice" == "1" ] || [ "$deploy_choice" == "3" ]; then
    echo ""
    echo "========================================"
    echo "  Deploying TeamSync Services"
    echo "========================================"

    # Core services
    echo ""
    echo "Phase 1: TeamSync Core Services"
    echo "----------------------------------------"

    deploy_service "teamsync-content-service" "$TEAMSYNC_BACKEND_DIR/content-service"
    deploy_service "teamsync-folder-service" "$TEAMSYNC_BACKEND_DIR/folder-service"
    deploy_service "teamsync-storage-service" "$TEAMSYNC_BACKEND_DIR/storage-service"
    deploy_service "teamsync-sharing-service" "$TEAMSYNC_BACKEND_DIR/sharing-service"

    # Collaboration services
    echo ""
    echo "Phase 2: TeamSync Collaboration Services"
    echo "----------------------------------------"

    deploy_service "teamsync-team-service" "$TEAMSYNC_BACKEND_DIR/team-service"
    deploy_service "teamsync-project-service" "$TEAMSYNC_BACKEND_DIR/project-service"
    deploy_service "teamsync-workflow-execution-service" "$TEAMSYNC_BACKEND_DIR/workflow-execution-service"
    deploy_service "teamsync-trash-service" "$TEAMSYNC_BACKEND_DIR/trash-service"

    # Search & AI services
    echo ""
    echo "Phase 3: TeamSync Search & AI Services"
    echo "----------------------------------------"

    deploy_service "teamsync-search-service" "$TEAMSYNC_BACKEND_DIR/search-service"
    deploy_service "teamsync-chat-service" "$TEAMSYNC_BACKEND_DIR/chat-service"

    # Supporting services
    echo ""
    echo "Phase 4: TeamSync Supporting Services"
    echo "----------------------------------------"

    deploy_service "teamsync-notification-service" "$TEAMSYNC_BACKEND_DIR/notification-service"
    deploy_service "teamsync-activity-service" "$TEAMSYNC_BACKEND_DIR/activity-service"
    deploy_service "teamsync-wopi-service" "$TEAMSYNC_BACKEND_DIR/wopi-service"
    deploy_service "teamsync-settings-service" "$TEAMSYNC_BACKEND_DIR/settings-service"
    deploy_service "teamsync-presence-service" "$TEAMSYNC_BACKEND_DIR/presence-service"

    # Permission Manager
    echo ""
    echo "Phase 5: TeamSync Permission Manager"
    echo "----------------------------------------"

    deploy_service "teamsync-permission-manager-service" "$TEAMSYNC_BACKEND_DIR/permission-manager-service"

    # API Gateway
    echo ""
    echo "Phase 6: TeamSync API Gateway"
    echo "----------------------------------------"

    deploy_service "teamsync-api-gateway" "$TEAMSYNC_BACKEND_DIR/api-gateway"

    # Frontend
    echo ""
    echo "Phase 7: TeamSync Frontend"
    echo "----------------------------------------"

    deploy_service "teamsync-frontend" "$TEAMSYNC_FRONTEND_DIR"

    echo ""
    echo "✓ TeamSync deployment complete"
fi

echo ""
echo "=========================================="
echo "  Deployment Complete!"
echo "=========================================="
echo ""

if [ "$deploy_choice" == "1" ]; then
    echo "Deployed platforms:"
    echo "  - AccessArc (10 backend services + 1 frontend)"
    echo "  - TeamSync (17 backend services + 1 frontend)"
    echo ""
    echo "Total: 28 services + shared infrastructure"
elif [ "$deploy_choice" == "2" ]; then
    echo "Deployed: AccessArc (10 backend services + 1 frontend)"
else
    echo "Deployed: TeamSync (17 backend services + 1 frontend)"
fi

echo ""
echo "Next steps:"
echo "1. Check deployment status: railway status"
echo "2. View logs: railway logs --service <service-name>"
echo "3. Configure environment variables in Railway Dashboard"
echo "4. Add custom domains for API Gateways and Frontends"
echo ""
echo "See railway/DEPLOYMENT.md for required environment variables."
echo ""
