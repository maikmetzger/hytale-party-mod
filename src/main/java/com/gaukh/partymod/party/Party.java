package com.gaukh.partymod.party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a party of players.
 */
public class Party {

    private String id;
    private UUID leaderUuid;
    private final Set<UUID> memberUuids = ConcurrentHashMap.newKeySet();
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

    public boolean addMember(@Nonnull UUID uuid) {
        boolean added = memberUuids.add(uuid);
        if (added) {
            updateUnmodifiable();
        }
        return added;
    }

    public boolean removeMember(@Nonnull UUID uuid) {
        if (isLeader(uuid)) {
            return false;
        }
        boolean removed = memberUuids.remove(uuid);
        if (removed) {
            updateUnmodifiable();
        }
        return removed;
    }

    public boolean transferLeadership(@Nonnull UUID newLeaderUuid) {
        if (!isMember(newLeaderUuid)) {
            return false;
        }
        this.leaderUuid = newLeaderUuid;
        return true;
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

    /**
     * Converts to simple JSON string for persistence.
     */
    @Nonnull
    public String toJson() {
        StringBuilder members = new StringBuilder("[");
        boolean first = true;
        for (UUID uuid : memberUuids) {
            if (!first) members.append(",");
            members.append("\"").append(uuid.toString()).append("\"");
            first = false;
        }
        members.append("]");

        return String.format(
                "{\"Id\":\"%s\",\"LeaderUuid\":\"%s\",\"MemberUuids\":%s,\"CreatedAt\":%d}",
                id, leaderUuid.toString(), members.toString(), createdAt
        );
    }

    /**
     * Parses a Party from a simple JSON string.
     */
    @Nonnull
    public static Party fromJson(@Nonnull String json) {
        Party party = new Party();

        party.id = extractJsonString(json, "Id");
        party.leaderUuid = UUID.fromString(extractJsonString(json, "LeaderUuid"));
        party.createdAt = extractJsonLong(json, "CreatedAt");

        // Parse member UUIDs array
        String membersArray = extractJsonArray(json, "MemberUuids");
        if (!membersArray.isEmpty()) {
            String[] parts = membersArray.replace("[", "").replace("]", "").replace("\"", "").split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    party.memberUuids.add(UUID.fromString(trimmed));
                }
            }
        }

        party.updateUnmodifiable();
        return party;
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    private static long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0L;
        start += pattern.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String extractJsonArray(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return "[]";
        start += pattern.length();
        int bracketStart = json.indexOf("[", start);
        if (bracketStart == -1) return "[]";
        int bracketEnd = json.indexOf("]", bracketStart);
        if (bracketEnd == -1) return "[]";
        return json.substring(bracketStart, bracketEnd + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Party party = (Party) o;
        return Objects.equals(id, party.id);
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
