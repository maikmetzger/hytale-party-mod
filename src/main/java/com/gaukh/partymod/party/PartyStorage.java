package com.gaukh.partymod.party;


import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PartyStorage {

    private final Party party;

    public PartyStorage(@Nonnull Party party) {
        this.party = party;
    }

    /**
     * Converts to simple JSON string for persistence.
     */
    @Nonnull
    public String toJson() {
        StringBuilder members = new StringBuilder("[");
        boolean first = true;

        for (UUID uuid : party.getMemberUuids()) {
            if (!first) members.append(",");
            members.append("\"").append(uuid.toString()).append("\"");
            first = false;
        }

        members.append("]");

        return String.format(
                "{\"Id\":\"%s\",\"LeaderUuid\":\"%s\",\"MemberUuids\":%s,\"CreatedAt\":%d}",
                party.getId(), party.getLeaderUuid().toString(), members.toString(), party.getCreatedAt()
        );
    }

    /**
     * Parses a Party from a simple JSON string.
     */
    @Nonnull
    public static Party fromJson(@Nonnull String json) {
        Party party = new Party();

        party.setId(extractJsonString(json, "Id"));
        party.setLeaderUuid(UUID.fromString(extractJsonString(json, "LeaderUuid")));
        party.setCreatedAt(extractJsonLong(json));

        // Parse member UUIDs array
        String membersArray = extractJsonArray(json);

        if (membersArray.isEmpty()) return party;

        String[] parts = membersArray.replace("[", "").replace("]", "").replace("\"", "").split(",");

        for (String part : parts) {
            String trimmed = part.trim();

            if (trimmed.isEmpty()) return party;

            party.addMember(UUID.fromString(trimmed));
        }

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

    private static long extractJsonLong(String json) {
        String pattern = "\"" + "CreatedAt" + "\":";

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

    private static String extractJsonArray(String json) {
        String pattern = "\"" + "MemberUuids" + "\":";

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

        PartyStorage other = (PartyStorage) o;

        return Objects.equals(party.getId(), other.party.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(party.getId());
    }
}
