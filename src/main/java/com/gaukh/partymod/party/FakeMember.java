package com.gaukh.partymod.party;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * Represents a fake party member for testing purposes.
 * Fake members have a simulated position and appear on the compass.
 * They can move randomly to test the delta update system.
 * Optionally, they can have a visible NPC entity in the world.
 */
public class FakeMember {

    private final UUID uuid;
    private final String name;
    private double x, y, z;
    private float yaw;

    // Movement simulation
    private double originX, originZ;
    private double targetX, targetZ;
    private double moveSpeed = 0.5; // blocks per tick update
    private boolean isMoving = true;
    private final Random random = new Random();

    // NPC Entity reference (optional - for visible models)
    @Nullable
    private Ref<EntityStore> entityRef;
    private String npcType;

    public FakeMember(@Nonnull String name, double x, double y, double z) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0f;

        // Set origin for random movement
        this.originX = x;
        this.originZ = z;
        pickNewTarget();
    }

    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    @Nonnull
    public String getName() {
        return name;
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

    public float getYaw() {
        return yaw;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public boolean isMoving() {
        return isMoving;
    }

    public void setMoving(boolean moving) {
        this.isMoving = moving;
    }

    /**
     * Pick a new random target within 30 blocks of origin.
     */
    private void pickNewTarget() {
        double radius = 30.0;
        targetX = originX + (random.nextDouble() * 2 - 1) * radius;
        targetZ = originZ + (random.nextDouble() * 2 - 1) * radius;
    }

    /**
     * Update position towards target. Call this every tick update.
     * Returns true if position changed.
     */
    public boolean updateMovement() {
        if (!isMoving) return false;

        double dx = targetX - x;
        double dz = targetZ - z;
        double distSq = dx * dx + dz * dz;

        // If close to target, pick new one
        if (distSq < 4.0) {
            pickNewTarget();
            return false;
        }

        // Move towards target
        double dist = Math.sqrt(distSq);
        double moveX = (dx / dist) * moveSpeed;
        double moveZ = (dz / dist) * moveSpeed;

        this.x += moveX;
        this.z += moveZ;

        // Update yaw to face movement direction
        this.yaw = (float) Math.atan2(moveZ, moveX);

        return true;
    }

    // ==================== NPC Entity Methods ====================

    /**
     * Set the NPC entity reference for this fake member.
     */
    public void setEntityRef(@Nullable Ref<EntityStore> entityRef) {
        this.entityRef = entityRef;
    }

    /**
     * Get the NPC entity reference (may be null if no NPC spawned).
     */
    @Nullable
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    /**
     * Check if this fake member has a spawned NPC entity.
     */
    public boolean hasEntity() {
        return entityRef != null && entityRef.isValid();
    }

    /**
     * Set the NPC type used for spawning.
     */
    public void setNpcType(@Nullable String npcType) {
        this.npcType = npcType;
    }

    /**
     * Get the NPC type used for spawning.
     */
    @Nullable
    public String getNpcType() {
        return npcType;
    }

    @Override
    public String toString() {
        return "FakeMember{name='" + name + "', pos=(" + x + ", " + y + ", " + z + ")" +
                (hasEntity() ? ", hasEntity=true" : "") + "}";
    }
}
