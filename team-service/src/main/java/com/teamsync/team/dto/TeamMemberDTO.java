package com.teamsync.team.dto;

import com.teamsync.team.model.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Data Transfer Object for TeamMember.
 * Used for member list responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberDTO {

    private String userId;
    private String email;
    private String displayName;
    private String avatar;

    private Team.MemberType memberType;
    private Team.MemberStatus status;

    private String roleId;
    private String roleName;
    private String roleColor;
    private Set<String> permissions;

    private Instant joinedAt;
    private String invitedBy;
    private String invitedByName;
    private Instant lastActiveAt;

    private Boolean isOwner;
}
