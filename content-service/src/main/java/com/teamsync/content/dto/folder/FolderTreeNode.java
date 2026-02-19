package com.teamsync.content.dto.folder;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FolderTreeNode {

    private String id;
    private String name;
    private String path;
    private String parentId;
    private Integer depth;
    private String color;
    private String icon;
    private Integer folderCount;
    private Integer documentCount;
    private Boolean hasChildren;
    private Boolean isExpanded;
    private List<FolderTreeNode> children;
}
