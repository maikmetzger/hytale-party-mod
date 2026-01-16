package com.gaukh.partymod.party;

import javax.annotation.Nonnull;
import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SQLite-based storage for party persistence.
 * <p>
 * Handles all database operations:
 * - init() to create database and tables
 * - saveParty() to insert/update a party
 * - loadAllParties() to load all parties on startup
 * - deleteParty() to remove a party
 * - removeMember() to remove a single member
 *
 * @see Party for party data model
 * @see PartyManager for business logic
 */
public class PartyStorage {

    private static final String DB_PATH = "mods/PartyMod/data/parties.db";
    private static Connection connection;

    /**
     * Initialize database connection and create tables if needed.
     */
    public static void init() throws SQLException {
        // Load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        File dbFile = new File(DB_PATH);
        dbFile.getParentFile().mkdirs();

        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        connection.setAutoCommit(true);

        // Enable foreign keys
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        createTables();
    }

    public static boolean isInitialized() {
        return connection != null;
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS parties (
                    id TEXT PRIMARY KEY,
                    leader_uuid TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS party_members (
                    party_id TEXT NOT NULL,
                    member_uuid TEXT NOT NULL,
                    role INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (party_id, member_uuid),
                    FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE CASCADE
                )
            """);
        }

        // Migration: Add role column if it doesn't exist (for existing databases)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE party_members ADD COLUMN role INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            // Column already exists - ignore
        }
    }

    /**
     * Save or update a party in the database.
     */
    public static void saveParty(@Nonnull Party party) throws SQLException {
        if (connection == null) return;

        // Upsert party
        try (PreparedStatement stmt = connection.prepareStatement("""
            INSERT INTO parties (id, leader_uuid, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET leader_uuid = excluded.leader_uuid
        """)) {
            stmt.setString(1, party.getId());
            stmt.setString(2, party.getLeaderUuid().toString());
            stmt.setLong(3, party.getCreatedAt());
            stmt.executeUpdate();
        }

        // Clear existing members and re-insert
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM party_members WHERE party_id = ?")) {
            stmt.setString(1, party.getId());
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO party_members (party_id, member_uuid, role) VALUES (?, ?, ?)")) {
            for (UUID memberUuid : party.getMemberUuids()) {
                stmt.setString(1, party.getId());
                stmt.setString(2, memberUuid.toString());
                stmt.setInt(3, party.getRole(memberUuid).getLevel());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Load all parties from the database.
     */
    @Nonnull
    public static List<Party> loadAllParties() throws SQLException {
        if (connection == null) {
            return new ArrayList<>();
        }

        Map<String, Party> parties = new HashMap<>();

        // Load party base data
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, leader_uuid, created_at FROM parties")) {
            while (rs.next()) {
                Party party = new Party();
                party.setId(rs.getString("id"));
                party.setLeaderUuid(UUID.fromString(rs.getString("leader_uuid")));
                party.setCreatedAt(rs.getLong("created_at"));
                parties.put(party.getId(), party);
            }
        }

        // Load members with roles
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT party_id, member_uuid, role FROM party_members")) {
            while (rs.next()) {
                String partyId = rs.getString("party_id");
                UUID memberUuid = UUID.fromString(rs.getString("member_uuid"));
                int roleLevel = rs.getInt("role");
                Party party = parties.get(partyId);
                if (party != null) {
                    party.addMember(memberUuid);
                    party.setRole(memberUuid, PartyRole.fromLevel(roleLevel));
                }
            }
        }

        return new ArrayList<>(parties.values());
    }

    /**
     * Delete a party from the database.
     */
    public static void deleteParty(@Nonnull String partyId) throws SQLException {
        if (connection == null) return;

        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM parties WHERE id = ?")) {
            stmt.setString(1, partyId);
            stmt.executeUpdate();
        }
        // Members are deleted automatically via ON DELETE CASCADE
    }

    /**
     * Remove a single member from a party.
     */
    public static void removeMember(@Nonnull String partyId, @Nonnull UUID memberUuid) throws SQLException {
        if (connection == null) return;

        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM party_members WHERE party_id = ? AND member_uuid = ?")) {
            stmt.setString(1, partyId);
            stmt.setString(2, memberUuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Add a member to a party.
     */
    public static void addMember(@Nonnull String partyId, @Nonnull UUID memberUuid) throws SQLException {
        if (connection == null) return;

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO party_members (party_id, member_uuid) VALUES (?, ?)")) {
            stmt.setString(1, partyId);
            stmt.setString(2, memberUuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Update the role of a member.
     */
    public static void updateMemberRole(@Nonnull String partyId, @Nonnull UUID memberUuid, @Nonnull PartyRole role) throws SQLException {
        if (connection == null) return;

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE party_members SET role = ? WHERE party_id = ? AND member_uuid = ?")) {
            stmt.setInt(1, role.getLevel());
            stmt.setString(2, partyId);
            stmt.setString(3, memberUuid.toString());
            stmt.executeUpdate();
        }
    }

    /**
     * Update the leader of a party.
     */
    public static void updateLeader(@Nonnull String partyId, @Nonnull UUID newLeaderUuid) throws SQLException {
        if (connection == null) return;

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE parties SET leader_uuid = ? WHERE id = ?")) {
            stmt.setString(1, newLeaderUuid.toString());
            stmt.setString(2, partyId);
            stmt.executeUpdate();
        }
    }

    /**
     * Close the database connection.
     */
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
