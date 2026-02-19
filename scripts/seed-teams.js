/**
 * TeamSync - Seed Sample Teams
 *
 * This script creates comprehensive sample teams to showcase all team functionality.
 * Run with: mongosh "mongodb://..." seed-teams.js
 */

const tenantId = 'default';
const adminUserId = '353047894862887416';
const adminEmail = 'admin@teamsync.local';
const adminName = 'Admin User';
const now = new Date();

// All permissions
const ALL_PERMISSIONS = [
    'TEAM_VIEW', 'TEAM_EDIT', 'TEAM_DELETE', 'TEAM_TRANSFER_OWNERSHIP',
    'MEMBER_VIEW', 'MEMBER_INVITE', 'MEMBER_INVITE_EXTERNAL', 'MEMBER_REMOVE', 'MEMBER_ROLE_ASSIGN',
    'ROLE_VIEW', 'ROLE_CREATE', 'ROLE_EDIT', 'ROLE_DELETE',
    'CONTENT_VIEW', 'CONTENT_UPLOAD', 'CONTENT_VERSION_CREATE', 'CONTENT_EDIT',
    'CONTENT_DELETE', 'CONTENT_SHARE', 'CONTENT_CREATE_FOLDER', 'CONTENT_MOVE', 'CONTENT_COMMENT',
    'TASK_VIEW', 'TASK_CREATE', 'TASK_EDIT', 'TASK_DELETE', 'TASK_ASSIGN', 'TASK_UPDATE_STATUS', 'TASK_COMMENT',
    'ACTIVITY_VIEW', 'SETTINGS_MANAGE'
];

const ADMIN_PERMISSIONS = ALL_PERMISSIONS.filter(p => !['TEAM_DELETE', 'TEAM_TRANSFER_OWNERSHIP'].includes(p));
const MANAGER_PERMISSIONS = ['TEAM_VIEW', 'MEMBER_VIEW', 'ROLE_VIEW', 'CONTENT_VIEW', 'CONTENT_UPLOAD', 'CONTENT_VERSION_CREATE', 'CONTENT_EDIT', 'CONTENT_DELETE', 'CONTENT_SHARE', 'CONTENT_CREATE_FOLDER', 'CONTENT_MOVE', 'CONTENT_COMMENT', 'TASK_VIEW', 'TASK_CREATE', 'TASK_EDIT', 'TASK_DELETE', 'TASK_ASSIGN', 'TASK_UPDATE_STATUS', 'TASK_COMMENT', 'ACTIVITY_VIEW'];
const MEMBER_PERMISSIONS = ['TEAM_VIEW', 'MEMBER_VIEW', 'ROLE_VIEW', 'CONTENT_VIEW', 'CONTENT_UPLOAD', 'CONTENT_VERSION_CREATE', 'CONTENT_EDIT', 'CONTENT_CREATE_FOLDER', 'CONTENT_COMMENT', 'TASK_VIEW', 'TASK_UPDATE_STATUS', 'TASK_COMMENT', 'ACTIVITY_VIEW'];
const GUEST_PERMISSIONS = ['TEAM_VIEW', 'MEMBER_VIEW', 'CONTENT_VIEW', 'TASK_VIEW', 'ACTIVITY_VIEW'];
const EXTERNAL_PERMISSIONS = ['TEAM_VIEW', 'MEMBER_VIEW', 'CONTENT_VIEW', 'CONTENT_UPLOAD', 'CONTENT_VERSION_CREATE', 'TASK_VIEW', 'TASK_COMMENT', 'ACTIVITY_VIEW'];

// Permission mapping by role
const ROLE_PERMISSIONS = {
    'OWNER': ALL_PERMISSIONS,
    'ADMIN': ADMIN_PERMISSIONS,
    'MANAGER': MANAGER_PERMISSIONS,
    'MEMBER': MEMBER_PERMISSIONS,
    'GUEST': GUEST_PERMISSIONS,
    'EXTERNAL': EXTERNAL_PERMISSIONS
};

// Sample users (simulated)
const sampleUsers = {
    sarah: { id: 'user-sarah-johnson', email: 'sarah.johnson@teamsync.local', name: 'Sarah Johnson' },
    mike: { id: 'user-mike-chen', email: 'mike.chen@teamsync.local', name: 'Mike Chen' },
    emily: { id: 'user-emily-davis', email: 'emily.davis@teamsync.local', name: 'Emily Davis' },
    alex: { id: 'user-alex-wilson', email: 'alex.wilson@teamsync.local', name: 'Alex Wilson' },
    jessica: { id: 'user-jessica-lee', email: 'jessica.lee@teamsync.local', name: 'Jessica Lee' },
    david: { id: 'user-david-brown', email: 'david.brown@teamsync.local', name: 'David Brown' },
    lisa: { id: 'user-lisa-garcia', email: 'lisa.garcia@teamsync.local', name: 'Lisa Garcia' },
    clientJohn: { id: 'external-client1', email: 'john@acme-corp.com', name: 'John Smith (ACME Corp)' },
    vendorMaria: { id: 'external-vendor1', email: 'maria@designstudio.com', name: 'Maria Rodriguez (Design Studio)' }
};

// Helper: Create member object
function createMember(user, roleId, memberType, invitedBy) {
    const roleNames = { OWNER: 'Owner', ADMIN: 'Admin', MANAGER: 'Manager', MEMBER: 'Member', GUEST: 'Guest', EXTERNAL: 'External' };
    return {
        userId: user.id,
        email: user.email,
        memberType: memberType || 'INTERNAL',
        roleId: roleId,
        roleName: roleNames[roleId] || roleId,
        permissions: ROLE_PERMISSIONS[roleId] || [],
        joinedAt: now,
        invitedBy: invitedBy || null,
        lastActiveAt: now,
        status: 'ACTIVE'
    };
}

print('');
print('=== TeamSync - Seed Sample Teams ===');
print('');

// Step 1: Create system roles
print('[1/3] Creating system roles...');

const systemRoles = [
    { _id: 'OWNER', tenantId: tenantId, teamId: null, name: 'Owner', description: 'Full access to all team features', color: '#9C27B0', displayOrder: 100, permissions: ALL_PERMISSIONS, isSystemRole: true, isDefault: false, isExternalOnly: false, createdAt: now },
    { _id: 'ADMIN', tenantId: tenantId, teamId: null, name: 'Admin', description: 'Full access except team deletion', color: '#2196F3', displayOrder: 80, permissions: ADMIN_PERMISSIONS, isSystemRole: true, isDefault: false, isExternalOnly: false, createdAt: now },
    { _id: 'MANAGER', tenantId: tenantId, teamId: null, name: 'Manager', description: 'Can manage content and tasks', color: '#4CAF50', displayOrder: 60, permissions: MANAGER_PERMISSIONS, isSystemRole: true, isDefault: false, isExternalOnly: false, createdAt: now },
    { _id: 'MEMBER', tenantId: tenantId, teamId: null, name: 'Member', description: 'Can create and edit content', color: '#FF9800', displayOrder: 40, permissions: MEMBER_PERMISSIONS, isSystemRole: true, isDefault: true, isExternalOnly: false, createdAt: now },
    { _id: 'GUEST', tenantId: tenantId, teamId: null, name: 'Guest', description: 'Read-only access', color: '#9E9E9E', displayOrder: 20, permissions: GUEST_PERMISSIONS, isSystemRole: true, isDefault: false, isExternalOnly: false, createdAt: now },
    { _id: 'EXTERNAL', tenantId: tenantId, teamId: null, name: 'External', description: 'Limited access for external users', color: '#795548', displayOrder: 10, permissions: EXTERNAL_PERMISSIONS, isSystemRole: true, isDefault: false, isExternalOnly: true, createdAt: now }
];

systemRoles.forEach(role => {
    db.team_roles.updateOne({ _id: role._id }, { $set: role }, { upsert: true });
});
print('  ✓ ' + systemRoles.length + ' system roles created/updated');

// Step 2: Generate team IDs (as ObjectId, not string)
print('[2/3] Generating team IDs...');

// IMPORTANT: Store _id as ObjectId, not string.
// Spring Data MongoDB converts 24-char hex strings to ObjectId in queries,
// so we must store them as ObjectId for findByIdAndTenantId to work.
const programTeamId = new ObjectId();
const websiteTeamId = new ObjectId();
const mobileTeamId = new ObjectId();
const dataTeamId = new ObjectId();
const marketingTeamId = new ObjectId();
const archivedTeamId = new ObjectId();

print('  ✓ Generated 6 team IDs (as ObjectId)');

// Step 3: Create teams
print('[3/3] Creating sample teams...');

const adminUser = { id: adminUserId, email: adminEmail, name: adminName };

const teams = [
    // TEAM 1: Enterprise Transformation Program (Parent)
    {
        _id: programTeamId,
        tenantId: tenantId,
        name: 'Enterprise Transformation Program',
        description: 'Umbrella program coordinating all digital transformation initiatives including website redesign, mobile development, and data migration. This program aims to modernize our technology stack and improve customer experience across all digital touchpoints.',
        avatar: null,
        visibility: 'PRIVATE',
        status: 'ACTIVE',
        driveId: 'team-' + programTeamId,
        allowMemberInvites: true,
        requireApprovalToJoin: true,
        allowExternalMembers: false,
        quotaSource: 'DEDICATED',
        quotaSourceId: null,
        dedicatedQuotaBytes: NumberLong(107374182400),
        members: [
            createMember(adminUser, 'OWNER'),
            createMember(sampleUsers.sarah, 'ADMIN', 'INTERNAL', adminUserId),
            createMember(sampleUsers.mike, 'MANAGER', 'INTERNAL', adminUserId),
            createMember(sampleUsers.emily, 'MEMBER', 'INTERNAL', sampleUsers.sarah.id),
            createMember(sampleUsers.alex, 'MEMBER', 'INTERNAL', sampleUsers.sarah.id),
            createMember(sampleUsers.jessica, 'GUEST', 'INTERNAL', sampleUsers.sarah.id)
        ],
        memberCount: 6,
        customRoleIds: [],
        metadata: { budget: 500000, currency: 'USD', department: 'IT' },
        tags: ['transformation', 'digital', 'program', 'strategic'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: adminUserId,
        createdBy: adminUserId,
        lastModifiedBy: adminUserId,
        phase: 'ACTIVE',
        projectCode: 'ETP-2024-001',
        clientName: null,
        parentTeamId: null,
        entityVersion: NumberLong(0),
        createdAt: new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000),
        updatedAt: now,
        archivedAt: null
    },

    // TEAM 2: Website Redesign (Child, ACTIVE)
    {
        _id: websiteTeamId,
        tenantId: tenantId,
        name: 'Website Redesign Project',
        description: 'Complete overhaul of corporate website with modern design, improved UX, and performance optimization.',
        avatar: null,
        visibility: 'PRIVATE',
        status: 'ACTIVE',
        driveId: 'team-' + websiteTeamId,
        allowMemberInvites: true,
        requireApprovalToJoin: false,
        allowExternalMembers: true,
        quotaSource: 'PERSONAL',
        quotaSourceId: adminUserId,
        dedicatedQuotaBytes: null,
        members: [
            createMember(adminUser, 'OWNER'),
            createMember(sampleUsers.emily, 'ADMIN', 'INTERNAL', adminUserId),
            createMember(sampleUsers.david, 'MANAGER', 'INTERNAL', sampleUsers.emily.id),
            createMember(sampleUsers.lisa, 'MEMBER', 'INTERNAL', sampleUsers.emily.id),
            createMember(sampleUsers.vendorMaria, 'EXTERNAL', 'EXTERNAL', sampleUsers.emily.id)
        ],
        memberCount: 5,
        customRoleIds: [],
        metadata: { budget: 75000, sprint: 'Sprint 4', milestone: 'MVP Launch' },
        tags: ['website', 'redesign', 'ux', 'frontend'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: adminUserId,
        createdBy: adminUserId,
        lastModifiedBy: sampleUsers.emily.id,
        phase: 'ACTIVE',
        projectCode: 'WEB-2024-042',
        clientName: null,
        parentTeamId: programTeamId,
        entityVersion: NumberLong(0),
        createdAt: new Date(now.getTime() - 60 * 24 * 60 * 60 * 1000),
        updatedAt: now,
        archivedAt: null
    },

    // TEAM 3: Mobile App Development (Child, PLANNING)
    {
        _id: mobileTeamId,
        tenantId: tenantId,
        name: 'Mobile App Development',
        description: 'Native mobile application development for iOS and Android platforms.',
        avatar: null,
        visibility: 'RESTRICTED',
        status: 'ACTIVE',
        driveId: 'team-' + mobileTeamId,
        allowMemberInvites: true,
        requireApprovalToJoin: true,
        allowExternalMembers: false,
        quotaSource: 'PERSONAL',
        quotaSourceId: sampleUsers.mike.id,
        dedicatedQuotaBytes: null,
        members: [
            createMember(sampleUsers.mike, 'OWNER'),
            createMember(adminUser, 'ADMIN', 'INTERNAL', sampleUsers.mike.id),
            createMember(sampleUsers.alex, 'MANAGER', 'INTERNAL', sampleUsers.mike.id),
            createMember(sampleUsers.jessica, 'MEMBER', 'INTERNAL', sampleUsers.alex.id)
        ],
        memberCount: 4,
        customRoleIds: [],
        metadata: { platform: ['iOS', 'Android'], framework: 'React Native' },
        tags: ['mobile', 'app', 'ios', 'android'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: sampleUsers.mike.id,
        createdBy: sampleUsers.mike.id,
        lastModifiedBy: sampleUsers.mike.id,
        phase: 'PLANNING',
        projectCode: 'MOB-2024-015',
        clientName: null,
        parentTeamId: programTeamId,
        entityVersion: NumberLong(0),
        createdAt: new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000),
        updatedAt: now,
        archivedAt: null
    },

    // TEAM 4: Data Migration (REVIEW, with external client)
    {
        _id: dataTeamId,
        tenantId: tenantId,
        name: 'ACME Corp Data Migration',
        description: 'Enterprise data migration project for ACME Corporation with zero downtime.',
        avatar: null,
        visibility: 'PRIVATE',
        status: 'ACTIVE',
        driveId: 'team-' + dataTeamId,
        allowMemberInvites: false,
        requireApprovalToJoin: true,
        allowExternalMembers: true,
        quotaSource: 'DEDICATED',
        quotaSourceId: null,
        dedicatedQuotaBytes: NumberLong(53687091200),
        members: [
            createMember(sampleUsers.sarah, 'OWNER'),
            createMember(adminUser, 'ADMIN', 'INTERNAL', sampleUsers.sarah.id),
            createMember(sampleUsers.david, 'MANAGER', 'INTERNAL', sampleUsers.sarah.id),
            createMember(sampleUsers.lisa, 'MEMBER', 'INTERNAL', sampleUsers.david.id),
            createMember(sampleUsers.clientJohn, 'EXTERNAL', 'EXTERNAL', sampleUsers.sarah.id)
        ],
        memberCount: 5,
        customRoleIds: [],
        metadata: { dataVolume: '2.5 TB', recordCount: 15000000, complianceFramework: 'SOC2' },
        tags: ['migration', 'data', 'client-project', 'enterprise'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: sampleUsers.sarah.id,
        createdBy: sampleUsers.sarah.id,
        lastModifiedBy: sampleUsers.sarah.id,
        phase: 'REVIEW',
        projectCode: 'DM-2024-ACME',
        clientName: 'ACME Corporation',
        parentTeamId: null,
        entityVersion: NumberLong(0),
        createdAt: new Date(now.getTime() - 120 * 24 * 60 * 60 * 1000),
        updatedAt: now,
        archivedAt: null
    },

    // TEAM 5: Q4 Marketing Campaign (CLOSING)
    {
        _id: marketingTeamId,
        tenantId: tenantId,
        name: 'Q4 Marketing Campaign',
        description: 'Holiday season marketing campaign including email, social media, and PPC advertising.',
        avatar: null,
        visibility: 'PUBLIC',
        status: 'ACTIVE',
        driveId: 'team-' + marketingTeamId,
        allowMemberInvites: true,
        requireApprovalToJoin: false,
        allowExternalMembers: false,
        quotaSource: 'PERSONAL',
        quotaSourceId: sampleUsers.emily.id,
        dedicatedQuotaBytes: null,
        members: [
            createMember(sampleUsers.emily, 'OWNER'),
            createMember(sampleUsers.jessica, 'ADMIN', 'INTERNAL', sampleUsers.emily.id),
            createMember(sampleUsers.alex, 'MEMBER', 'INTERNAL', sampleUsers.jessica.id),
            createMember(adminUser, 'GUEST', 'INTERNAL', sampleUsers.emily.id)
        ],
        memberCount: 4,
        customRoleIds: [],
        metadata: { budget: 150000, channels: ['Email', 'Social', 'PPC'], roi: '320%' },
        tags: ['marketing', 'campaign', 'q4', 'holiday'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: sampleUsers.emily.id,
        createdBy: sampleUsers.emily.id,
        lastModifiedBy: sampleUsers.jessica.id,
        phase: 'CLOSING',
        projectCode: 'MKT-2024-Q4',
        clientName: null,
        parentTeamId: null,
        entityVersion: NumberLong(0),
        createdAt: new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000),
        updatedAt: now,
        archivedAt: null
    },

    // TEAM 6: 2023 Annual Report (ARCHIVED)
    {
        _id: archivedTeamId,
        tenantId: tenantId,
        name: '2023 Annual Report',
        description: 'Team responsible for the 2023 annual report. Project completed and archived.',
        avatar: null,
        visibility: 'PRIVATE',
        status: 'ARCHIVED',
        driveId: 'team-' + archivedTeamId,
        allowMemberInvites: false,
        requireApprovalToJoin: true,
        allowExternalMembers: false,
        quotaSource: 'PERSONAL',
        quotaSourceId: adminUserId,
        dedicatedQuotaBytes: null,
        members: [
            createMember(adminUser, 'OWNER'),
            createMember(sampleUsers.sarah, 'ADMIN', 'INTERNAL', adminUserId),
            createMember(sampleUsers.mike, 'MEMBER', 'INTERNAL', sampleUsers.sarah.id)
        ],
        memberCount: 3,
        customRoleIds: [],
        metadata: { fiscalYear: 2023, pageCount: 48, published: true },
        tags: ['annual-report', 'finance', '2023', 'archived'],
        pinnedDocumentIds: [],
        pinnedTaskIds: [],
        ownerId: adminUserId,
        createdBy: adminUserId,
        lastModifiedBy: sampleUsers.sarah.id,
        phase: 'ARCHIVED',
        projectCode: 'AR-2023',
        clientName: null,
        parentTeamId: null,
        entityVersion: NumberLong(0),
        createdAt: new Date('2023-09-01'),
        updatedAt: new Date('2024-01-15'),
        archivedAt: new Date('2024-01-15')
    }
];

// Insert or update teams
let created = 0;
let updated = 0;

teams.forEach(team => {
    const existing = db.teams.findOne({ tenantId: team.tenantId, name: team.name });
    if (existing) {
        db.teams.updateOne(
            { _id: existing._id },
            { $set: { ...team, _id: existing._id, driveId: existing.driveId } }
        );
        updated++;
    } else {
        db.teams.insertOne(team);
        created++;
    }
});

print('  ✓ ' + created + ' teams created, ' + updated + ' updated');

// Print summary
print('');
print('=== Summary ===');
print('');
print('Teams in database (with IDs for direct access):');
print('');
db.teams.find({ tenantId: tenantId }).sort({ createdAt: -1 }).forEach(t => {
    const parent = t.parentTeamId ? ' (Child of Program)' : '';
    const external = t.members && t.members.some(m => m.memberType === 'EXTERNAL') ? ' [Has External]' : '';
    print('  • ' + t.name + parent + external);
    print('    ID: ' + t._id);
    print('    Phase: ' + (t.phase || 'N/A') + ' | Members: ' + (t.memberCount || 0) + ' | Code: ' + (t.projectCode || 'N/A'));
    print('    URL: https://portal.teamsync.link/teams/' + t._id);
    print('');
});

print('');
print('Features demonstrated:');
print('  ✓ All 5 phases (Planning, Active, Review, Closing, Archived)');
print('  ✓ Program hierarchy (parent/child teams)');
print('  ✓ All 6 roles (Owner, Admin, Manager, Member, Guest, External)');
print('  ✓ Internal and External members');
print('  ✓ Visibility settings (Public, Private, Restricted)');
print('  ✓ Enterprise features (project codes, client names)');
print('');
print('Done! Access teams at: https://portal.teamsync.link/teams');
