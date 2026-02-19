package com.teamsync.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResult {
    private String id;
    private String name;
    private String email;
    private String avatarUrl;
    private String department;
}
