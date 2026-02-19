package com.teamsync.sharing.dto;

import com.teamsync.sharing.model.Share.SharePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessCheckResponse {

    private Boolean hasAccess;
    private Set<SharePermission> permissions;
    private String accessSource;  // OWNER, SHARE, PUBLIC_LINK
    private String shareId;
}
