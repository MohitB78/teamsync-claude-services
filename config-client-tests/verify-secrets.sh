#!/bin/bash

# =============================================================================
# Config Server & Vault Secrets Verification Script
# =============================================================================
# This script verifies that Config Server is correctly serving secrets from
# Vault to all services in both TeamSync and AccessArc platforms.
#
# Usage:
#   ./verify-secrets.sh [options]
#
# Options:
#   --vault-only      Test Vault directly (requires Vault access)
#   --config-only     Test Config Server only
#   --all             Test both (default)
#   --show-values     Show actual secret values (CAUTION: sensitive data!)
#   --railway         Use Railway endpoints
#   --local           Use local endpoints (default)
#
# Environment Variables:
#   CONFIG_SERVER_URL      - Config Server URL
#   CONFIG_SERVER_USER     - Config Server username
#   CONFIG_SERVER_PASSWORD - Config Server password
#   VAULT_ADDR            - Vault URL
#   VAULT_TOKEN           - Vault token
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Default configuration
ENVIRONMENT="local"
TEST_VAULT=true
TEST_CONFIG=true
SHOW_VALUES=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --vault-only)
            TEST_CONFIG=false
            shift
            ;;
        --config-only)
            TEST_VAULT=false
            shift
            ;;
        --all)
            TEST_VAULT=true
            TEST_CONFIG=true
            shift
            ;;
        --show-values)
            SHOW_VALUES=true
            shift
            ;;
        --railway)
            ENVIRONMENT="railway"
            shift
            ;;
        --local)
            ENVIRONMENT="local"
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--vault-only|--config-only|--all] [--show-values] [--railway|--local]"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Set defaults based on environment
if [ "$ENVIRONMENT" == "railway" ]; then
    : ${CONFIG_SERVER_URL:="http://config-server.railway.internal:8888"}
    : ${VAULT_ADDR:="http://vault.railway.internal:8200"}
else
    : ${CONFIG_SERVER_URL:="http://localhost:8888"}
    : ${VAULT_ADDR:="http://localhost:8200"}
fi

: ${CONFIG_SERVER_USER:="config"}
: ${CONFIG_SERVER_PASSWORD:="config-secret"}
: ${VAULT_TOKEN:="root-token"}

# Service lists
TEAMSYNC_SERVICES=(
    "teamsync-api-gateway"
    "teamsync-content-service"
    "teamsync-storage-service"
    "teamsync-sharing-service"
    "teamsync-team-service"
    "teamsync-project-service"
    "teamsync-workflow-execution-service"
    "teamsync-trash-service"
    "teamsync-search-service"
    "teamsync-chat-service"
    "teamsync-notification-service"
    "teamsync-activity-service"
    "teamsync-wopi-service"
    "teamsync-settings-service"
    "teamsync-presence-service"
    "teamsync-permission-manager-service"
)

ACCESSARC_SERVICES=(
    "api-gateway"
    "user-service"
    "department-service"
    "tenant-service"
    "license-service"
    "integration-service"
    "ldap-service"
    "workflow-service"
    "business-rules-service"
)

# Vault KV v2 secret paths
VAULT_SECRET_PATHS=(
    "secret/data/platform/mongodb"
    "secret/data/platform/redis"
    "secret/data/platform/jwt"
    "secret/data/platform/config-server"
    "secret/data/platform/minio"
    "secret/data/platform/zitadel"
    "secret/data/platform/zitadel-db"
    "secret/data/platform/ldap"
    "secret/data/platform/wopi"
    "secret/data/platform/session"
    "secret/data/platform/teamsync"
    "secret/data/platform/kafka"
    "secret/data/platform/elasticsearch"
)

# Helper functions
print_header() {
    echo ""
    echo -e "${BOLD}${BLUE}============================================${NC}"
    echo -e "${BOLD}${BLUE}  $1${NC}"
    echo -e "${BOLD}${BLUE}============================================${NC}"
    echo ""
}

print_section() {
    echo ""
    echo -e "${CYAN}--- $1 ---${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

redact_value() {
    local value=$1
    local length=${#value}
    if [ $length -le 4 ]; then
        echo "****"
    else
        echo "${value:0:2}****${value: -2}"
    fi
}

# =============================================================================
# VAULT TESTS
# =============================================================================
test_vault() {
    print_header "Vault Secrets Verification"

    echo "Vault Address: $VAULT_ADDR"
    echo ""

    # Check Vault health
    print_section "Vault Health Check"
    VAULT_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$VAULT_ADDR/v1/sys/health" 2>/dev/null || echo "000")

    if [ "$VAULT_HEALTH" == "200" ] || [ "$VAULT_HEALTH" == "429" ] || [ "$VAULT_HEALTH" == "472" ] || [ "$VAULT_HEALTH" == "473" ]; then
        print_success "Vault is reachable (HTTP $VAULT_HEALTH)"
    else
        print_error "Vault is not reachable (HTTP $VAULT_HEALTH)"
        return 1
    fi

    # List all secrets
    print_section "Vault KV v2 Secrets (secret/platform/*)"

    echo ""
    echo -e "${BOLD}Secret Path                              | Keys${NC}"
    echo "------------------------------------------|--------------------------------------------------"

    for path in "${VAULT_SECRET_PATHS[@]}"; do
        # Extract the short name
        short_name=$(echo "$path" | sed 's|secret/data/platform/||')

        # Fetch secret
        RESPONSE=$(curl -s -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/$path" 2>/dev/null)

        if echo "$RESPONSE" | jq -e '.data.data' > /dev/null 2>&1; then
            KEYS=$(echo "$RESPONSE" | jq -r '.data.data | keys | join(", ")')
            printf "%-42s| %s\n" "secret/platform/$short_name" "$KEYS"

            # Show values if requested
            if [ "$SHOW_VALUES" == "true" ]; then
                echo "$RESPONSE" | jq -r '.data.data | to_entries[] | "    \(.key): \(.value)"' | while read line; do
                    if [ "$SHOW_VALUES" == "true" ]; then
                        echo "    $line"
                    fi
                done
            fi
        else
            printf "%-42s| ${RED}(not found or empty)${NC}\n" "secret/platform/$short_name"
        fi
    done

    # Check database secrets engine
    print_section "Vault Database Secrets Engine"

    DB_ROLES=$(curl -s -H "X-Vault-Token: $VAULT_TOKEN" "$VAULT_ADDR/v1/database/roles?list=true" 2>/dev/null)

    if echo "$DB_ROLES" | jq -e '.data.keys' > /dev/null 2>&1; then
        print_success "Database secrets engine is enabled"
        echo ""
        echo "Available roles:"
        echo "$DB_ROLES" | jq -r '.data.keys[]' | while read role; do
            echo "  - $role"
        done
    else
        print_warning "Database secrets engine not enabled or no roles configured"
    fi

    echo ""
}

# =============================================================================
# CONFIG SERVER TESTS
# =============================================================================
test_config_server() {
    print_header "Config Server Secrets Verification"

    echo "Config Server: $CONFIG_SERVER_URL"
    echo "Profile: railway"
    echo ""

    # Check Config Server health
    print_section "Config Server Health Check"
    CS_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" "$CONFIG_SERVER_URL/actuator/health" 2>/dev/null || echo "000")

    if [ "$CS_HEALTH" == "200" ]; then
        print_success "Config Server is reachable (HTTP $CS_HEALTH)"
    else
        print_error "Config Server is not reachable (HTTP $CS_HEALTH)"
        print_warning "Make sure Config Server is running and credentials are correct"
        return 1
    fi

    # Test shared configuration (application-railway.yml)
    print_section "Shared Configuration (application/railway)"

    SHARED_CONFIG=$(curl -s -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" \
        "$CONFIG_SERVER_URL/application/railway" 2>/dev/null)

    if echo "$SHARED_CONFIG" | jq -e '.propertySources' > /dev/null 2>&1; then
        SOURCES=$(echo "$SHARED_CONFIG" | jq -r '.propertySources | length')
        print_success "Shared config loaded ($SOURCES property sources)"

        echo ""
        echo "Property Sources:"
        echo "$SHARED_CONFIG" | jq -r '.propertySources[].name' | while read source; do
            echo "  - $source"
        done

        # Check for Vault secrets in shared config
        echo ""
        echo "Vault-sourced secrets present in shared config:"

        # Check MongoDB
        MONGODB_URI=$(echo "$SHARED_CONFIG" | jq -r '.propertySources[].source["spring.data.mongodb.uri"] // empty' | head -1)
        if [ -n "$MONGODB_URI" ]; then
            if [ "$SHOW_VALUES" == "true" ]; then
                print_success "spring.data.mongodb.uri: $(redact_value "$MONGODB_URI")"
            else
                print_success "spring.data.mongodb.uri: [REDACTED]"
            fi
        else
            print_warning "spring.data.mongodb.uri: (not set - may use component properties)"
        fi

        # Check other secrets
        for key in "jwt.secret" "zitadel.project-id" "session.secret" "wopi.token-secret"; do
            VALUE=$(echo "$SHARED_CONFIG" | jq -r ".propertySources[].source[\"$key\"] // empty" | head -1)
            if [ -n "$VALUE" ] && [ "$VALUE" != "null" ]; then
                if [ "$SHOW_VALUES" == "true" ]; then
                    print_success "$key: $(redact_value "$VALUE")"
                else
                    print_success "$key: [REDACTED]"
                fi
            else
                print_warning "$key: (not set)"
            fi
        done
    else
        print_error "Failed to load shared configuration"
    fi

    # Test TeamSync services
    print_section "TeamSync Services Configuration"

    echo ""
    echo -e "${BOLD}Service                              | Config Status | Vault Secrets${NC}"
    echo "-------------------------------------|---------------|-------------------------"

    for service in "${TEAMSYNC_SERVICES[@]}"; do
        CONFIG=$(curl -s -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" \
            "$CONFIG_SERVER_URL/$service/railway" 2>/dev/null)

        if echo "$CONFIG" | jq -e '.propertySources' > /dev/null 2>&1; then
            SOURCES=$(echo "$CONFIG" | jq -r '.propertySources | length')

            # Check for vault source
            HAS_VAULT=$(echo "$CONFIG" | jq -r '.propertySources[].name' | grep -c "vault" || echo "0")

            if [ "$HAS_VAULT" -gt "0" ]; then
                VAULT_STATUS="${GREEN}Yes${NC}"
            else
                VAULT_STATUS="${YELLOW}Native${NC}"
            fi

            printf "%-37s| ${GREEN}OK${NC} ($SOURCES sources)  | $VAULT_STATUS\n" "$service"
        else
            printf "%-37s| ${RED}FAILED${NC}        | -\n" "$service"
        fi
    done

    # Test AccessArc services
    print_section "AccessArc Services Configuration"

    echo ""
    echo -e "${BOLD}Service                              | Config Status | Vault Secrets${NC}"
    echo "-------------------------------------|---------------|-------------------------"

    for service in "${ACCESSARC_SERVICES[@]}"; do
        CONFIG=$(curl -s -u "$CONFIG_SERVER_USER:$CONFIG_SERVER_PASSWORD" \
            "$CONFIG_SERVER_URL/$service/railway" 2>/dev/null)

        if echo "$CONFIG" | jq -e '.propertySources' > /dev/null 2>&1; then
            SOURCES=$(echo "$CONFIG" | jq -r '.propertySources | length')

            # Check for vault source
            HAS_VAULT=$(echo "$CONFIG" | jq -r '.propertySources[].name' | grep -c "vault" || echo "0")

            if [ "$HAS_VAULT" -gt "0" ]; then
                VAULT_STATUS="${GREEN}Yes${NC}"
            else
                VAULT_STATUS="${YELLOW}Native${NC}"
            fi

            printf "%-37s| ${GREEN}OK${NC} ($SOURCES sources)  | $VAULT_STATUS\n" "$service"
        else
            printf "%-37s| ${RED}FAILED${NC}        | -\n" "$service"
        fi
    done

    echo ""
}

# =============================================================================
# GENERATE COMPREHENSIVE SECRETS REPORT
# =============================================================================
generate_secrets_report() {
    print_header "Comprehensive Secrets Report"

    echo "This report shows all secrets that are configured to be served by"
    echo "Config Server from Vault to the services."
    echo ""

    print_section "Vault Secret Paths and Their Purposes"

    cat << 'EOF'
+----------------------------+---------------------------------------------------+
| Vault Path                 | Purpose                                           |
+----------------------------+---------------------------------------------------+
| secret/platform/mongodb    | MongoDB connection credentials                    |
|                            | Keys: uri, host, port, username, password,        |
|                            |       auth-source                                 |
+----------------------------+---------------------------------------------------+
| secret/platform/redis      | Redis connection settings                         |
|                            | Keys: url, host, port                             |
+----------------------------+---------------------------------------------------+
| secret/platform/jwt        | JWT token signing configuration                   |
|                            | Keys: secret, issuer, audience                    |
+----------------------------+---------------------------------------------------+
| secret/platform/config-srv | Config Server authentication                      |
|                            | Keys: username, password                          |
+----------------------------+---------------------------------------------------+
| secret/platform/minio      | MinIO/S3 storage credentials                      |
|                            | Keys: endpoint, access-key, secret-key            |
+----------------------------+---------------------------------------------------+
| secret/platform/zitadel    | Zitadel OIDC configuration                        |
|                            | Keys: url, issuer, project-id, client-id,         |
|                            |       client-secret                               |
+----------------------------+---------------------------------------------------+
| secret/platform/zitadel-db | Zitadel PostgreSQL database                       |
|                            | Keys: username, password, jdbc-url                |
+----------------------------+---------------------------------------------------+
| secret/platform/ldap       | LDAP server credentials                           |
|                            | Keys: admin-password, config-password, bind-dn    |
+----------------------------+---------------------------------------------------+
| secret/platform/wopi       | WOPI token for Office editing                     |
|                            | Keys: token-secret                                |
+----------------------------+---------------------------------------------------+
| secret/platform/session    | Session management secrets                        |
|                            | Keys: secret, encryption-key                      |
+----------------------------+---------------------------------------------------+
| secret/platform/teamsync   | TeamSync specific secrets                         |
|                            | Keys: download-token-secret                       |
+----------------------------+---------------------------------------------------+
| secret/platform/kafka      | Kafka/Redpanda configuration                      |
|                            | Keys: bootstrap-servers                           |
+----------------------------+---------------------------------------------------+
| secret/platform/elastic... | Elasticsearch configuration                       |
|                            | Keys: uri                                         |
+----------------------------+---------------------------------------------------+
EOF

    print_section "Database Secrets Engine Roles"

    cat << 'EOF'
+---------------------------+---------------------+---------------------------+
| Role Name                 | Database            | Services Using            |
+---------------------------+---------------------+---------------------------+
| accessarc-users           | accessarc_users     | user-service              |
| accessarc-departments     | accessarc_depts     | department-service        |
| accessarc-tenants         | accessarc_tenants   | tenant-service            |
| accessarc-licenses        | accessarc_licenses  | license-service           |
| accessarc-integrations    | accessarc_integ     | integration-service       |
| accessarc-ldap            | accessarc_ldap      | ldap-service              |
| accessarc-workflows       | accessarc_workflows | workflow-service          |
| accessarc-business-rules  | accessarc_bus_rules | business-rules-service    |
| teamsync-shared           | teamsync            | All TeamSync services     |
| teamsync-permissions      | teamsync_perms      | permission-manager-service|
+---------------------------+---------------------+---------------------------+
EOF

    print_section "Services and Their Secret Dependencies"

    cat << 'EOF'
TeamSync Services (16 total):
  - All services need: mongodb, redis, jwt, zitadel
  - Storage services: + minio secrets
  - WOPI service: + wopi secrets
  - Chat service: + AI provider secrets (openai/anthropic)
  - Notification service: + sendgrid, firebase secrets
  - API Gateway: + session secrets

AccessArc Services (9 total):
  - All services need: mongodb, redis, jwt, zitadel
  - API Gateway: + session secrets
  - LDAP service: + ldap secrets
EOF

    echo ""
}

# =============================================================================
# MAIN
# =============================================================================
print_header "Config Server & Vault Secrets Verification"

echo "Environment: $ENVIRONMENT"
echo "Date: $(date)"
echo ""

if [ "$TEST_VAULT" == "true" ]; then
    test_vault || true
fi

if [ "$TEST_CONFIG" == "true" ]; then
    test_config_server || true
fi

generate_secrets_report

print_header "Verification Complete"

echo "Summary:"
if [ "$TEST_VAULT" == "true" ]; then
    echo "  - Vault secrets: Tested"
fi
if [ "$TEST_CONFIG" == "true" ]; then
    echo "  - Config Server: Tested"
fi
echo ""
echo "For more details, run with --show-values (CAUTION: shows sensitive data)"
echo ""
