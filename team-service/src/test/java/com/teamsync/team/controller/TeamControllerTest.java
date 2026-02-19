package com.teamsync.team.controller;

import com.teamsync.common.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TeamController.
 * Tests team CRUD and member management endpoints.
 */
@DisplayName("Team Controller Tests")
class TeamControllerTest {

    private TeamController teamController;

    private static final String TEAM_ID = "team-123";

    @BeforeEach
    void setUp() {
        teamController = new TeamController();
    }

    @Nested
    @DisplayName("List Teams Tests")
    class ListTeamsTests {

        @Test
        @DisplayName("Should return 200 when listing teams")
        void listTeams_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = teamController.listTeams();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Team Service - List Teams endpoint");
        }

        @Test
        @DisplayName("Should have success=true")
        void listTeams_HasSuccessTrue() {
            // When
            ResponseEntity<ApiResponse<String>> response = teamController.listTeams();

            // Then
            assertThat(response.getBody().isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("Create Team Tests")
    class CreateTeamTests {

        @Test
        @DisplayName("Should return 200 when creating team")
        void createTeam_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = teamController.createTeam();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Team Service - Create Team endpoint");
        }
    }

    @Nested
    @DisplayName("Get Team Tests")
    class GetTeamTests {

        @Test
        @DisplayName("Should return 200 when getting team")
        void getTeam_ValidId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = teamController.getTeam(TEAM_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Team Service - Get Team: " + TEAM_ID);
        }

        @Test
        @DisplayName("Should include team ID in response")
        void getTeam_IncludesTeamId() {
            // Given
            String customId = "custom-team-456";

            // When
            ResponseEntity<ApiResponse<String>> response = teamController.getTeam(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID team ID")
        void getTeam_UuidId_ReturnsOk() {
            // Given
            String uuidId = "550e8400-e29b-41d4-a716-446655440000";

            // When
            ResponseEntity<ApiResponse<String>> response = teamController.getTeam(uuidId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains(uuidId);
        }
    }

    @Nested
    @DisplayName("Add Member Tests")
    class AddMemberTests {

        @Test
        @DisplayName("Should return 200 when adding member")
        void addMember_ValidTeamId_ReturnsOk() {
            // When
            ResponseEntity<ApiResponse<String>> response = teamController.addMember(TEAM_ID);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Team Service - Add Member to: " + TEAM_ID);
        }

        @Test
        @DisplayName("Should include team ID in add member response")
        void addMember_IncludesTeamId() {
            // Given
            String customId = "add-member-team-789";

            // When
            ResponseEntity<ApiResponse<String>> response = teamController.addMember(customId);

            // Then
            assertThat(response.getBody().getMessage()).contains(customId);
        }

        @Test
        @DisplayName("Should handle UUID team ID for add member")
        void addMember_UuidId_ReturnsOk() {
            // Given
            String uuidId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

            // When
            ResponseEntity<ApiResponse<String>> response = teamController.addMember(uuidId);

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
            assertThat(teamController.listTeams().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(teamController.createTeam().getBody()).isInstanceOf(ApiResponse.class);
            assertThat(teamController.getTeam("id").getBody()).isInstanceOf(ApiResponse.class);
            assertThat(teamController.addMember("id").getBody()).isInstanceOf(ApiResponse.class);
        }

        @Test
        @DisplayName("All successful responses should have success=true")
        void allEndpoints_HaveSuccessTrue() {
            assertThat(teamController.listTeams().getBody().isSuccess()).isTrue();
            assertThat(teamController.createTeam().getBody().isSuccess()).isTrue();
            assertThat(teamController.getTeam("id").getBody().isSuccess()).isTrue();
            assertThat(teamController.addMember("id").getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("All responses should have HTTP 200 OK status")
        void allEndpoints_Return200() {
            assertThat(teamController.listTeams().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(teamController.createTeam().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(teamController.getTeam("id").getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(teamController.addMember("id").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("All responses should have non-null body")
        void allEndpoints_HaveNonNullBody() {
            assertThat(teamController.listTeams().getBody()).isNotNull();
            assertThat(teamController.createTeam().getBody()).isNotNull();
            assertThat(teamController.getTeam("id").getBody()).isNotNull();
            assertThat(teamController.addMember("id").getBody()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Service Identification Tests")
    class ServiceIdentificationTests {

        @Test
        @DisplayName("All responses should identify as Team Service")
        void allEndpoints_IdentifyAsTeamService() {
            assertThat(teamController.listTeams().getBody().getMessage()).contains("Team Service");
            assertThat(teamController.createTeam().getBody().getMessage()).contains("Team Service");
            assertThat(teamController.getTeam("id").getBody().getMessage()).contains("Team Service");
            assertThat(teamController.addMember("id").getBody().getMessage()).contains("Team Service");
        }
    }

    @Nested
    @DisplayName("Path Variable Tests")
    class PathVariableTests {

        @Test
        @DisplayName("Get team should work with various team IDs")
        void getTeam_VariousIds_AllWork() {
            String[] ids = {"simple", "with-dash", "with_underscore", "UPPERCASE", "MixedCase123"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = teamController.getTeam(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }

        @Test
        @DisplayName("Add member should work with various team IDs")
        void addMember_VariousIds_AllWork() {
            String[] ids = {"team1", "team-2", "team_3", "TEAM4"};

            for (String id : ids) {
                ResponseEntity<ApiResponse<String>> response = teamController.addMember(id);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody().getMessage()).contains(id);
            }
        }
    }
}
