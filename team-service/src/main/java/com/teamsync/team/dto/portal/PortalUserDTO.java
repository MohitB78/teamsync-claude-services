package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Portal user info after authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUserDTO {

    private String id;
    private String email;
    private String displayName;
    private List<PortalTeamAccessDTO> teams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortalTeamAccessDTO {
        private String teamId;
        private String teamName;
        private String roleName;
        private List<String> permissions;
    }
}
