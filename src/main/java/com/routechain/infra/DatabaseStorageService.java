package com.routechain.infra;

import com.routechain.domain.Order;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

public class DatabaseStorageService {
    private static final String DB_URL = "jdbc:sqlite:routechain.db";

    public DatabaseStorageService() {
        initDatabase();
    }

    private void initDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS orders ("
                + "id TEXT PRIMARY KEY,"
                + "customer_id TEXT,"
                + "pickup_lat REAL,"
                + "pickup_lng REAL,"
                + "dropoff_lat REAL,"
                + "dropoff_lng REAL,"
                + "fee REAL,"
                + "status TEXT,"
                + "created_at TEXT,"
                + "delivered_at TEXT"
                + ");";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("[DB] SQLite Initialized: routechain.db");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveOrder(Order order) {
        String insertSQL = "INSERT OR REPLACE INTO orders "
                + "(id, customer_id, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, fee, status, created_at, delivered_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, order.getId());
            pstmt.setString(2, order.getCustomerId());
            pstmt.setDouble(3, order.getPickupPoint().lat());
            pstmt.setDouble(4, order.getPickupPoint().lng());
            pstmt.setDouble(5, order.getDropoffPoint().lat());
            pstmt.setDouble(6, order.getDropoffPoint().lng());
            pstmt.setDouble(7, order.getQuotedFee());
            pstmt.setString(8, order.getStatus().name());
            pstmt.setString(9, order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
            pstmt.setString(10, order.getDeliveredAt() != null ? order.getDeliveredAt().toString() : null);

            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] Error saving order " + order.getId() + ": " + e.getMessage());
        }
    }
}
