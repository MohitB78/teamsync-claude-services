#!/bin/bash
# =============================================================================
# Seed Sample Documents for Admin User
# =============================================================================
# This script creates sample documents in MongoDB and uploads files to MinIO
# for the admin@accessarc.com user's personal drive.
#
# Prerequisites:
# - Docker running with teamsync-mongodb and teamsync-minio containers
# - accessarc-mongodb container (shared infrastructure)
#
# Usage: ./seed-sample-documents.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Seeding Sample Documents for Admin User ===${NC}"

# Configuration
MONGO_CONTAINER="accessarc-mongodb"
MINIO_CONTAINER="teamsync-minio"
MINIO_ALIAS="myminio"
BUCKET_NAME="teamsync-documents"
TENANT_ID="default"
ADMIN_EMAIL="admin@accessarc.com"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

# Check if containers are running
if ! docker ps | grep -q "$MONGO_CONTAINER"; then
    echo -e "${RED}Error: $MONGO_CONTAINER container is not running.${NC}"
    echo -e "${YELLOW}Please start AccessArc backend first: cd ../access-arc-backend/docker && docker-compose up -d${NC}"
    exit 1
fi

if ! docker ps | grep -q "$MINIO_CONTAINER"; then
    echo -e "${RED}Error: $MINIO_CONTAINER container is not running.${NC}"
    echo -e "${YELLOW}Please start TeamSync backend first: cd .. && docker-compose up -d minio${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker containers are running${NC}"

# Get admin user ID from Keycloak or use a default
# First, check if we can find the user in MongoDB
ADMIN_USER_ID=$(docker exec "$MONGO_CONTAINER" mongosh --quiet accessarc --eval "
const user = db.users.findOne({email: '$ADMIN_EMAIL'});
if (user) {
    print(user._id.toString());
} else {
    print('');
}
" 2>/dev/null | tail -1)

if [ -z "$ADMIN_USER_ID" ]; then
    echo -e "${YELLOW}Admin user not found in MongoDB. Checking Keycloak...${NC}"
    # Use Keycloak user ID if available
    ADMIN_USER_ID=$(docker exec accessarc-keycloak /opt/keycloak/bin/kcadm.sh get users -r accessarc-realm --fields id,email 2>/dev/null | grep -A1 "admin@accessarc.com" | grep '"id"' | sed 's/.*: "\([^"]*\)".*/\1/' || echo "")

    if [ -z "$ADMIN_USER_ID" ]; then
        echo -e "${YELLOW}Using default admin user ID${NC}"
        ADMIN_USER_ID="2fd9b5d2-262d-4748-b24f-f8fe544eabad"  # Default Keycloak admin UUID
    fi
fi

echo -e "${GREEN}✓ Admin User ID: $ADMIN_USER_ID${NC}"

# Personal drive ID follows the pattern: personal-{userId}
DRIVE_ID="personal-$ADMIN_USER_ID"
echo -e "${GREEN}✓ Personal Drive ID: $DRIVE_ID${NC}"

# Create temporary directory for sample files
TEMP_DIR=$(mktemp -d)
echo -e "${GREEN}✓ Created temp directory: $TEMP_DIR${NC}"

# Cleanup function
cleanup() {
    rm -rf "$TEMP_DIR"
    echo -e "${GREEN}✓ Cleaned up temp directory${NC}"
}
trap cleanup EXIT

# =============================================================================
# Create Sample Files
# =============================================================================
echo -e "\n${GREEN}Creating sample files...${NC}"

# 1. Sample PDF
cat > "$TEMP_DIR/Company-Report-2024.pdf" << 'PDFEOF'
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 200 >>
stream
BT
/F1 24 Tf
50 700 Td
(Company Report 2024) Tj
0 -30 Td
/F1 12 Tf
(This is a sample company report document.) Tj
0 -20 Td
(Generated for TeamSync testing purposes.) Tj
0 -20 Td
(Author: Admin User) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000266 00000 n
0000000518 00000 n
trailer
<< /Size 6 /Root 1 0 R >>
startxref
595
%%EOF
PDFEOF

# 2. Sample PDF - Project Proposal
cat > "$TEMP_DIR/Project-Proposal.pdf" << 'PDF2EOF'
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 250 >>
stream
BT
/F1 24 Tf
50 700 Td
(Project Proposal) Tj
0 -30 Td
/F1 12 Tf
(Project: TeamSync Document Platform) Tj
0 -20 Td
(Objective: Build a cloud-native document management system) Tj
0 -20 Td
(Timeline: Q1 2025) Tj
0 -20 Td
(Budget: $250,000) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000266 00000 n
0000000568 00000 n
trailer
<< /Size 6 /Root 1 0 R >>
startxref
645
%%EOF
PDF2EOF

# 3. Sample DOCX (minimal valid structure)
mkdir -p "$TEMP_DIR/docx_temp/word" "$TEMP_DIR/docx_temp/_rels" "$TEMP_DIR/docx_temp/docProps"

cat > "$TEMP_DIR/docx_temp/[Content_Types].xml" << 'CTEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>
CTEOF

cat > "$TEMP_DIR/docx_temp/_rels/.rels" << 'RELSEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>
RELSEOF

cat > "$TEMP_DIR/docx_temp/word/document.xml" << 'DOCEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
            <w:r><w:t>Meeting Notes - Q4 Planning</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>Date: December 10, 2024</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>Attendees: John Smith, Jane Doe, Bob Wilson</w:t></w:r>
        </w:p>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
            <w:r><w:t>Agenda Items</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>1. Review Q3 results</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>2. Set Q4 objectives</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>3. Budget allocation</w:t></w:r>
        </w:p>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
            <w:r><w:t>Action Items</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- John: Prepare financial report by Dec 15</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- Jane: Schedule team retrospective</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- Bob: Review vendor contracts</w:t></w:r>
        </w:p>
    </w:body>
</w:document>
DOCEOF

cat > "$TEMP_DIR/docx_temp/docProps/core.xml" << 'COREEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
    <dc:title>Meeting Notes - Q4 Planning</dc:title>
    <dc:creator>Admin User</dc:creator>
    <dcterms:created>2024-12-10T10:00:00Z</dcterms:created>
</cp:coreProperties>
COREEOF

(cd "$TEMP_DIR/docx_temp" && zip -q -r "../Meeting-Notes-Q4.docx" .)
rm -rf "$TEMP_DIR/docx_temp"

# 4. Sample XLSX (minimal valid structure)
mkdir -p "$TEMP_DIR/xlsx_temp/xl/worksheets" "$TEMP_DIR/xlsx_temp/_rels" "$TEMP_DIR/xlsx_temp/xl/_rels" "$TEMP_DIR/xlsx_temp/docProps"

cat > "$TEMP_DIR/xlsx_temp/[Content_Types].xml" << 'CTEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
    <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
</Types>
CTEOF

cat > "$TEMP_DIR/xlsx_temp/_rels/.rels" << 'RELSEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
</Relationships>
RELSEOF

cat > "$TEMP_DIR/xlsx_temp/xl/_rels/workbook.xml.rels" << 'WBRELEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>
WBRELEOF

cat > "$TEMP_DIR/xlsx_temp/xl/workbook.xml" << 'WBEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <sheets>
        <sheet name="Budget" sheetId="1" r:id="rId1"/>
    </sheets>
</workbook>
WBEOF

cat > "$TEMP_DIR/xlsx_temp/xl/worksheets/sheet1.xml" << 'SHEETEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheetData>
        <row r="1">
            <c r="A1" t="inlineStr"><is><t>Department</t></is></c>
            <c r="B1" t="inlineStr"><is><t>Q1 Budget</t></is></c>
            <c r="C1" t="inlineStr"><is><t>Q2 Budget</t></is></c>
            <c r="D1" t="inlineStr"><is><t>Q3 Budget</t></is></c>
            <c r="E1" t="inlineStr"><is><t>Q4 Budget</t></is></c>
            <c r="F1" t="inlineStr"><is><t>Total</t></is></c>
        </row>
        <row r="2">
            <c r="A2" t="inlineStr"><is><t>Engineering</t></is></c>
            <c r="B2"><v>150000</v></c>
            <c r="C2"><v>175000</v></c>
            <c r="D2"><v>180000</v></c>
            <c r="E2"><v>200000</v></c>
            <c r="F2"><v>705000</v></c>
        </row>
        <row r="3">
            <c r="A3" t="inlineStr"><is><t>Marketing</t></is></c>
            <c r="B3"><v>80000</v></c>
            <c r="C3"><v>95000</v></c>
            <c r="D3"><v>110000</v></c>
            <c r="E3"><v>125000</v></c>
            <c r="F3"><v>410000</v></c>
        </row>
        <row r="4">
            <c r="A4" t="inlineStr"><is><t>Sales</t></is></c>
            <c r="B4"><v>120000</v></c>
            <c r="C4"><v>130000</v></c>
            <c r="D4"><v>145000</v></c>
            <c r="E4"><v>160000</v></c>
            <c r="F4"><v>555000</v></c>
        </row>
        <row r="5">
            <c r="A5" t="inlineStr"><is><t>Operations</t></is></c>
            <c r="B5"><v>60000</v></c>
            <c r="C5"><v>65000</v></c>
            <c r="D5"><v>70000</v></c>
            <c r="E5"><v>75000</v></c>
            <c r="F5"><v>270000</v></c>
        </row>
        <row r="6">
            <c r="A6" t="inlineStr"><is><t>Total</t></is></c>
            <c r="B6"><v>410000</v></c>
            <c r="C6"><v>465000</v></c>
            <c r="D6"><v>505000</v></c>
            <c r="E6"><v>560000</v></c>
            <c r="F6"><v>1940000</v></c>
        </row>
    </sheetData>
</worksheet>
SHEETEOF

cat > "$TEMP_DIR/xlsx_temp/docProps/core.xml" << 'COREEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
    <dc:title>2024 Annual Budget</dc:title>
    <dc:creator>Admin User</dc:creator>
    <dcterms:created>2024-12-10T10:00:00Z</dcterms:created>
</cp:coreProperties>
COREEOF

(cd "$TEMP_DIR/xlsx_temp" && zip -q -r "../Annual-Budget-2024.xlsx" .)
rm -rf "$TEMP_DIR/xlsx_temp"

# 5. Sample TXT file
cat > "$TEMP_DIR/README.txt" << 'TXTEOF'
TeamSync Document Platform - Getting Started Guide
===================================================

Welcome to TeamSync!

This is your personal drive where you can store and manage your documents.

Quick Start:
1. Upload files by clicking the "Upload" button
2. Create folders to organize your content
3. Share documents with colleagues using the "Share" option
4. Search across all your documents using the search bar

Features:
- Real-time collaboration on Office documents
- Version history for all files
- AI-powered document analysis
- Full-text search across all content
- Mobile access via iOS and Android apps

For support, contact: support@teamsync.example.com

Last updated: December 10, 2024
TXTEOF

echo -e "${GREEN}✓ Created sample files${NC}"

# =============================================================================
# Upload to MinIO
# =============================================================================
echo -e "\n${GREEN}Uploading files to MinIO...${NC}"

# Install mc (MinIO Client) if not already in container
docker exec "$MINIO_CONTAINER" mc alias set "$MINIO_ALIAS" http://localhost:9000 minioadmin minioadmin 2>/dev/null || true

# Create bucket if it doesn't exist
docker exec "$MINIO_CONTAINER" mc mb --ignore-existing "$MINIO_ALIAS/$BUCKET_NAME" 2>/dev/null || true
echo -e "${GREEN}✓ Bucket $BUCKET_NAME ready${NC}"

# Generate UUIDs for documents
DOC1_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC2_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC3_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC4_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC5_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Storage keys follow the pattern: tenantId/driveId/documentId/version/filename
STORAGE_KEY1="$TENANT_ID/$DRIVE_ID/$DOC1_ID/1/Company-Report-2024.pdf"
STORAGE_KEY2="$TENANT_ID/$DRIVE_ID/$DOC2_ID/1/Project-Proposal.pdf"
STORAGE_KEY3="$TENANT_ID/$DRIVE_ID/$DOC3_ID/1/Meeting-Notes-Q4.docx"
STORAGE_KEY4="$TENANT_ID/$DRIVE_ID/$DOC4_ID/1/Annual-Budget-2024.xlsx"
STORAGE_KEY5="$TENANT_ID/$DRIVE_ID/$DOC5_ID/1/README.txt"

# Copy files to container and upload
docker cp "$TEMP_DIR/Company-Report-2024.pdf" "$MINIO_CONTAINER:/tmp/"
docker cp "$TEMP_DIR/Project-Proposal.pdf" "$MINIO_CONTAINER:/tmp/"
docker cp "$TEMP_DIR/Meeting-Notes-Q4.docx" "$MINIO_CONTAINER:/tmp/"
docker cp "$TEMP_DIR/Annual-Budget-2024.xlsx" "$MINIO_CONTAINER:/tmp/"
docker cp "$TEMP_DIR/README.txt" "$MINIO_CONTAINER:/tmp/"

docker exec "$MINIO_CONTAINER" mc cp "/tmp/Company-Report-2024.pdf" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY1"
docker exec "$MINIO_CONTAINER" mc cp "/tmp/Project-Proposal.pdf" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY2"
docker exec "$MINIO_CONTAINER" mc cp "/tmp/Meeting-Notes-Q4.docx" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY3"
docker exec "$MINIO_CONTAINER" mc cp "/tmp/Annual-Budget-2024.xlsx" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY4"
docker exec "$MINIO_CONTAINER" mc cp "/tmp/README.txt" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY5"

echo -e "${GREEN}✓ Files uploaded to MinIO${NC}"

# Get file sizes
SIZE1=$(wc -c < "$TEMP_DIR/Company-Report-2024.pdf" | tr -d ' ')
SIZE2=$(wc -c < "$TEMP_DIR/Project-Proposal.pdf" | tr -d ' ')
SIZE3=$(wc -c < "$TEMP_DIR/Meeting-Notes-Q4.docx" | tr -d ' ')
SIZE4=$(wc -c < "$TEMP_DIR/Annual-Budget-2024.xlsx" | tr -d ' ')
SIZE5=$(wc -c < "$TEMP_DIR/README.txt" | tr -d ' ')

# =============================================================================
# Create Documents in MongoDB
# =============================================================================
echo -e "\n${GREEN}Creating documents in MongoDB...${NC}"

# First, ensure the drive exists in the permission manager database
docker exec "$MONGO_CONTAINER" mongosh --quiet teamsync_permissions --eval "
const driveId = '$DRIVE_ID';
const tenantId = '$TENANT_ID';
const ownerId = '$ADMIN_USER_ID';
const now = new Date();

// Check if drive exists
const existingDrive = db.drives.findOne({ _id: driveId });

if (!existingDrive) {
    db.drives.insertOne({
        _id: driveId,
        tenantId: tenantId,
        name: 'My Drive',
        description: 'Personal drive for admin user',
        type: 'PERSONAL',
        ownerId: ownerId,
        departmentId: null,
        quotaBytes: NumberLong(10737418240),  // 10GB
        usedBytes: NumberLong(0),
        defaultRoleId: null,
        status: 'ACTIVE',
        settings: {
            allowPublicLinks: true,
            allowExternalSharing: false,
            maxVersions: 100,
            trashRetentionDays: 30,
            requireApprovalForDelete: false
        },
        createdAt: now,
        updatedAt: now,
        createdBy: ownerId
    });
    print('✓ Created personal drive: ' + driveId);
} else {
    print('✓ Personal drive already exists: ' + driveId);
}
"

# Create documents in teamsync database
docker exec "$MONGO_CONTAINER" mongosh --quiet teamsync --eval "
const tenantId = '$TENANT_ID';
const driveId = '$DRIVE_ID';
const ownerId = '$ADMIN_USER_ID';
const now = new Date();

// Document 1: Company Report PDF
const doc1 = {
    _id: '$DOC1_ID',
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Company-Report-2024.pdf',
    description: 'Annual company report for 2024',
    contentType: 'application/pdf',
    fileSize: NumberLong($SIZE1),
    storageKey: '$STORAGE_KEY1',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'pdf',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '${DOC1_ID}-v1',
    versionCount: 1,
    tags: ['report', 'annual', '2024'],
    isStarred: true,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'Company Report 2024 This is a sample company report document. Generated for TeamSync testing purposes. Author: Admin User',
    textExtractedAt: now,
    thumbnailKey: null,
    hasThumbnail: false,
    aiMetadata: null,
    aiProcessedAt: null,
    virusScanStatus: 'CLEAN',
    virusScanAt: now,
    createdAt: now,
    updatedAt: now,
    accessedAt: now
};

// Document 2: Project Proposal PDF
const doc2 = {
    _id: '$DOC2_ID',
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Project-Proposal.pdf',
    description: 'TeamSync Document Platform project proposal',
    contentType: 'application/pdf',
    fileSize: NumberLong($SIZE2),
    storageKey: '$STORAGE_KEY2',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'pdf',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '${DOC2_ID}-v1',
    versionCount: 1,
    tags: ['proposal', 'project', 'teamsync'],
    isStarred: false,
    isPinned: true,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'Project Proposal Project: TeamSync Document Platform Objective: Build a cloud-native document management system Timeline: Q1 2025 Budget: 250000',
    textExtractedAt: now,
    thumbnailKey: null,
    hasThumbnail: false,
    aiMetadata: null,
    aiProcessedAt: null,
    virusScanStatus: 'CLEAN',
    virusScanAt: now,
    createdAt: now,
    updatedAt: now,
    accessedAt: now
};

// Document 3: Meeting Notes DOCX
const doc3 = {
    _id: '$DOC3_ID',
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Meeting-Notes-Q4.docx',
    description: 'Q4 Planning meeting notes',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSize: NumberLong($SIZE3),
    storageKey: '$STORAGE_KEY3',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'docx',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '${DOC3_ID}-v1',
    versionCount: 1,
    tags: ['meeting', 'notes', 'Q4', 'planning'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'Meeting Notes - Q4 Planning Date: December 10, 2024 Attendees: John Smith, Jane Doe, Bob Wilson Agenda Items Review Q3 results Set Q4 objectives Budget allocation Action Items John: Prepare financial report by Dec 15 Jane: Schedule team retrospective Bob: Review vendor contracts',
    textExtractedAt: now,
    thumbnailKey: null,
    hasThumbnail: false,
    aiMetadata: null,
    aiProcessedAt: null,
    virusScanStatus: 'CLEAN',
    virusScanAt: now,
    createdAt: now,
    updatedAt: now,
    accessedAt: now
};

// Document 4: Budget XLSX
const doc4 = {
    _id: '$DOC4_ID',
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Annual-Budget-2024.xlsx',
    description: '2024 departmental budget spreadsheet',
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    fileSize: NumberLong($SIZE4),
    storageKey: '$STORAGE_KEY4',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'xlsx',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '${DOC4_ID}-v1',
    versionCount: 1,
    tags: ['budget', 'finance', '2024', 'annual'],
    isStarred: true,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'Department Q1 Budget Q2 Budget Q3 Budget Q4 Budget Total Engineering 150000 175000 180000 200000 705000 Marketing 80000 95000 110000 125000 410000 Sales 120000 130000 145000 160000 555000 Operations 60000 65000 70000 75000 270000 Total 410000 465000 505000 560000 1940000',
    textExtractedAt: now,
    thumbnailKey: null,
    hasThumbnail: false,
    aiMetadata: null,
    aiProcessedAt: null,
    virusScanStatus: 'CLEAN',
    virusScanAt: now,
    createdAt: now,
    updatedAt: now,
    accessedAt: now
};

// Document 5: README TXT
const doc5 = {
    _id: '$DOC5_ID',
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'README.txt',
    description: 'Getting started guide for TeamSync',
    contentType: 'text/plain',
    fileSize: NumberLong($SIZE5),
    storageKey: '$STORAGE_KEY5',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'txt',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '${DOC5_ID}-v1',
    versionCount: 1,
    tags: ['readme', 'guide', 'documentation'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'TeamSync Document Platform - Getting Started Guide Welcome to TeamSync! This is your personal drive where you can store and manage your documents. Quick Start: Upload files by clicking the Upload button Create folders to organize your content Share documents with colleagues using the Share option Search across all your documents using the search bar Features: Real-time collaboration on Office documents Version history for all files AI-powered document analysis Full-text search across all content Mobile access via iOS and Android apps',
    textExtractedAt: now,
    thumbnailKey: null,
    hasThumbnail: false,
    aiMetadata: null,
    aiProcessedAt: null,
    virusScanStatus: 'CLEAN',
    virusScanAt: now,
    createdAt: now,
    updatedAt: now,
    accessedAt: now
};

// Delete existing sample documents (if re-running)
db.documents.deleteMany({
    driveId: driveId,
    tenantId: tenantId,
    name: { \$in: ['Company-Report-2024.pdf', 'Project-Proposal.pdf', 'Meeting-Notes-Q4.docx', 'Annual-Budget-2024.xlsx', 'README.txt'] }
});

// Insert all documents
db.documents.insertMany([doc1, doc2, doc3, doc4, doc5]);

print('✓ Created 5 documents in MongoDB');

// Update drive usage
const totalSize = $SIZE1 + $SIZE2 + $SIZE3 + $SIZE4 + $SIZE5;
print('Total storage used: ' + totalSize + ' bytes');
"

# Update drive usage in permission manager
docker exec "$MONGO_CONTAINER" mongosh --quiet teamsync_permissions --eval "
const driveId = '$DRIVE_ID';
const totalSize = $SIZE1 + $SIZE2 + $SIZE3 + $SIZE4 + $SIZE5;

db.drives.updateOne(
    { _id: driveId },
    {
        \$set: {
            usedBytes: NumberLong(totalSize),
            updatedAt: new Date()
        }
    }
);
print('✓ Updated drive storage usage: ' + totalSize + ' bytes');
"

# =============================================================================
# Verify
# =============================================================================
echo -e "\n${GREEN}=== Verification ===${NC}"

# Check MinIO
echo -e "\n${YELLOW}Files in MinIO:${NC}"
docker exec "$MINIO_CONTAINER" mc ls --recursive "$MINIO_ALIAS/$BUCKET_NAME/$TENANT_ID/$DRIVE_ID/" 2>/dev/null || echo "No files found"

# Check MongoDB
echo -e "\n${YELLOW}Documents in MongoDB:${NC}"
docker exec "$MONGO_CONTAINER" mongosh --quiet teamsync --eval "
db.documents.find({ driveId: '$DRIVE_ID' }, { name: 1, fileSize: 1, contentType: 1 }).forEach(doc => {
    print('  - ' + doc.name + ' (' + doc.fileSize + ' bytes, ' + doc.contentType + ')');
});
"

echo -e "\n${GREEN}=== Done! ===${NC}"
echo -e "Sample documents have been created for admin@accessarc.com"
echo -e "Personal Drive ID: $DRIVE_ID"
echo -e "\nDocuments created:"
echo -e "  1. Company-Report-2024.pdf (PDF)"
echo -e "  2. Project-Proposal.pdf (PDF)"
echo -e "  3. Meeting-Notes-Q4.docx (Word)"
echo -e "  4. Annual-Budget-2024.xlsx (Excel)"
echo -e "  5. README.txt (Text)"
