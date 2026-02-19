#!/bin/bash
# Fix sample documents in Railway MongoDB to have entityVersion field
# This is needed because Spring Data MongoDB uses @Version to determine if a document is new
#
# The issue: When documents are manually created in MongoDB without entityVersion,
# Spring Data MongoDB's @Version annotation causes save() to think the document
# is new (because version is null), resulting in an INSERT instead of UPDATE.
# This causes duplicate key errors.
#
# Usage: ./fix-sample-documents-railway.sh

set -e

echo "=== Fix Sample Documents in Railway MongoDB ==="

# MongoDB URI for Railway (use the public proxy URL for external access)
MONGODB_URI="mongodb://mongo:bImjOQVrydytOZWNladJECNlcsJPShJd@yamanote.proxy.rlwy.net:44087/teamsync?authSource=admin"

# Fix documents by adding entityVersion field (set to 0 for initial version)
echo "Fixing entityVersion for sample documents..."

mongosh "$MONGODB_URI" --quiet --eval '
// Update all documents that have null/missing entityVersion
const docResult = db.documents.updateMany(
  { entityVersion: { $exists: false } },
  { $set: { entityVersion: NumberLong(0) } }
);
print("Documents fixed: " + docResult.modifiedCount);

// Also check drives collection
const driveResult = db.drives.updateMany(
  { entityVersion: { $exists: false } },
  { $set: { entityVersion: NumberLong(0) } }
);
print("Drives fixed: " + driveResult.modifiedCount);

print("\nDocuments in teamsync database:");
db.documents.find({}, { _id: 1, name: 1, entityVersion: 1 }).forEach(doc => {
  print("  " + doc._id + " - " + (doc.name || "unnamed") + " - entityVersion: " + doc.entityVersion);
});

print("\nDrives in teamsync database:");
db.drives.find({}, { _id: 1, name: 1, entityVersion: 1 }).forEach(doc => {
  print("  " + doc._id + " - " + (doc.name || "unnamed") + " - entityVersion: " + doc.entityVersion);
});
'

echo ""
echo "=== Fix Complete ==="
