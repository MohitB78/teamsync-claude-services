package com.teamsync.team.dto.portal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Limited member view for external portal users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalMemberDTO {

    private String userId;
    private String displayName;
    private String avatar;
    private String roleName;
    private boolean isExternal;
}
