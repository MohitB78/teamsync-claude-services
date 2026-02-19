package com.teamsync.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSearchResult {
    private String id;
    private String name;
    private int memberCount;
    private String description;
}
