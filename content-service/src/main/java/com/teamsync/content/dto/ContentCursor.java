package com.teamsync.content.dto;

import com.teamsync.common.util.CursorUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cursor for paginating across two collections (folders and documents).
 *
 * <h2>Cursor Strategy</h2>
 * <p>Since we paginate folders before documents (type ordering), the cursor
 * tracks which phase we're in:</p>
 * <ul>
 *   <li>FOLDER phase: Still fetching folders</li>
 *   <li>DOCUMENT phase: All folders fetched, now fetching documents</li>
 * </ul>
 *
 * <h2>Cursor Format</h2>
 * <p>Encoded as Base64: "PHASE|name|id"</p>
 * <ul>
 *   <li>PHASE: "F" for FOLDER, "D" for DOCUMENT</li>
 *   <li>name: Last item's name (for name-based ordering)</li>
 *   <li>id: Last item's ID (for tie-breaking same names)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentCursor {

    public enum Phase {
        FOLDER,   // Currently paginating through folders
        DOCUMENT  // All folders done, paginating through documents
    }

    private Phase phase;
    private String name;  // Last item's name (for sorting)
    private String id;    // Last item's ID (for stable cursor when names match)

    /**
     * Encodes the cursor to a URL-safe Base64 string.
     */
    public String encode() {
        String phaseCode = phase == Phase.FOLDER ? "F" : "D";
        String raw = phaseCode + "|" + (name != null ? name : "") + "|" + (id != null ? id : "");
        return CursorUtils.encode(raw);
    }

    /**
     * Decodes a cursor string back to a ContentCursor object.
     *
     * @param encoded the Base64-encoded cursor string
     * @return ContentCursor or null if invalid
     */
    public static ContentCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        String decoded = CursorUtils.decode(encoded);
        if (decoded == null) {
            return null;
        }

        String[] parts = decoded.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }

        Phase phase = "F".equals(parts[0]) ? Phase.FOLDER : Phase.DOCUMENT;
        String name = parts[1].isEmpty() ? null : parts[1];
        String id = parts[2].isEmpty() ? null : parts[2];

        return ContentCursor.builder()
                .phase(phase)
                .name(name)
                .id(id)
                .build();
    }

    /**
     * Creates a cursor pointing to the start of folders.
     */
    public static ContentCursor start() {
        return ContentCursor.builder()
                .phase(Phase.FOLDER)
                .build();
    }

    /**
     * Creates a cursor for the next page after a folder.
     */
    public static ContentCursor afterFolder(String name, String id) {
        return ContentCursor.builder()
                .phase(Phase.FOLDER)
                .name(name)
                .id(id)
                .build();
    }

    /**
     * Creates a cursor for starting documents (all folders exhausted).
     */
    public static ContentCursor startDocuments() {
        return ContentCursor.builder()
                .phase(Phase.DOCUMENT)
                .build();
    }

    /**
     * Creates a cursor for the next page after a document.
     */
    public static ContentCursor afterDocument(String name, String id) {
        return ContentCursor.builder()
                .phase(Phase.DOCUMENT)
                .name(name)
                .id(id)
                .build();
    }
}
