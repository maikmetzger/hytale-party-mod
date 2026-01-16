package com.gaukh.partymod.party;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

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

    private String id;
    private UUID leaderUuid;
    private final Set<UUID> memberUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PartyRole> memberRoles = new ConcurrentHashMap<>();
    private long createdAt;

    // Transient - not persisted
    private transient Set<UUID> unmodifiableMembers;

    public Party() {
        // Default constructor
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
    }

    public Party(@Nonnull UUID leaderUuid) {
        this();
        this.leaderUuid = leaderUuid;
        this.memberUuids.add(leaderUuid);
        updateUnmodifiable();
    }

    private void updateUnmodifiable() {
        this.unmodifiableMembers = Collections.unmodifiableSet(new HashSet<>(memberUuids));
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

    public boolean isLeader(@Nonnull UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public boolean isMember(@Nonnull UUID uuid) {
        return memberUuids.contains(uuid);
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
        if (!isMember(uuid) || isLeader(uuid)) {
            return;
        }
        memberRoles.put(uuid, role);
    }

    public boolean promote(@Nonnull UUID uuid) {
        if (!isMember(uuid) || isLeader(uuid)) {
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
        if (!isMember(uuid) || isLeader(uuid)) {
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
        if (!isMember(newLeaderUuid) || isLeader(newLeaderUuid)) {
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
}
