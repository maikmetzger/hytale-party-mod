package com.gaukh.partymod.party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Data model representing a party of players.
 * <p>
 * This class holds the party state:
 * - Unique party ID
 * - Leader UUID (the player who controls the party)
 * - Set of member UUIDs (including the leader)
 * - Creation timestamp
 * <p>
 * Also handles JSON serialization/deserialization for persistence.
 *
 * @see PartyManager for business logic (create, join, leave, kick, etc.)
 * @see PartyInvite for pending invitation data
 */
public class Party {

    private static final int DEFAULT_MAX_MEMBERS = 8;
    private static final int MIN_MEMBERS = 1;
    private static final int MAX_MEMBERS_LIMIT = 20;
    private static final int MAX_NAME_LENGTH = 32;

    private String id;
    private UUID leaderUuid;
    private final Set<UUID> memberUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PartyRole> memberRoles = new ConcurrentHashMap<>();
    private long createdAt;

    // Party settings
    private String name;
    private String password;
    private PartyAccessType accessType = PartyAccessType.LOCKED;
    private int maxMembers = DEFAULT_MAX_MEMBERS;

    // Transient - not persisted
    private transient Set<UUID> unmodifiableMembers;

    // Fake members for testing (not persisted)
    private transient Map<UUID, FakeMember> fakeMembers = new ConcurrentHashMap<>();

    public Party() {
        // Default constructor
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public Party(@Nonnull UUID leaderUuid) {
        this(leaderUuid, "Party");
    }

    public Party(@Nonnull UUID leaderUuid, @Nonnull String name) {
        this();
        this.leaderUuid = leaderUuid;
        this.name = sanitizeName(name);
        this.memberUuids.add(leaderUuid);
        updateUnmodifiable();
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Party";
        }
        String trimmed = name.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), MAX_NAME_LENGTH));
    }

    private void updateUnmodifiable() {
        this.unmodifiableMembers = Set.copyOf(memberUuids);
    }

    // Getters

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    @Nonnull
    public Set<UUID> getMemberUuids() {
        if (unmodifiableMembers == null) {
            updateUnmodifiable();
        }
        return unmodifiableMembers;
    }

    public int getMemberCount() {
        return memberUuids.size();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Nonnull
    public String getName() {
        return name != null ? name : "Party";
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    @Nonnull
    public PartyAccessType getAccessType() {
        return accessType;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public boolean isFull() {
        return memberUuids.size() >= maxMembers;
    }

    public boolean isJoinable() {
        return !isFull() && accessType != PartyAccessType.LOCKED;
    }

    public boolean checkPassword(@Nullable String input) {
        if (password == null || password.isEmpty()) {
            return true;
        }
        return password.equals(input);
    }

    public boolean isLeader(@Nonnull UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public boolean isMember(@Nonnull UUID uuid) {
        return !memberUuids.contains(uuid);
    }

    @Nonnull
    public PartyRole getRole(@Nonnull UUID uuid) {
        if (isLeader(uuid)) {
            return PartyRole.ADMIN; // Leader has highest role
        }
        return memberRoles.getOrDefault(uuid, PartyRole.GUEST);
    }

    @Nonnull
    public String getRoleDisplayName(@Nonnull UUID uuid) {
        if (isLeader(uuid)) {
            return "Leader";
        }
        return getRole(uuid).getDisplayName();
    }

    @Nonnull
    public Map<UUID, PartyRole> getMemberRoles() {
        return Collections.unmodifiableMap(memberRoles);
    }

    // Setters for JSON parsing

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    public void setLeaderUuid(@Nonnull UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setName(@Nonnull String name) {
        this.name = sanitizeName(name);
    }

    public void setPassword(@Nullable String password) {
        this.password = (password == null || password.isEmpty()) ? null : password;
    }

    public void setAccessType(@Nonnull PartyAccessType accessType) {
        this.accessType = accessType;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(MIN_MEMBERS, Math.min(MAX_MEMBERS_LIMIT, maxMembers));
    }

    // Modifiers

    public void addMember(@Nonnull UUID uuid) {
        boolean added = memberUuids.add(uuid);

        if (added) {
            updateUnmodifiable();
        }

    }

    public void removeMember(@Nonnull UUID uuid) {
        if (isLeader(uuid)) {
            return;
        }

        boolean removed = memberUuids.remove(uuid);
        memberRoles.remove(uuid);

        if (removed) {
            updateUnmodifiable();
        }
    }

    public void setRole(@Nonnull UUID uuid, @Nonnull PartyRole role) {
        if (isMember(uuid) || isLeader(uuid)) {
            return;
        }
        memberRoles.put(uuid, role);
    }

    public boolean promote(@Nonnull UUID uuid) {
        if (isMember(uuid) || isLeader(uuid)) {
            return false;
        }
        PartyRole current = getRole(uuid);
        PartyRole next = current.getNextRole();
        if (next != null) {
            memberRoles.put(uuid, next);
            return true;
        }
        return false;
    }

    public boolean demote(@Nonnull UUID uuid) {
        if (isMember(uuid) || isLeader(uuid)) {
            return false;
        }
        PartyRole current = getRole(uuid);
        PartyRole prev = current.getPreviousRole();
        if (prev != null) {
            memberRoles.put(uuid, prev);
            return true;
        }
        return false;
    }

    public void transferLeadership(@Nonnull UUID newLeaderUuid) {
        if (isMember(newLeaderUuid) || isLeader(newLeaderUuid)) {
            return;
        }
        // Old leader becomes Admin
        memberRoles.put(this.leaderUuid, PartyRole.ADMIN);
        // New leader
        this.leaderUuid = newLeaderUuid;
        memberRoles.remove(newLeaderUuid); // Leader has no stored role
    }

    @Nonnull
    public Set<UUID> getMembersExcept(@Nonnull UUID excludeUuid) {
        Set<UUID> result = new HashSet<>(memberUuids);
        result.remove(excludeUuid);

        return result;
    }

    @Nullable
    public UUID getNextLeaderCandidate() {
        for (UUID uuid : memberUuids) {
            if (!uuid.equals(leaderUuid)) {
                return uuid;
            }
        }

        return null;
    }


    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Party{" +
                "id='" + id + '\'' +
                ", leader=" + leaderUuid +
                ", members=" + memberUuids.size() +
                '}';
    }

    // ==================== FAKE MEMBERS (for testing) ====================

    public void addFakeMember(@Nonnull FakeMember fakeMember) {
        if (fakeMembers == null) {
            fakeMembers = new ConcurrentHashMap<>();
        }
        fakeMembers.put(fakeMember.getUuid(), fakeMember);
    }

    public void removeFakeMember(@Nonnull UUID uuid) {
        if (fakeMembers != null) {
            fakeMembers.remove(uuid);
        }
    }

    public void clearFakeMembers() {
        if (fakeMembers != null) {
            fakeMembers.clear();
        }
    }

    @Nonnull
    public Map<UUID, FakeMember> getFakeMembers() {
        if (fakeMembers == null) {
            fakeMembers = new ConcurrentHashMap<>();
        }
        return fakeMembers;
    }

    @Nullable
    public FakeMember getFakeMember(@Nonnull UUID uuid) {
        return fakeMembers != null ? fakeMembers.get(uuid) : null;
    }

    public boolean hasFakeMembers() {
        return fakeMembers != null && !fakeMembers.isEmpty();
    }
}
