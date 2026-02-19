#!/bin/bash
# =============================================================================
# Seed Media Documents for Admin User - Railway Deployment
# =============================================================================
# This script creates sample DOCX, XLSX, MP3, and MP4 files and uploads them
# to MinIO for the admin@accessarc.com user's personal drive on Railway.
#
# Prerequisites:
# - mongosh installed locally
# - mc (MinIO Client) installed locally
# - ffmpeg installed for audio/video generation (brew install ffmpeg)
#
# Usage: ./seed-media-documents-railway.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Seeding Media Documents for Admin User (Railway) ===${NC}"

# =============================================================================
# Railway Configuration
# =============================================================================

# MongoDB Configuration
MONGO_PUBLIC_URL="mongodb://mongo:bImjOQVrydytOZWNladJECNlcsJPShJd@yamanote.proxy.rlwy.net:44087"
MONGO_DB="teamsync"

# MinIO Configuration
MINIO_PUBLIC_ENDPOINT="https://bucket-production-5e8a.up.railway.app"
MINIO_ROOT_USER="HqeNWv0JDw1nOvnomdnkSXuVRhOizmg6"
MINIO_ROOT_PASSWORD="qaRZZPfwqPK8mAju6Y6Hr9KkcULFeQgYFSL1phlrTz4guCpH"
MINIO_ALIAS="railway-minio"
BUCKET_NAME="teamsync-documents"

# Application Configuration
TENANT_ID="default"
# Use the Zitadel admin user ID
ADMIN_USER_ID="353047894862887416"
DRIVE_ID="personal-$ADMIN_USER_ID"

echo -e "${GREEN}✓ Admin User ID: $ADMIN_USER_ID${NC}"
echo -e "${GREEN}✓ Personal Drive ID: $DRIVE_ID${NC}"

# =============================================================================
# Check Prerequisites
# =============================================================================
echo -e "\n${BLUE}Checking prerequisites...${NC}"

if ! command -v mongosh &> /dev/null; then
    echo -e "${RED}Error: mongosh is not installed.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ mongosh installed${NC}"

if ! command -v mc &> /dev/null; then
    echo -e "${RED}Error: mc (MinIO Client) is not installed.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ mc (MinIO Client) installed${NC}"

if ! command -v ffmpeg &> /dev/null; then
    echo -e "${YELLOW}Warning: ffmpeg is not installed. Will create placeholder audio/video files.${NC}"
    HAS_FFMPEG=false
else
    echo -e "${GREEN}✓ ffmpeg installed${NC}"
    HAS_FFMPEG=true
fi

# =============================================================================
# Create Temporary Directory
# =============================================================================
TEMP_DIR=$(mktemp -d)
echo -e "${GREEN}✓ Created temp directory: $TEMP_DIR${NC}"

cleanup() {
    rm -rf "$TEMP_DIR"
    echo -e "${GREEN}✓ Cleaned up temp directory${NC}"
}
trap cleanup EXIT

# =============================================================================
# Create Sample DOCX File
# =============================================================================
echo -e "\n${BLUE}Creating sample DOCX file...${NC}"

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
            <w:r><w:t>TeamSync Project Documentation</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>Version: 1.0.0</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>Date: December 30, 2024</w:t></w:r>
        </w:p>
        <w:p/>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
            <w:r><w:t>1. Introduction</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>TeamSync is a cloud-native document management and collaboration platform designed for enterprise teams. It provides secure file storage, real-time collaboration, and AI-powered document analysis.</w:t></w:r>
        </w:p>
        <w:p/>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
            <w:r><w:t>2. Key Features</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- Drive-Level RBAC with O(1) permission checks</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- Real-time document collaboration via WOPI</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- AI-powered metadata extraction and search</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>- Petabyte-scale storage with tiered lifecycle</w:t></w:r>
        </w:p>
        <w:p/>
        <w:p>
            <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
            <w:r><w:t>3. Architecture</w:t></w:r>
        </w:p>
        <w:p>
            <w:r><w:t>The platform consists of 16 microservices built on Spring Boot 4.0 and Java 25, with a React/Next.js 16 frontend.</w:t></w:r>
        </w:p>
    </w:body>
</w:document>
DOCEOF

cat > "$TEMP_DIR/docx_temp/docProps/core.xml" << 'COREEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
    <dc:title>TeamSync Project Documentation</dc:title>
    <dc:creator>Admin User</dc:creator>
    <dcterms:created>2024-12-30T10:00:00Z</dcterms:created>
</cp:coreProperties>
COREEOF

(cd "$TEMP_DIR/docx_temp" && zip -q -r "../TeamSync-Documentation.docx" .)
rm -rf "$TEMP_DIR/docx_temp"
echo -e "${GREEN}✓ Created TeamSync-Documentation.docx${NC}"

# =============================================================================
# Create Sample XLSX File
# =============================================================================
echo -e "\n${BLUE}Creating sample XLSX file...${NC}"

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
        <sheet name="Q4 Sales" sheetId="1" r:id="rId1"/>
    </sheets>
</workbook>
WBEOF

cat > "$TEMP_DIR/xlsx_temp/xl/worksheets/sheet1.xml" << 'SHEETEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <sheetData>
        <row r="1">
            <c r="A1" t="inlineStr"><is><t>Region</t></is></c>
            <c r="B1" t="inlineStr"><is><t>October</t></is></c>
            <c r="C1" t="inlineStr"><is><t>November</t></is></c>
            <c r="D1" t="inlineStr"><is><t>December</t></is></c>
            <c r="E1" t="inlineStr"><is><t>Q4 Total</t></is></c>
        </row>
        <row r="2">
            <c r="A2" t="inlineStr"><is><t>North America</t></is></c>
            <c r="B2"><v>125000</v></c>
            <c r="C2"><v>142000</v></c>
            <c r="D2"><v>168000</v></c>
            <c r="E2"><v>435000</v></c>
        </row>
        <row r="3">
            <c r="A3" t="inlineStr"><is><t>Europe</t></is></c>
            <c r="B3"><v>98000</v></c>
            <c r="C3"><v>115000</v></c>
            <c r="D3"><v>132000</v></c>
            <c r="E3"><v>345000</v></c>
        </row>
        <row r="4">
            <c r="A4" t="inlineStr"><is><t>Asia Pacific</t></is></c>
            <c r="B4"><v>156000</v></c>
            <c r="C4"><v>178000</v></c>
            <c r="D4"><v>195000</v></c>
            <c r="E4"><v>529000</v></c>
        </row>
        <row r="5">
            <c r="A5" t="inlineStr"><is><t>Latin America</t></is></c>
            <c r="B5"><v>45000</v></c>
            <c r="C5"><v>52000</v></c>
            <c r="D5"><v>61000</v></c>
            <c r="E5"><v>158000</v></c>
        </row>
        <row r="6">
            <c r="A6" t="inlineStr"><is><t>Total</t></is></c>
            <c r="B6"><v>424000</v></c>
            <c r="C6"><v>487000</v></c>
            <c r="D6"><v>556000</v></c>
            <c r="E6"><v>1467000</v></c>
        </row>
    </sheetData>
</worksheet>
SHEETEOF

cat > "$TEMP_DIR/xlsx_temp/docProps/core.xml" << 'COREEOF'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
    <dc:title>Q4 Sales Report 2024</dc:title>
    <dc:creator>Admin User</dc:creator>
    <dcterms:created>2024-12-30T10:00:00Z</dcterms:created>
</cp:coreProperties>
COREEOF

(cd "$TEMP_DIR/xlsx_temp" && zip -q -r "../Q4-Sales-Report.xlsx" .)
rm -rf "$TEMP_DIR/xlsx_temp"
echo -e "${GREEN}✓ Created Q4-Sales-Report.xlsx${NC}"

# =============================================================================
# Create Sample MP3 File
# =============================================================================
echo -e "\n${BLUE}Creating sample MP3 file...${NC}"

if [ "$HAS_FFMPEG" = true ]; then
    # Create a 10-second test tone audio file
    ffmpeg -f lavfi -i "sine=frequency=440:duration=10" -c:a libmp3lame -q:a 2 "$TEMP_DIR/Sample-Audio.mp3" -y 2>/dev/null
    echo -e "${GREEN}✓ Created Sample-Audio.mp3 (10 second 440Hz tone)${NC}"
else
    # Create a minimal valid MP3 file (silent)
    # This is a minimal MP3 header with silence - about 1KB
    echo -e "${YELLOW}Creating placeholder MP3 file...${NC}"
    base64 -d << 'MP3EOF' > "$TEMP_DIR/Sample-Audio.mp3"
//uQxAAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVTEFNRTMuMTAwVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
//uQxDUAAADSAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
MP3EOF
    echo -e "${GREEN}✓ Created placeholder Sample-Audio.mp3${NC}"
fi

# =============================================================================
# Create Sample MP4 File
# =============================================================================
echo -e "\n${BLUE}Creating sample MP4 file...${NC}"

if [ "$HAS_FFMPEG" = true ]; then
    # Create a 5-second test video with color bars and tone
    ffmpeg -f lavfi -i "testsrc=duration=5:size=640x480:rate=30" \
           -f lavfi -i "sine=frequency=440:duration=5" \
           -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 128k \
           "$TEMP_DIR/Sample-Video.mp4" -y 2>/dev/null
    echo -e "${GREEN}✓ Created Sample-Video.mp4 (5 second test pattern)${NC}"
else
    # Create a minimal valid MP4 file
    echo -e "${YELLOW}Creating placeholder MP4 file...${NC}"
    # Minimal valid MP4 with ftyp and moov atoms
    base64 -d << 'MP4EOF' > "$TEMP_DIR/Sample-Video.mp4"
AAAAHGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAAIZnJlZQAAABxtZGF0AAAAAAAAAAAAA
AAAAAAAAAAAAAAAAAAAAA==
MP4EOF
    echo -e "${GREEN}✓ Created placeholder Sample-Video.mp4${NC}"
fi

# =============================================================================
# Configure MinIO Client
# =============================================================================
echo -e "\n${BLUE}Configuring MinIO client...${NC}"

mc alias remove "$MINIO_ALIAS" 2>/dev/null || true
mc alias set "$MINIO_ALIAS" "$MINIO_PUBLIC_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" --api S3v4
echo -e "${GREEN}✓ MinIO client configured${NC}"

mc mb --ignore-existing "$MINIO_ALIAS/$BUCKET_NAME" 2>/dev/null || true
echo -e "${GREEN}✓ Bucket $BUCKET_NAME ready${NC}"

# =============================================================================
# Generate Document IDs and Upload to MinIO
# =============================================================================
echo -e "\n${BLUE}Uploading files to MinIO...${NC}"

DOC_DOCX_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC_XLSX_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC_MP3_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
DOC_MP4_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

STORAGE_KEY_DOCX="$TENANT_ID/$DRIVE_ID/$DOC_DOCX_ID/1/TeamSync-Documentation.docx"
STORAGE_KEY_XLSX="$TENANT_ID/$DRIVE_ID/$DOC_XLSX_ID/1/Q4-Sales-Report.xlsx"
STORAGE_KEY_MP3="$TENANT_ID/$DRIVE_ID/$DOC_MP3_ID/1/Sample-Audio.mp3"
STORAGE_KEY_MP4="$TENANT_ID/$DRIVE_ID/$DOC_MP4_ID/1/Sample-Video.mp4"

mc cp "$TEMP_DIR/TeamSync-Documentation.docx" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY_DOCX"
mc cp "$TEMP_DIR/Q4-Sales-Report.xlsx" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY_XLSX"
mc cp "$TEMP_DIR/Sample-Audio.mp3" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY_MP3"
mc cp "$TEMP_DIR/Sample-Video.mp4" "$MINIO_ALIAS/$BUCKET_NAME/$STORAGE_KEY_MP4"

echo -e "${GREEN}✓ Files uploaded to MinIO${NC}"

# Get file sizes
SIZE_DOCX=$(wc -c < "$TEMP_DIR/TeamSync-Documentation.docx" | tr -d ' ')
SIZE_XLSX=$(wc -c < "$TEMP_DIR/Q4-Sales-Report.xlsx" | tr -d ' ')
SIZE_MP3=$(wc -c < "$TEMP_DIR/Sample-Audio.mp3" | tr -d ' ')
SIZE_MP4=$(wc -c < "$TEMP_DIR/Sample-Video.mp4" | tr -d ' ')

echo -e "${GREEN}✓ File sizes: DOCX=$SIZE_DOCX, XLSX=$SIZE_XLSX, MP3=$SIZE_MP3, MP4=$SIZE_MP4${NC}"

# =============================================================================
# Create Documents in MongoDB
# =============================================================================
echo -e "\n${BLUE}Creating documents in MongoDB...${NC}"

mongosh "$MONGO_PUBLIC_URL/$MONGO_DB?authSource=admin" --quiet --eval "
const tenantId = '$TENANT_ID';
const driveId = '$DRIVE_ID';
const ownerId = '$ADMIN_USER_ID';
const now = new Date();

// DOCX Document
const docDocx = {
    _id: '$DOC_DOCX_ID',
    entityVersion: NumberLong(0),
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'TeamSync-Documentation.docx',
    description: 'TeamSync project documentation',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSize: NumberLong($SIZE_DOCX),
    storageKey: '$STORAGE_KEY_DOCX',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'docx',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '$DOC_DOCX_ID-v1',
    versionCount: 1,
    tags: ['documentation', 'project', 'word'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'TeamSync Project Documentation. Version 1.0.0. Introduction, Key Features, Architecture.',
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

// XLSX Document
const docXlsx = {
    _id: '$DOC_XLSX_ID',
    entityVersion: NumberLong(0),
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Q4-Sales-Report.xlsx',
    description: 'Q4 2024 Regional Sales Report',
    contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    fileSize: NumberLong($SIZE_XLSX),
    storageKey: '$STORAGE_KEY_XLSX',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'xlsx',
    checksum: null,
    documentTypeId: null,
    metadata: {},
    currentVersion: 1,
    currentVersionId: '$DOC_XLSX_ID-v1',
    versionCount: 1,
    tags: ['sales', 'report', 'Q4', 'excel'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: 'Q4 Sales Report 2024. North America, Europe, Asia Pacific, Latin America regional sales.',
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

// MP3 Document
const docMp3 = {
    _id: '$DOC_MP3_ID',
    entityVersion: NumberLong(0),
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Sample-Audio.mp3',
    description: 'Sample audio file for testing',
    contentType: 'audio/mpeg',
    fileSize: NumberLong($SIZE_MP3),
    storageKey: '$STORAGE_KEY_MP3',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'mp3',
    checksum: null,
    documentTypeId: null,
    metadata: {
        duration: '10 seconds',
        bitrate: '128 kbps'
    },
    currentVersion: 1,
    currentVersionId: '$DOC_MP3_ID-v1',
    versionCount: 1,
    tags: ['audio', 'sample', 'mp3'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: null,
    textExtractedAt: null,
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

// MP4 Document
const docMp4 = {
    _id: '$DOC_MP4_ID',
    entityVersion: NumberLong(0),
    tenantId: tenantId,
    driveId: driveId,
    folderId: null,
    name: 'Sample-Video.mp4',
    description: 'Sample video file for testing',
    contentType: 'video/mp4',
    fileSize: NumberLong($SIZE_MP4),
    storageKey: '$STORAGE_KEY_MP4',
    storageBucket: '$BUCKET_NAME',
    storageTier: 'HOT',
    extension: 'mp4',
    checksum: null,
    documentTypeId: null,
    metadata: {
        duration: '5 seconds',
        resolution: '640x480',
        codec: 'H.264'
    },
    currentVersion: 1,
    currentVersionId: '$DOC_MP4_ID-v1',
    versionCount: 1,
    tags: ['video', 'sample', 'mp4'],
    isStarred: false,
    isPinned: false,
    ownerId: ownerId,
    createdBy: ownerId,
    lastModifiedBy: ownerId,
    status: 'ACTIVE',
    isLocked: false,
    lockedBy: null,
    lockedAt: null,
    extractedText: null,
    textExtractedAt: null,
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

// Insert documents
db.documents.insertMany([docDocx, docXlsx, docMp3, docMp4]);

print('Created 4 media documents in MongoDB');
print('  DOCX ID: $DOC_DOCX_ID');
print('  XLSX ID: $DOC_XLSX_ID');
print('  MP3 ID: $DOC_MP3_ID');
print('  MP4 ID: $DOC_MP4_ID');
"

echo -e "${GREEN}✓ Documents created in MongoDB${NC}"

# =============================================================================
# Verification
# =============================================================================
echo -e "\n${BLUE}=== Verification ===${NC}"

echo -e "\n${YELLOW}New files in MinIO:${NC}"
mc ls "$MINIO_ALIAS/$BUCKET_NAME/$TENANT_ID/$DRIVE_ID/" 2>/dev/null | tail -10

echo -e "\n${YELLOW}Documents in MongoDB:${NC}"
mongosh "$MONGO_PUBLIC_URL/$MONGO_DB?authSource=admin" --quiet --eval "
db.documents.find({ driveId: '$DRIVE_ID' }, { name: 1, contentType: 1, fileSize: 1 }).forEach(doc => {
    print('  - ' + doc.name + ' (' + doc.contentType + ', ' + doc.fileSize + ' bytes)');
});
"

echo -e "\n${GREEN}=== Done! ===${NC}"
echo -e "Media documents have been created for admin@accessarc.com on Railway"
echo -e "\nDocuments created:"
echo -e "  1. TeamSync-Documentation.docx (Word)"
echo -e "  2. Q4-Sales-Report.xlsx (Excel)"
echo -e "  3. Sample-Audio.mp3 (Audio)"
echo -e "  4. Sample-Video.mp4 (Video)"
