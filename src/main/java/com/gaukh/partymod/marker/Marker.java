package com.gaukh.partymod.marker;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a map marker placed by a player.
 */
public class Marker {

    private String id;
    private String name;
    private UUID ownerUuid;
    private double x;
    private double y;
    private double z;
    private MarkerColor color;
    private MarkerVisibility visibility;
    private long createdAt;

    public Marker() {
        // Default constructor
        this.id = UUID.randomUUID().toString();
        this.color = MarkerColor.BLUE;
        this.visibility = MarkerVisibility.PRIVATE;
        this.createdAt = System.currentTimeMillis();
    }

    public Marker(@Nonnull String name, @Nonnull UUID ownerUuid, double x, double y, double z,
                  @Nonnull MarkerColor color, @Nonnull MarkerVisibility visibility) {
        this();
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.visibility = visibility;
    }

    // Getters

    @Nonnull
    public String getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Nonnull
    public MarkerColor getColor() {
        return color;
    }

    @Nonnull
    public MarkerVisibility getVisibility() {
        return visibility;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // Setters

    public void setId(@Nonnull String id) {
        this.id = id;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    public void setOwnerUuid(@Nonnull UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setColor(@Nonnull MarkerColor color) {
        this.color = color;
    }

    public void setVisibility(@Nonnull MarkerVisibility visibility) {
        this.visibility = visibility;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    // Utility

    public boolean isOwner(@Nonnull UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    /**
     * Calculates the distance from this marker to a position.
     */
    public double distanceTo(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculates the horizontal distance (ignoring Y) from this marker to a position.
     */
    public double horizontalDistanceTo(double px, double pz) {
        double dx = x - px;
        double dz = z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Converts to simple JSON string for persistence.
     */
    @Nonnull
    public String toJson() {
        return String.format(
                "{\"Id\":\"%s\",\"Name\":\"%s\",\"OwnerUuid\":\"%s\",\"X\":%f,\"Y\":%f,\"Z\":%f,\"Color\":\"%s\",\"Visibility\":\"%s\",\"CreatedAt\":%d}",
                id, escapeJson(name), ownerUuid.toString(), x, y, z, color.name(), visibility.name(), createdAt
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Parses a Marker from a simple JSON string.
     */
    @Nonnull
    public static Marker fromJson(@Nonnull String json) {
        Marker marker = new Marker();

        // Simple JSON parsing (not robust, for basic use)
        marker.id = extractJsonString(json, "Id");
        marker.name = extractJsonString(json, "Name");
        marker.ownerUuid = UUID.fromString(extractJsonString(json, "OwnerUuid"));
        marker.x = extractJsonDouble(json, "X");
        marker.y = extractJsonDouble(json, "Y");
        marker.z = extractJsonDouble(json, "Z");
        marker.color = MarkerColor.fromNameOrDefault(extractJsonString(json, "Color"), MarkerColor.BLUE);
        marker.visibility = MarkerVisibility.valueOf(extractJsonString(json, "Visibility"));
        marker.createdAt = extractJsonLong(json, "CreatedAt");

        return marker;
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

    private static double extractJsonDouble(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0.0;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0.0;
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Marker marker = (Marker) o;
        return Objects.equals(id, marker.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Marker{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", position=(" + x + ", " + y + ", " + z + ")" +
                ", color=" + color +
                ", visibility=" + visibility +
                '}';
    }
}
