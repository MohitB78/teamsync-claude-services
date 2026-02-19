package com.teamsync.search.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SearchController.
 * Tests search, suggest, and reindex endpoints.
 */
@DisplayName("Search Controller Tests")
class SearchControllerTest {

    private SearchController searchController;

    @BeforeEach
    void setUp() {
        searchController = new SearchController();
    }

    @Nested
    @DisplayName("Search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should return 200 when searching")
        void search_ValidQuery_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.search("test query", null, 0, 20);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Search Service - Query: test query");
        }

        @Test
        @DisplayName("Should include query in response message")
        void search_IncludesQueryInMessage() {
            // Given
            String query = "specific search term";

            // When
            ResponseEntity<ApiResponse<String>> response = searchController.search(query, null, 0, 20);

            // Then
            assertThat(response.getBody().getMessage()).contains(query);
        }

        @Test
        @DisplayName("Should handle type filter")
        void search_WithType_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.search("test", "DOCUMENT", 0, 20);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should handle pagination parameters")
        void search_WithPagination_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.search("test", null, 10, 50);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Should handle special characters in query")
        void search_SpecialChars_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.search("test@#$%", null, 0, 20);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Suggest Tests")
    class SuggestTests {

        @Test
        @DisplayName("Should return 200 when getting suggestions")
        void suggest_ValidQuery_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.suggest("test");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Search Service - Suggestions for: test");
        }

        @Test
        @DisplayName("Should include query in suggestions message")
        void suggest_IncludesQuery() {
            // Given
            String query = "document";

            // When
            ResponseEntity<ApiResponse<String>> response = searchController.suggest(query);

            // Then
            assertThat(response.getBody().getMessage()).contains(query);
        }

        @Test
        @DisplayName("Should handle short queries")
        void suggest_ShortQuery_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.suggest("ab");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Reindex Tests")
    class ReindexTests {

        @Test
        @DisplayName("Should return 200 when reindexing document")
        void reindex_ValidDocId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = searchController.reindex("doc-123");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Search Service - Reindex: doc-123");
        }

        @Test
        @DisplayName("Should include document ID in reindex message")
        void reindex_IncludesDocId() {
            // Given
            String docId = "custom-doc-456";

            // When
            ResponseEntity<ApiResponse<String>> response = searchController.reindex(docId);

            // Then
            assertThat(response.getBody().getMessage()).contains(docId);
        }

        @Test
        @DisplayName("Should handle UUID document ID")
        void reindex_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = searchController.reindex(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }
    }

    @Nested
    @DisplayName("Response Format Tests")
    class ResponseFormatTests {

        @Test
        @DisplayName("All endpoints should return ApiResponse wrapper")
        void allEndpoints_ReturnApiResponse() {
            assertThat(searchController.search("q", null, 0, 20).getBody()).isInstanceOf(ApiResponse.class);
            assertThat(searchController.suggest("q").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(searchController.reindex("doc").getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(searchController.search("q", null, 0, 20).getBody().isSuccess()).isTrue();
            assertThat(searchController.suggest("q").getBody().isSuccess()).isTrue();
            assertThat(searchController.reindex("doc").getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(searchController.search("q", null, 0, 20).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(searchController.suggest("q").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(searchController.reindex("doc").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(searchController.search("q", null, 0, 20).getBody()).isNotNull();
            assertThat(searchController.suggest("q").getBody()).isNotNull();
            assertThat(searchController.reindex("doc").getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Search Service")
        void allEndpoints_IdentifyAsSearchService() {
            assertThat(searchController.search("q", null, 0, 20).getBody().getMessage()).contains("Search Service");
            assertThat(searchController.suggest("q").getBody().getMessage()).contains("Search Service");
            assertThat(searchController.reindex("doc").getBody().getMessage()).contains("Search Service");
        }
    }
}
