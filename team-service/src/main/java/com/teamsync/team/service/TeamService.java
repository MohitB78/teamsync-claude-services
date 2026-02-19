package com.teamsync.team.service;

import com.teamsync.common.context.TenantContext;
import com.teamsync.common.dto.CursorPage;
import com.teamsync.common.exception.AccessDeniedException;
import com.teamsync.common.exception.ResourceNotFoundException;
import com.teamsync.team.dto.*;
import com.teamsync.team.model.Team;
import com.teamsync.team.model.Team.*;
import com.teamsync.team.model.TeamPermission;
import com.teamsync.team.model.TeamRole;
import com.teamsync.team.repository.TeamRepository;
import com.teamsync.team.repository.TeamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core Team Service providing team management operations.
 *
 * Responsibilities:
 * - Team CRUD operations
 * - Team settings management
 * - Quota configuration
 * - Publishing team lifecycle events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamRoleRepository roleRepository;
    private final TeamRoleService roleService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Kafka topics
    private static final String TOPIC_TEAMS_CREATED = "teamsync.teams.created";
    private static final String TOPIC_TEAMS_UPDATED = "teamsync.teams.updated";
    private static final String TOPIC_TEAMS_DELETED = "teamsync.teams.deleted";

    // ============== TEAM CRUD ==============

    /**
     * Create a new team.
     *
     * @param request The team creation request
     * @return The created team DTO
     */
    @Transactional
    public TeamDTO createTeam(CreateTeamRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.info("Creating team: name={}, owner={}", request.getName(), userId);

        // Check for duplicate name
        if (teamRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
            throw new IllegalArgumentException("Team name already exists: " + request.getName());
        }

        // Ensure system roles exist for tenant
        roleService.ensureSystemRoles(tenantId);

        // Get owner role (system roles have ID = constant like "OWNER")
        TeamRole ownerRole = roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_OWNER)
                .orElseThrow(() -> new IllegalStateException("Owner role not found"));

        // Create owner as first member
        TeamMember ownerMember = TeamMember.builder()
                .userId(userId)
                .email(request.getOwnerEmail())
                .memberType(MemberType.INTERNAL)
                .roleId(ownerRole.getId())
                .roleName(ownerRole.getName())
                .permissions(ownerRole.getPermissions().stream()
                        .map(TeamPermission::name)
                        .collect(Collectors.toSet()))
                .joinedAt(Instant.now())
                .status(MemberStatus.ACTIVE)
                .build();

        // Build team entity
        Team team = Team.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .visibility(request.getVisibility() != null ? request.getVisibility() : TeamVisibility.PRIVATE)
                .allowMemberInvites(request.getAllowMemberInvites() != null ? request.getAllowMemberInvites() : true)
                .requireApprovalToJoin(request.getRequireApprovalToJoin() != null ? request.getRequireApprovalToJoin() : true)
                .allowExternalMembers(request.getAllowExternalMembers() != null ? request.getAllowExternalMembers() : false)
                .quotaSource(request.getQuotaSource() != null ? request.getQuotaSource() : QuotaSource.PERSONAL)
                .quotaSourceId(request.getQuotaSourceId())
                .dedicatedQuotaBytes(request.getDedicatedQuotaBytes())
                .members(new ArrayList<>(List.of(ownerMember)))
                .memberCount(1)
                .customRoleIds(new ArrayList<>())
                .tags(request.getTags())
                .metadata(request.getMetadata())
                .ownerId(userId)
                .createdBy(userId)
                .status(TeamStatus.ACTIVE)
                .licenseFeatureKey("TEAMS_MODULE")
                // Phase 5 Enterprise fields
                .phase(request.getPhase() != null ? request.getPhase() : TeamPhase.PLANNING)
                .projectCode(request.getProjectCode())
                .clientName(request.getClientName())
                .parentTeamId(request.getParentTeamId())
                .createdAt(Instant.now())
                .build();

        // Generate drive ID
        team.setDriveId(Team.teamDriveId(team.getId()));

        // Save team
        team = teamRepository.save(team);

        // Update drive ID with actual team ID
        team.setDriveId(Team.teamDriveId(team.getId()));
        team = teamRepository.save(team);

        log.info("Team created: id={}, name={}", team.getId(), team.getName());

        // Publish event for Permission Manager to create team drive
        publishTeamCreatedEvent(team);

        return mapToDTO(team);
    }

    /**
     * Get team by ID.
     *
     * @param teamId The team ID
     * @return The team DTO
     */
    public TeamDTO getTeam(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Getting team: teamId={}, tenantId={}, userId={}", teamId, tenantId, userId);

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> {
                    log.warn("Team not found: teamId={}, tenantId={}", teamId, tenantId);
                    return new ResourceNotFoundException("Team not found: " + teamId);
                });

        return mapToDTO(team);
    }

    /**
     * Update a team.
     *
     * @param teamId The team ID
     * @param request The update request
     * @return The updated team DTO
     */
    @Transactional
    public TeamDTO updateTeam(String teamId, UpdateTeamRequest request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Check permission
        requirePermission(team, userId, TeamPermission.TEAM_EDIT);

        // Update fields
        if (request.getName() != null && !request.getName().equals(team.getName())) {
            if (teamRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
                throw new IllegalArgumentException("Team name already exists: " + request.getName());
            }
            team.setName(request.getName());
        }

        if (request.getDescription() != null) {
            team.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            team.setVisibility(request.getVisibility());
        }
        if (request.getAllowMemberInvites() != null) {
            team.setAllowMemberInvites(request.getAllowMemberInvites());
        }
        if (request.getRequireApprovalToJoin() != null) {
            team.setRequireApprovalToJoin(request.getRequireApprovalToJoin());
        }
        if (request.getAllowExternalMembers() != null) {
            team.setAllowExternalMembers(request.getAllowExternalMembers());
        }
        if (request.getTags() != null) {
            team.setTags(request.getTags());
        }

        // Phase 5 Enterprise fields
        if (request.getPhase() != null) {
            team.setPhase(request.getPhase());
        }
        if (request.getProjectCode() != null) {
            team.setProjectCode(request.getProjectCode().isEmpty() ? null : request.getProjectCode());
        }
        if (request.getClientName() != null) {
            team.setClientName(request.getClientName().isEmpty() ? null : request.getClientName());
        }
        if (request.getParentTeamId() != null) {
            // Validate parent team exists and user has access
            if (!request.getParentTeamId().isEmpty()) {
                teamRepository.findByIdAndTenantId(request.getParentTeamId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Parent team not found: " + request.getParentTeamId()));
            }
            team.setParentTeamId(request.getParentTeamId().isEmpty() ? null : request.getParentTeamId());
        }

        team.setLastModifiedBy(userId);
        team.setUpdatedAt(Instant.now());

        team = teamRepository.save(team);

        log.info("Team updated: id={}", teamId);

        // Publish update event
        publishTeamUpdatedEvent(team);

        return mapToDTO(team);
    }

    /**
     * Archive a team (soft delete).
     *
     * @param teamId The team ID
     */
    @Transactional
    public void archiveTeam(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Only owner can archive
        if (!team.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the team owner can archive the team");
        }

        team.setStatus(TeamStatus.ARCHIVED);
        team.setArchivedAt(Instant.now());
        team.setLastModifiedBy(userId);
        team.setUpdatedAt(Instant.now());

        teamRepository.save(team);

        log.info("Team archived: id={}", teamId);

        // Publish delete event for cleanup
        publishTeamDeletedEvent(team, false);
    }

    /**
     * Delete a team (permanent).
     *
     * @param teamId The team ID
     */
    @Transactional
    public void deleteTeam(String teamId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Only owner can delete
        if (!team.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the team owner can delete the team");
        }

        // Mark as deleted
        team.setStatus(TeamStatus.DELETED);
        team.setLastModifiedBy(userId);
        team.setUpdatedAt(Instant.now());

        teamRepository.save(team);

        // Delete custom roles
        roleRepository.deleteByTenantIdAndTeamIdAndIsSystemRoleFalse(tenantId, teamId);

        log.info("Team deleted: id={}", teamId);

        // Publish delete event
        publishTeamDeletedEvent(team, true);
    }

    // ============== LIST OPERATIONS ==============

    /**
     * Get teams the current user is a member of.
     *
     * @param cursor Pagination cursor
     * @param limit Page size
     * @return Paginated list of teams
     */
    public CursorPage<TeamDTO> getMyTeams(String cursor, int limit) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        log.debug("Getting my teams: tenantId={}, userId={}, cursor={}, limit={}", tenantId, userId, cursor, limit);

        limit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by("_id"));

        List<Team> teams;
        if (cursor != null && !cursor.isEmpty()) {
            teams = teamRepository.findMemberTeamsAfterCursor(
                    tenantId, TeamStatus.ACTIVE, userId, cursor, pageable);
        } else {
            teams = teamRepository.findByTenantIdAndStatusAndMembersUserIdPaged(
                    tenantId, TeamStatus.ACTIVE, userId, pageable);
        }

        log.info("Found {} teams for user={} in tenant={}, teamIds={}",
                teams.size(), userId, tenantId,
                teams.stream().map(Team::getId).collect(Collectors.joining(", ")));

        boolean hasMore = teams.size() > limit;
        if (hasMore) {
            teams = teams.subList(0, limit);
        }

        String nextCursor = hasMore && !teams.isEmpty()
                ? teams.get(teams.size() - 1).getId()
                : null;

        // Use batch loading to avoid N+1 queries for parent/child teams
        List<TeamDTO> dtos = mapToDTOsWithBatchLoading(teams);

        long totalCount = cursor == null
                ? teamRepository.countMemberTeams(tenantId, TeamStatus.ACTIVE, userId)
                : -1;

        return CursorPage.<TeamDTO>builder()
                .items(dtos)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .totalCount(totalCount)
                .limit(limit)
                .build();
    }

    /**
     * Search public teams.
     *
     * @param query Search query
     * @param limit Max results
     * @return List of matching teams
     */
    public List<TeamDTO> searchPublicTeams(String query, int limit) {
        String tenantId = TenantContext.getTenantId();

        limit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, limit);

        List<Team> teams = teamRepository.searchPublicTeams(tenantId, query, pageable);
        // Use batch loading to avoid N+1 queries
        return mapToDTOsWithBatchLoading(teams);
    }

    // ============== OWNERSHIP TRANSFER ==============

    /**
     * Transfer team ownership to another member.
     *
     * @param teamId The team ID
     * @param newOwnerId The new owner's user ID
     */
    @Transactional
    public void transferOwnership(String teamId, String newOwnerId) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();

        Team team = teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        // Only current owner can transfer
        if (!team.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Only the team owner can transfer ownership");
        }

        // Verify new owner is a member
        TeamMember newOwner = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(newOwnerId) && m.getStatus() == MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not an active team member"));

        // External users cannot be owners
        if (newOwner.getMemberType() == MemberType.EXTERNAL) {
            throw new IllegalArgumentException("External users cannot be team owners");
        }

        // Get roles (system roles have ID = constant like "OWNER", "ADMIN")
        TeamRole ownerRole = roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_OWNER)
                .orElseThrow(() -> new IllegalStateException("Owner role not found"));
        TeamRole adminRole = roleRepository.findSystemRoleById(tenantId, TeamRole.ROLE_ID_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Admin role not found"));

        // Update roles
        for (TeamMember member : team.getMembers()) {
            if (member.getUserId().equals(userId)) {
                // Demote current owner to admin
                member.setRoleId(adminRole.getId());
                member.setRoleName(adminRole.getName());
                member.setPermissions(adminRole.getPermissions().stream()
                        .map(TeamPermission::name)
                        .collect(Collectors.toSet()));
            } else if (member.getUserId().equals(newOwnerId)) {
                // Promote new owner
                member.setRoleId(ownerRole.getId());
                member.setRoleName(ownerRole.getName());
                member.setPermissions(ownerRole.getPermissions().stream()
                        .map(TeamPermission::name)
                        .collect(Collectors.toSet()));
            }
        }

        team.setOwnerId(newOwnerId);
        team.setLastModifiedBy(userId);
        team.setUpdatedAt(Instant.now());

        teamRepository.save(team);

        log.info("Team ownership transferred: teamId={}, from={}, to={}", teamId, userId, newOwnerId);
    }

    // ============== HELPER METHODS ==============

    /**
     * Check if user has a specific permission on a team.
     */
    private void requirePermission(Team team, String userId, TeamPermission permission) {
        TeamMember member = team.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId) && m.getStatus() == MemberStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Not a team member"));

        if (!member.getPermissions().contains(permission.name())) {
            throw new AccessDeniedException("Permission denied: " + permission);
        }
    }

    /**
     * Check if user is a member of the team.
     */
    public boolean isMember(String teamId, String userId) {
        String tenantId = TenantContext.getTenantId();

        return teamRepository.findByIdAndTenantId(teamId, tenantId)
                .map(team -> team.getMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(userId) && m.getStatus() == MemberStatus.ACTIVE))
                .orElse(false);
    }

    /**
     * Get team entity (for internal use by other services).
     */
    public Team getTeamEntity(String teamId) {
        String tenantId = TenantContext.getTenantId();
        return teamRepository.findByIdAndTenantId(teamId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    /**
     * Map a single team to DTO.
     * Note: For single team requests, we still do individual queries for parent/child.
     * For batch operations, use mapToDTOsWithBatchLoading() instead.
     */
    private TeamDTO mapToDTO(Team team) {
        // For single team, use the batch method with a singleton list
        // This ensures consistent behavior and still benefits from any caching
        return mapToDTOsWithBatchLoading(Collections.singletonList(team)).get(0);
    }

    /**
     * Batch map teams to DTOs with efficient loading of parent and child teams.
     * Avoids N+1 queries by batch-loading all parent teams and child teams in 2 queries
     * instead of 2*N queries.
     *
     * PERFORMANCE FIX: Reduces from O(2N) queries to O(2) queries for team lists.
     */
    private List<TeamDTO> mapToDTOsWithBatchLoading(List<Team> teams) {
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }

        String tenantId = teams.get(0).getTenantId();

        // Collect all parent team IDs that need to be resolved
        Set<String> parentTeamIds = teams.stream()
                .map(Team::getParentTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Collect all team IDs to find their children
        List<String> teamIds = teams.stream()
                .map(Team::getId)
                .collect(Collectors.toList());

        // BATCH QUERY 1: Load all parent teams in one query
        Map<String, Team> parentTeamsMap = Collections.emptyMap();
        if (!parentTeamIds.isEmpty()) {
            parentTeamsMap = teamRepository.findByTenantIdAndIdIn(tenantId, new ArrayList<>(parentTeamIds))
                    .stream()
                    .collect(Collectors.toMap(Team::getId, t -> t));
            log.debug("Batch loaded {} parent teams", parentTeamsMap.size());
        }

        // BATCH QUERY 2: Load all child teams in one query
        Map<String, List<Team>> childTeamsMap = Collections.emptyMap();
        if (!teamIds.isEmpty()) {
            childTeamsMap = teamRepository.findByTenantIdAndParentTeamIdInAndStatus(
                            tenantId, teamIds, TeamStatus.ACTIVE)
                    .stream()
                    .collect(Collectors.groupingBy(Team::getParentTeamId));
            log.debug("Batch loaded child teams for {} parent teams", childTeamsMap.size());
        }

        // Map to DTOs using pre-loaded data (no additional queries)
        final Map<String, Team> finalParentTeamsMap = parentTeamsMap;
        final Map<String, List<Team>> finalChildTeamsMap = childTeamsMap;

        return teams.stream()
                .map(team -> mapToDTOWithPreloadedData(team, finalParentTeamsMap, finalChildTeamsMap))
                .collect(Collectors.toList());
    }

    /**
     * Map a single team to DTO using pre-loaded parent and child data.
     * This avoids any additional database queries.
     */
    private TeamDTO mapToDTOWithPreloadedData(
            Team team,
            Map<String, Team> parentTeamsMap,
            Map<String, List<Team>> childTeamsMap) {

        TeamDTO.TeamDTOBuilder builder = TeamDTO.builder()
                .id(team.getId())
                .tenantId(team.getTenantId())
                .name(team.getName())
                .description(team.getDescription())
                .avatar(team.getAvatar())
                .visibility(team.getVisibility())
                .allowMemberInvites(team.getAllowMemberInvites())
                .requireApprovalToJoin(team.getRequireApprovalToJoin())
                .allowExternalMembers(team.getAllowExternalMembers())
                .driveId(team.getDriveId())
                .quotaSource(team.getQuotaSource())
                .memberCount(team.getMemberCount())
                .ownerId(team.getOwnerId())
                .status(team.getStatus())
                .tags(team.getTags())
                // Phase 5 Enterprise fields
                .phase(team.getPhase())
                .projectCode(team.getProjectCode())
                .clientName(team.getClientName())
                .parentTeamId(team.getParentTeamId())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt());

        // Resolve parent team name from pre-loaded map (no query)
        if (team.getParentTeamId() != null) {
            Team parentTeam = parentTeamsMap.get(team.getParentTeamId());
            if (parentTeam != null) {
                builder.parentTeamName(parentTeam.getName());
            }
        }

        // Get child teams from pre-loaded map (no query)
        List<Team> childTeams = childTeamsMap.getOrDefault(team.getId(), Collections.emptyList());
        if (!childTeams.isEmpty()) {
            builder.childTeams(childTeams.stream()
                    .map(child -> TeamDTO.ChildTeamDTO.builder()
                            .id(child.getId())
                            .name(child.getName())
                            .build())
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    // ============== EVENT PUBLISHING ==============

    private void publishTeamCreatedEvent(Team team) {
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "tenantId", team.getTenantId(),
                "teamId", team.getId(),
                "teamName", team.getName(),
                "ownerId", team.getOwnerId(),
                "quotaBytes", team.getDedicatedQuotaBytes() != null ? team.getDedicatedQuotaBytes() : 0L,
                "timestamp", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC_TEAMS_CREATED, team.getId(), event);
    }

    private void publishTeamUpdatedEvent(Team team) {
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "tenantId", team.getTenantId(),
                "teamId", team.getId(),
                "teamName", team.getName(),
                "timestamp", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC_TEAMS_UPDATED, team.getId(), event);
    }

    private void publishTeamDeletedEvent(Team team, boolean isPermanent) {
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "tenantId", team.getTenantId(),
                "teamId", team.getId(),
                "isPermanent", isPermanent,
                "timestamp", Instant.now().toString()
        );
        kafkaTemplate.send(TOPIC_TEAMS_DELETED, team.getId(), event);
    }
}
