#!/bin/bash

# TeamSync Login Integration Test Runner
#
# This script runs the comprehensive login integration test that traces
# the complete authentication flow from frontend to backend.
#
# Prerequisites:
#   - jbang (install via: curl -Ls https://sh.jbang.dev | bash)
#   - TeamSync API Gateway running
#   - Zitadel running and configured
#
# Usage:
#   ./test-login-flow.sh                    # Run with defaults (local development)
#   ./test-login-flow.sh --railway          # Run against Railway deployment
#   ./test-login-flow.sh --verbose          # Run with verbose output
#   ./test-login-flow.sh --step 3           # Run only up to step 3

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default values for local development
API_GATEWAY_URL="${API_GATEWAY_URL:-http://localhost:9080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3001}"
ZITADEL_URL="${ZITADEL_URL:-http://localhost:8085}"
TEST_EMAIL="${TEST_EMAIL:-admin@teamsync.local}"
TEST_PASSWORD="${TEST_PASSWORD:-Admin@Teamsync2024!}"
SERVICE_TOKEN="${ZITADEL_SERVICE_USER_TOKEN:-}"

# Parse command line arguments
VERBOSE=""
MAX_STEP=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --railway)
            # Railway deployment URLs
            API_GATEWAY_URL="${RAILWAY_API_GATEWAY_URL:-https://teamsync-api-gateway-production.up.railway.app}"
            FRONTEND_URL="${RAILWAY_FRONTEND_URL:-https://teamsync-frontend-production.up.railway.app}"
            ZITADEL_URL="${RAILWAY_ZITADEL_URL:-https://zitadel-production-1bd8.up.railway.app}"
            shift
            ;;
        --verbose|-v)
            VERBOSE="--verbose"
            shift
            ;;
        --step)
            MAX_STEP="--step $2"
            shift 2
            ;;
        --email)
            TEST_EMAIL="$2"
            shift 2
            ;;
        --password)
            TEST_PASSWORD="$2"
            shift 2
            ;;
        --service-token)
            SERVICE_TOKEN="$2"
            shift 2
            ;;
        --api-gateway)
            API_GATEWAY_URL="$2"
            shift 2
            ;;
        --frontend)
            FRONTEND_URL="$2"
            shift 2
            ;;
        --zitadel)
            ZITADEL_URL="$2"
            shift 2
            ;;
        --help|-h)
            echo "TeamSync Login Integration Test"
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --railway           Use Railway deployment URLs"
            echo "  --verbose, -v       Enable verbose output"
            echo "  --step <n>          Run only up to step N (1-6)"
            echo "  --email <email>     Test user email"
            echo "  --password <pass>   Test user password"
            echo "  --service-token <t> Zitadel service user token"
            echo "  --api-gateway <url> API Gateway URL"
            echo "  --frontend <url>    Frontend URL"
            echo "  --zitadel <url>     Zitadel URL"
            echo "  --help, -h          Show this help"
            echo ""
            echo "Environment Variables:"
            echo "  API_GATEWAY_URL             Override API Gateway URL"
            echo "  FRONTEND_URL                Override Frontend URL"
            echo "  ZITADEL_URL                 Override Zitadel URL"
            echo "  TEST_EMAIL                  Override test user email"
            echo "  TEST_PASSWORD               Override test user password"
            echo "  ZITADEL_SERVICE_USER_TOKEN  Zitadel service token for Session API"
            echo ""
            echo "Examples:"
            echo "  $0                          # Run with local defaults"
            echo "  $0 --railway --verbose      # Run against Railway with verbose output"
            echo "  $0 --step 3 --verbose       # Run only steps 1-3 with verbose output"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check for jbang
if ! command -v jbang &> /dev/null; then
    echo "Error: jbang is not installed."
    echo ""
    echo "Install jbang using one of these methods:"
    echo "  curl -Ls https://sh.jbang.dev | bash"
    echo "  brew install jbangdev/tap/jbang"
    echo "  sdk install jbang"
    echo ""
    exit 1
fi

# Print configuration
echo "═══════════════════════════════════════════════════════════════════════════"
echo "TeamSync Login Integration Test"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""
echo "Configuration:"
echo "  API Gateway: $API_GATEWAY_URL"
echo "  Frontend:    $FRONTEND_URL"
echo "  Zitadel:     $ZITADEL_URL"
echo "  Test User:   $TEST_EMAIL"
echo "  Service Token: $([ -n "$SERVICE_TOKEN" ] && echo "***provided***" || echo "(not set)")"
echo ""

# Build command
CMD="jbang $SCRIPT_DIR/TeamSyncLoginIntegrationTest.java"
CMD="$CMD --api-gateway $API_GATEWAY_URL"
CMD="$CMD --frontend $FRONTEND_URL"
CMD="$CMD --zitadel $ZITADEL_URL"
CMD="$CMD --email $TEST_EMAIL"
CMD="$CMD --password $TEST_PASSWORD"

if [ -n "$SERVICE_TOKEN" ]; then
    CMD="$CMD --service-token $SERVICE_TOKEN"
fi

if [ -n "$VERBOSE" ]; then
    CMD="$CMD $VERBOSE"
fi

if [ -n "$MAX_STEP" ]; then
    CMD="$CMD $MAX_STEP"
fi

# Run the test
echo "Running: $CMD"
echo ""
exec $CMD
