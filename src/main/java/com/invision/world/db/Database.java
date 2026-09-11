package com.invision.world.db;

import com.invision.world.InvisionWorldPlugin;

import java.io.File;
import java.sql.*;

public final class Database {
    private final InvisionWorldPlugin plugin;
    private Connection connection;

    public Database(InvisionWorldPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void init() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IllegalStateException("Cannot create plugin data folder");
            }
            Class.forName("com.invision.world.libs.sqlite.JDBC");
            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + new File(plugin.getDataFolder(), "data.db").getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS chunks (" +
                        "world TEXT NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL, " +
                        "layer INTEGER NOT NULL, purchased_by TEXT, purchased_at INTEGER NOT NULL, " +
                        "price REAL NOT NULL, PRIMARY KEY(world,x,z))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS world_stats (" +
                        "world TEXT PRIMARY KEY, fund REAL NOT NULL DEFAULT 0, " +
                        "total_contributed REAL NOT NULL DEFAULT 0, total_spent REAL NOT NULL DEFAULT 0, " +
                        "total_purchases INTEGER NOT NULL DEFAULT 0)");
            }
            try (PreparedStatement p = connection.prepareStatement(
                    "INSERT OR IGNORE INTO world_stats(world) VALUES (?)")) {
                p.setString(1, plugin.getConfig().getString("world-name", "anarchy"));
                p.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("SQLite initialization failed", e);
        }
    }

    public synchronized boolean isPurchased(String world, int x, int z) {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT 1 FROM chunks WHERE world=? AND x=? AND z=?")) {
            p.setString(1, world);
            p.setInt(2, x);
            p.setInt(3, z);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized PurchaseDbResult purchaseChunk(String world, int x, int z, int layer,
                                                         String purchaser, double price) {
        try {
            connection.setAutoCommit(false);
            double fund;
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT fund FROM world_stats WHERE world=?")) {
                check.setString(1, world);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        connection.setAutoCommit(true);
                        return new PurchaseDbResult(false, "World stats row missing");
                    }
                    fund = rs.getDouble(1);
                }
            }
            if (fund + 1e-9 < price) {
                connection.rollback();
                connection.setAutoCommit(true);
                return new PurchaseDbResult(false, "Not enough fund");
            }

            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM chunks WHERE world=? AND x=? AND z=?")) {
                exists.setString(1, world);
                exists.setInt(2, x);
                exists.setInt(3, z);
                try (ResultSet rs = exists.executeQuery()) {
                    if (rs.next()) {
                        connection.rollback();
                        connection.setAutoCommit(true);
                        return new PurchaseDbResult(false, "Already purchased");
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO chunks(world,x,z,layer,purchased_by,purchased_at,price) VALUES(?,?,?,?,?,?,?)")) {
                insert.setString(1, world);
                insert.setInt(2, x);
                insert.setInt(3, z);
                insert.setInt(4, layer);
                insert.setString(5, purchaser);
                insert.setLong(6, System.currentTimeMillis());
                insert.setDouble(7, price);
                insert.executeUpdate();
            }

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE world_stats SET fund=fund-?, total_spent=total_spent+?, total_purchases=total_purchases+1 WHERE world=?")) {
                update.setDouble(1, price);
                update.setDouble(2, price);
                update.setString(3, world);
                update.executeUpdate();
            }

            connection.commit();
            connection.setAutoCommit(true);
            return new PurchaseDbResult(true, "OK");
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
            throw new RuntimeException(e);
        }
    }

    public synchronized double fund(String world) {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT fund FROM world_stats WHERE world=?")) {
            p.setString(1, world);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void donateToFund(String world, double amount) {
        try (PreparedStatement p = connection.prepareStatement(
                "UPDATE world_stats SET fund=fund+?, total_contributed=total_contributed+? WHERE world=?")) {
            p.setDouble(1, amount);
            p.setDouble(2, amount);
            p.setString(3, world);
            p.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized int maxPurchasedLayer(String world) {
        try (PreparedStatement p = connection.prepareStatement("SELECT COALESCE(MAX(layer), 0) FROM chunks WHERE world=?")) {
            p.setString(1, world);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized long purchasedCount(String world) {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT COUNT(*) FROM chunks WHERE world=?")) {
            p.setString(1, world);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized long totalContributed(String world) {
        return singleLong("SELECT CAST(total_contributed AS INTEGER) FROM world_stats WHERE world=?", world);
    }

    public synchronized double totalSpent(String world) {
        try (PreparedStatement p = connection.prepareStatement(
                "SELECT total_spent FROM world_stats WHERE world=?")) {
            p.setString(1, world);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long singleLong(String sql, String world) {
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            p.setString(1, world);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }

    public record PurchaseDbResult(boolean success, String reason) {}
}
