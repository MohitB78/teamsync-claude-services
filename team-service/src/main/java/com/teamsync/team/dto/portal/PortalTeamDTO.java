package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Limited team view for external portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalTeamDTO {

    private String id;
    private String name;
    private String description;
    private String avatar;
    private int memberCount;
    private String myRole;
    private Set<String> myPermissions;
}
