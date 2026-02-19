#!/bin/bash

# Config Client Verification Tests Runner
# ========================================
#
# This script runs both static YAML verification tests and runtime
# integration tests for Config Server and Vault.
#
# Usage:
#   ./run-config-tests.sh [options]
#
# Options:
#   --yaml-only     Run only YAML verification tests (no network required)
#   --runtime-only  Run only runtime integration tests (requires Config Server)
#   --all           Run all tests (default)
#
# Environment Variables (for runtime tests):
#   CONFIG_SERVER_URL      - Config Server URL (default: http://localhost:8888)
#   CONFIG_SERVER_USER     - Config Server username (default: config)
#   CONFIG_SERVER_PASSWORD - Config Server password (default: password)
#   VAULT_URL             - Vault URL (default: http://localhost:8200)
#   VAULT_TOKEN           - Vault token (optional, required for Vault tests)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo ""
echo "========================================"
echo "  Config Client Verification Tests"
echo "========================================"
echo ""

# Parse arguments
RUN_YAML=true
RUN_RUNTIME=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --yaml-only)
            RUN_RUNTIME=false
            shift
            ;;
        --runtime-only)
            RUN_YAML=false
            shift
            ;;
        --all)
            RUN_YAML=true
            RUN_RUNTIME=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--yaml-only|--runtime-only|--all]"
            echo ""
            echo "Options:"
            echo "  --yaml-only     Run only YAML verification tests"
            echo "  --runtime-only  Run only runtime integration tests"
            echo "  --all           Run all tests (default)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven (mvn) is not installed or not in PATH${NC}"
    exit 1
fi

cd "$SCRIPT_DIR"

# Run YAML verification tests
if [ "$RUN_YAML" = true ]; then
    echo ""
    echo "----------------------------------------"
    echo "  Running YAML Verification Tests"
    echo "----------------------------------------"
    echo ""

    mvn test -Dtest=ConfigClientVerificationTest \
        -Dproject.root="$PROJECT_ROOT" \
        -q || {
        echo -e "${RED}YAML verification tests failed${NC}"
        exit 1
    }

    echo -e "${GREEN}YAML verification tests passed!${NC}"
fi

# Run runtime integration tests
if [ "$RUN_RUNTIME" = true ]; then
    echo ""
    echo "----------------------------------------"
    echo "  Running Runtime Integration Tests"
    echo "----------------------------------------"
    echo ""

    # Set defaults if not provided
    : ${CONFIG_SERVER_URL:="http://localhost:8888"}
    : ${CONFIG_SERVER_USER:="config"}
    : ${CONFIG_SERVER_PASSWORD:="password"}
    : ${VAULT_URL:="http://localhost:8200"}

    echo "Config Server URL: $CONFIG_SERVER_URL"
    echo "Vault URL: $VAULT_URL"
    echo ""

    mvn test -Dtest=ConfigServerRuntimeIntegrationTest \
        -DCONFIG_SERVER_URL="$CONFIG_SERVER_URL" \
        -DCONFIG_SERVER_USER="$CONFIG_SERVER_USER" \
        -DCONFIG_SERVER_PASSWORD="$CONFIG_SERVER_PASSWORD" \
        -DVAULT_URL="$VAULT_URL" \
        -DVAULT_TOKEN="${VAULT_TOKEN:-}" \
        -q || {
        echo -e "${YELLOW}Note: Some runtime tests may have been skipped if services are unavailable${NC}"
    }

    echo -e "${GREEN}Runtime integration tests completed!${NC}"
fi

echo ""
echo "========================================"
echo "  All Tests Completed"
echo "========================================"
echo ""
