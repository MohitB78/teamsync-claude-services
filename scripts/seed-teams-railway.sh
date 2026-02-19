#!/bin/bash
# =============================================================================
# Seed Sample Teams for TeamSync - Railway Deployment
# =============================================================================
# This script creates comprehensive sample teams to showcase all team
# functionality including:
# - Different team phases (Planning, Active, Review, Closing, Archived)
# - Program hierarchy (parent/child teams)
# - Various member roles (Owner, Admin, Manager, Member, Guest, External)
# - Different visibility settings (Public, Private, Restricted)
# - Enterprise features (project codes, client names)
#
# Prerequisites:
# - mongosh installed locally
# - Access to Railway MongoDB
#
# Usage: ./seed-teams-railway.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     TeamSync - Seed Sample Teams (Railway Deployment)          ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"

# =============================================================================
# Railway Configuration
# =============================================================================

# MongoDB Configuration
MONGO_PUBLIC_URL="mongodb://mongo:bImjOQVrydytOZWNladJECNlcsJPShJd@yamanote.proxy.rlwy.net:44087"
MONGO_DB="teamsync"

# =============================================================================
# Check Prerequisites
# =============================================================================
echo -e "\n${BLUE}Checking prerequisites...${NC}"

if ! command -v mongosh &> /dev/null; then
    echo -e "${RED}Error: mongosh is not installed.${NC}"
    echo -e "${YELLOW}Install with: brew install mongosh${NC}"
    exit 1
fi
echo -e "${GREEN}✓ mongosh installed${NC}"

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check if seed-teams.js exists
if [ ! -f "$SCRIPT_DIR/seed-teams.js" ]; then
    echo -e "${RED}Error: seed-teams.js not found in $SCRIPT_DIR${NC}"
    exit 1
fi
echo -e "${GREEN}✓ seed-teams.js found${NC}"

# =============================================================================
# Run Seed Script
# =============================================================================
echo -e "\n${BLUE}Running seed script...${NC}\n"

mongosh "$MONGO_PUBLIC_URL/$MONGO_DB?authSource=admin" --quiet "$SCRIPT_DIR/seed-teams.js" 2>&1 | grep -v "baseline-browser-mapping"

echo -e "\n${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    Seed Complete!                              ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"

echo -e "\n${CYAN}Teams Created:${NC}"
echo -e "  ${YELLOW}1. Enterprise Transformation Program${NC} (Parent Program)"
echo -e "     └── Phase: ACTIVE | Members: 6 | Code: ETP-2024-001"
echo -e "  ${YELLOW}2. Website Redesign Project${NC} (Child Team)"
echo -e "     └── Phase: ACTIVE | Members: 5 | Code: WEB-2024-042"
echo -e "     └── Has external designer (Maria Rodriguez)"
echo -e "  ${YELLOW}3. Mobile App Development${NC} (Child Team)"
echo -e "     └── Phase: PLANNING | Members: 4 | Code: MOB-2024-015"
echo -e "  ${YELLOW}4. ACME Corp Data Migration${NC} (Client Project)"
echo -e "     └── Phase: REVIEW | Members: 5 | Code: DM-2024-ACME"
echo -e "     └── Has external client (John Smith from ACME Corp)"
echo -e "  ${YELLOW}5. Q4 Marketing Campaign${NC} (Standalone)"
echo -e "     └── Phase: CLOSING | Members: 4 | Code: MKT-2024-Q4"
echo -e "  ${YELLOW}6. 2023 Annual Report${NC} (Archived)"
echo -e "     └── Phase: ARCHIVED | Members: 3 | Code: AR-2023"

echo -e "\n${CYAN}Features Demonstrated:${NC}"
echo -e "  ✓ All 5 team phases (Planning, Active, Review, Closing, Archived)"
echo -e "  ✓ Program hierarchy (parent/child teams)"
echo -e "  ✓ All 6 member roles (Owner, Admin, Manager, Member, Guest, External)"
echo -e "  ✓ Internal and External members"
echo -e "  ✓ Different visibility settings (Public, Private, Restricted)"
echo -e "  ✓ Enterprise features (project codes, client names)"
echo -e "  ✓ Different quota sources (Personal, Dedicated)"

echo -e "\n${GREEN}Access teams at: https://portal.teamsync.link/teams${NC}"
