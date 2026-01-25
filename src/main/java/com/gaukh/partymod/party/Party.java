package com.gaukh.partymod.party;

import com.hypixel.hytale.server.core.Message;
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

    // ==================== PLAYER UTILITY METHODS ====================

    /**
     * Gets a player's username from their UUID.
     * First checks if the player is online (Universe lookup), then falls back to the
     * persistent name cache in the database. If online, the cache is updated.
     *
     * @param uuid the player's UUID
     * @return the player's username, or "Unknown" if not found anywhere
     */
    @Nonnull
    public static String getPlayerName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null) {
            String username = ref.getUsername();
            // Update the cache with the current username (async to avoid blocking)
            try {
                PartyStorage.cachePlayerName(uuid, username);
            } catch (Exception ignored) {
                // Ignore cache update failures - not critical
            }
            return username;
        }

        // Player is offline - try to get cached name from database
        try {
            String cachedName = PartyStorage.getCachedPlayerName(uuid);
            if (cachedName != null) {
                return cachedName;
            }
        } catch (Exception ignored) {
            // Ignore cache lookup failures
        }

        return "Unknown";
    }

    /**
     * Checks if a player is currently online.
     *
     * @param uuid the player's UUID
     * @return true if the player is online, false otherwise
     */
    public static boolean isPlayerOnline(@Nonnull UUID uuid) {
        return Universe.get().getPlayer(uuid) != null;
    }

    /**
     * Gets a member's display name. Checks fake members first (for testing),
     * then falls back to the real player name from Universe.
     *
     * @param uuid the member's UUID
     * @return the member's display name
     */
    @Nonnull
    public String getMemberName(@Nonnull UUID uuid) {
        // Check fake members first (for testing)
        FakeMember fake = getFakeMember(uuid);
        if (fake != null) {
            return fake.getName();
        }
        return getPlayerName(uuid);
    }

    /**
     * Gets the member's status string showing their role and online status.
     * Format: "Admin" or "Admin (Offline)"
     *
     * @param uuid the member's UUID
     * @return formatted status string
     */
    @Nonnull
    public String getMemberStatus(@Nonnull UUID uuid) {
        String role = getRoleDisplayName(uuid);
        // Fake members are always "online" for display purposes
        if (getFakeMember(uuid) != null) {
            return role;
        }
        return isPlayerOnline(uuid) ? role : role + " (Offline)";
    }

    /**
     * Gets all member UUIDs mapped to their display names.
     * Useful for populating UI lists.
     *
     * @return ordered map of UUID to member name
     */
    @Nonnull
    public Map<UUID, String> getMemberNames() {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (UUID uuid : memberUuids) {
            result.put(uuid, getMemberName(uuid));
        }
        // Include fake members
        if (fakeMembers != null) {
            for (UUID uuid : fakeMembers.keySet()) {
                result.put(uuid, getMemberName(uuid));
            }
        }
        return result;
    }

    /**
     * Sends a message to a player if they are online.
     * Silently does nothing if the player is offline.
     *
     * @param uuid the player's UUID
     * @param message the message to send
     */
    public static void sendMessageToPlayer(@Nonnull UUID uuid, @Nonnull Message message) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null) {
            ref.sendMessage(message);
        }
    }

    /**
     * Adjusts a value up or down within specified bounds.
     *
     * @param action "increase" or "decrease"
     * @param currentValue the current value
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return the adjusted value, clamped to bounds
     */
    public static int adjustBounded(@Nullable String action, int currentValue, int min, int max) {
        if ("increase".equals(action)) {
            return Math.min(max, currentValue + 1);
        } else if ("decrease".equals(action)) {
            return Math.max(min, currentValue - 1);
        }
        return currentValue;
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
