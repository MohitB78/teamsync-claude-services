#!/bin/bash
# =============================================================================
# Test TeamSync Content Service on Railway Deployment
# =============================================================================
# This script:
# 1. Authenticates with Zitadel using Session API v2
# 2. Fetches documents from the user's personal drive (mydrive)
# 3. Lists all documents found
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Railway Production URLs
ZITADEL_URL="https://teamsync-zitadel-production.up.railway.app"
ZITADEL_CLIENT_ID="teamsync-bff"
API_GATEWAY_URL="https://teamsync-api-gateway-production.up.railway.app"

# Test user credentials
ADMIN_USERNAME="admin@accessarc.com"
ADMIN_PASSWORD="password"
TENANT_ID="default"

echo -e "${BOLD}================================================================================${NC}"
echo -e "${BOLD}TeamSync Content Service Integration Test - Railway Deployment${NC}"
echo -e "${BOLD}================================================================================${NC}"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo -e "  Zitadel URL: ${ZITADEL_URL}"
echo -e "  API Gateway URL: ${API_GATEWAY_URL}"
echo -e "  Test User: ${ADMIN_USERNAME}"
echo ""

# =============================================================================
# Step 1: Authenticate with Zitadel using Session API v2
# =============================================================================
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Step 1: Authenticating with Zitadel Session API v2...${NC}"
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"

# Step 1a: Create session with user check
echo -e "  Creating session with user check..."
SESSION_RESPONSE=$(curl -s -X POST "${ZITADEL_URL}/v2/sessions" \
    -H "Content-Type: application/json" \
    -d "{
        \"checks\": {
            \"user\": {
                \"loginName\": \"${ADMIN_USERNAME}\"
            }
        }
    }")

if echo "$SESSION_RESPONSE" | grep -q "sessionId"; then
    SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['sessionId'])")
    SESSION_TOKEN=$(echo "$SESSION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['sessionToken'])")
    echo -e "  ${GREEN}Session created: ${SESSION_ID}${NC}"
else
    echo -e "  ${RED}ERROR: Failed to create session${NC}"
    echo -e "  Response: ${SESSION_RESPONSE}"
    exit 1
fi

# Step 1b: Verify password
echo -e "  Verifying password..."
PASSWORD_RESPONSE=$(curl -s -X PATCH "${ZITADEL_URL}/v2/sessions/${SESSION_ID}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${SESSION_TOKEN}" \
    -d "{
        \"checks\": {
            \"password\": {
                \"password\": \"${ADMIN_PASSWORD}\"
            }
        }
    }")

if echo "$PASSWORD_RESPONSE" | grep -q "sessionToken"; then
    SESSION_TOKEN=$(echo "$PASSWORD_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['sessionToken'])")
    echo -e "  ${GREEN}Password verified!${NC}"
else
    echo -e "  ${RED}ERROR: Password verification failed${NC}"
    echo -e "  Response: ${PASSWORD_RESPONSE}"
    exit 1
fi

# Step 1c: Exchange session for OAuth2 tokens
echo -e "  Exchanging session for OAuth2 tokens..."
TOKEN_RESPONSE=$(curl -s -X POST "${ZITADEL_URL}/oauth/v2/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
    -d "client_id=${ZITADEL_CLIENT_ID}" \
    -d "assertion=${SESSION_TOKEN}" \
    -d "scope=openid profile email")

if echo "$TOKEN_RESPONSE" | grep -q "access_token"; then
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['access_token'])")
    echo -e "  ${GREEN}Authentication successful!${NC}"

    # Decode JWT to get user info (middle part is the payload)
    JWT_PART2=$(echo "$ACCESS_TOKEN" | cut -d'.' -f2)
    # Add padding if needed
    case $((${#JWT_PART2} % 4)) in
        2) JWT_PART2="${JWT_PART2}==" ;;
        3) JWT_PART2="${JWT_PART2}=" ;;
    esac
    # Replace URL-safe chars with standard base64
    JWT_PART2=$(echo "$JWT_PART2" | tr '_-' '/+')
    JWT_PAYLOAD=$(echo "$JWT_PART2" | base64 -d 2>/dev/null || echo "$JWT_PART2" | base64 -D 2>/dev/null)

    USER_ID=$(echo "$JWT_PAYLOAD" | python3 -c "import sys, json; print(json.load(sys.stdin)['sub'])")
    EMAIL=$(echo "$JWT_PAYLOAD" | python3 -c "import sys, json; print(json.load(sys.stdin).get('email', 'N/A'))")
    NAME=$(echo "$JWT_PAYLOAD" | python3 -c "import sys, json; print(json.load(sys.stdin).get('name', 'N/A'))")

    # Personal drive ID follows the pattern: personal-{userId}
    DRIVE_ID="personal-${USER_ID}"

    echo -e "  User ID: ${USER_ID}"
    echo -e "  Email: ${EMAIL}"
    echo -e "  Name: ${NAME}"
    echo -e "  Personal Drive ID: ${DRIVE_ID}"
    echo -e "  Access Token: ${ACCESS_TOKEN:0:50}..."
else
    echo -e "  ${RED}ERROR: Token exchange failed${NC}"
    echo -e "  Response: ${TOKEN_RESPONSE}"
    exit 1
fi

echo ""

# =============================================================================
# Step 2: Get documents from mydrive
# =============================================================================
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Step 2: Fetching documents from MyDrive...${NC}"
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"

DOCS_URL="${API_GATEWAY_URL}/api/documents?limit=100"
echo -e "  Request URL: ${DOCS_URL}"
echo -e "  Headers: X-Tenant-ID=${TENANT_ID}, X-Drive-ID=${DRIVE_ID}, X-User-ID=${USER_ID}"

DOCS_RESPONSE=$(curl -s -X GET "${DOCS_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${DOCS_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

echo -e "  Response Status: ${HTTP_STATUS}"

if [ "$HTTP_STATUS" = "200" ]; then
    SUCCESS=$(echo "$DOCS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('success', False))")

    if [ "$SUCCESS" = "True" ]; then
        echo ""
        echo -e "${BOLD}=== Documents in MyDrive for admin@accessarc.com ===${NC}"

        # Parse and display documents
        python3 << PYTHON_SCRIPT
import json
import sys

response = json.loads('''${DOCS_RESPONSE}''')
data = response.get('data', {})
items = data.get('items', [])
has_more = data.get('hasMore', False)

print(f"Total documents found: {len(items)}")
print(f"Has more pages: {has_more}")
print()

if items:
    print(f"{'Document Name':<40} {'Type':<12} {'Size':<12} {'Status':<15}")
    print("-" * 82)

    for doc in items:
        name = doc.get('name', 'N/A')[:38]
        ext = doc.get('extension', 'N/A').upper() if doc.get('extension') else 'N/A'
        size_bytes = doc.get('fileSize', 0)

        # Format file size
        if size_bytes < 1024:
            size = f"{size_bytes} B"
        elif size_bytes < 1024 * 1024:
            size = f"{size_bytes / 1024:.1f} KB"
        elif size_bytes < 1024 * 1024 * 1024:
            size = f"{size_bytes / (1024 * 1024):.1f} MB"
        else:
            size = f"{size_bytes / (1024 * 1024 * 1024):.1f} GB"

        status = doc.get('status', 'N/A')

        print(f"{name:<40} {ext:<12} {size:<12} {status:<15}")

        # Print additional details
        if doc.get('id'):
            print(f"    ID: {doc['id']}")
        if doc.get('description'):
            print(f"    Description: {doc['description']}")
        if doc.get('tags'):
            print(f"    Tags: {doc['tags']}")
        starred = doc.get('isStarred', False)
        pinned = doc.get('isPinned', False)
        print(f"    Starred: {starred}, Pinned: {pinned}")
        print()

    print("-" * 82)
else:
    print("No documents found in mydrive.")
    print("To seed sample documents, configure MongoDB with sample data.")
PYTHON_SCRIPT

    else
        echo -e "  ${RED}API Error: $(echo "$DOCS_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('error', 'Unknown error'))")${NC}"
    fi
else
    echo -e "  ${RED}ERROR: ${DOCS_RESPONSE}${NC}"
fi

echo ""

# =============================================================================
# Step 3: Get starred documents
# =============================================================================
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Step 3: Fetching starred documents...${NC}"
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"

STARRED_URL="${API_GATEWAY_URL}/api/documents/starred"

STARRED_RESPONSE=$(curl -s -X GET "${STARRED_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${STARRED_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

echo -e "  Response Status: ${HTTP_STATUS}"

if [ "$HTTP_STATUS" = "200" ]; then
    python3 << PYTHON_SCRIPT
import json

response = json.loads('''${STARRED_RESPONSE}''')
if response.get('success'):
    data = response.get('data', [])
    print(f"  Starred documents found: {len(data)}")
    for doc in data:
        name = doc.get('name', 'N/A')
        size_bytes = doc.get('fileSize', 0)
        if size_bytes < 1024:
            size = f"{size_bytes} B"
        elif size_bytes < 1024 * 1024:
            size = f"{size_bytes / 1024:.1f} KB"
        else:
            size = f"{size_bytes / (1024 * 1024):.1f} MB"
        print(f"    - {name} ({size})")
else:
    print(f"  Error: {response.get('error', 'Unknown error')}")
PYTHON_SCRIPT
else
    echo -e "  ${RED}ERROR: ${STARRED_RESPONSE}${NC}"
fi

echo ""

# =============================================================================
# Step 4: Get document count
# =============================================================================
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Step 4: Getting document count...${NC}"
echo -e "${YELLOW}--------------------------------------------------------------------------------${NC}"

COUNT_URL="${API_GATEWAY_URL}/api/documents/count"

COUNT_RESPONSE=$(curl -s -X GET "${COUNT_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${COUNT_URL}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Drive-ID: ${DRIVE_ID}" \
    -H "X-User-ID: ${USER_ID}" \
    -H "Accept: application/json")

echo -e "  Response Status: ${HTTP_STATUS}"

if [ "$HTTP_STATUS" = "200" ]; then
    python3 << PYTHON_SCRIPT
import json

response = json.loads('''${COUNT_RESPONSE}''')
if response.get('success'):
    data = response.get('data', {})
    count = data.get('count', 0)
    print(f"  Total document count in drive: {count}")
else:
    print(f"  Error: {response.get('error', 'Unknown error')}")
PYTHON_SCRIPT
else
    echo -e "  ${RED}ERROR: ${COUNT_RESPONSE}${NC}"
fi

echo ""
echo -e "${BOLD}================================================================================${NC}"
echo -e "${GREEN}Test completed!${NC}"
echo -e "${BOLD}================================================================================${NC}"
