package com.diet.app.dao;

import com.diet.app.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 신체 기록(체중/골격근량/체지방률) DB 접근 객체
// 테이블: body_metrics(user_id, record_date, weight_kg, muscle_kg, body_fat_pct) - 사용자당 날짜별 1행 (DB/body_metrics.sql 참고)
public class BodyMetricsDAO {

    // ===================== 전체 기록 조회 (날짜 오름차순) =====================
    // 값이 없는(NULL) 항목은 응답 Map 에서 빠집니다.
    public List<Map<String, Object>> getRecords(String email) throws SQLException {
        final String sql = """
            SELECT b.record_date, b.weight_kg, b.muscle_kg, b.body_fat_pct
            FROM body_metrics b
            JOIN users u ON u.user_id = b.user_id
            WHERE u.email = ?
            ORDER BY b.record_date
            """;

        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = requireConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("recordDate", rs.getString("record_date"));
                    row.put("weightKg", nullableDouble(rs, "weight_kg"));
                    row.put("muscleKg", nullableDouble(rs, "muscle_kg"));
                    row.put("bodyFatPct", nullableDouble(rs, "body_fat_pct"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    // ===================== 기록 저장 (같은 날짜가 있으면 갱신) =====================
    // null 로 전달된 항목은 기존 값을 그대로 유지합니다. (예: 체중만 입력하면 골격근량은 그대로)
    public boolean saveRecord(String email, String recordDate,
                              Double weightKg, Double muscleKg, Double bodyFatPct) throws SQLException {
        final String sql = """
            INSERT INTO body_metrics (user_id, record_date, weight_kg, muscle_kg, body_fat_pct)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                weight_kg    = COALESCE(VALUES(weight_kg), weight_kg),
                muscle_kg    = COALESCE(VALUES(muscle_kg), muscle_kg),
                body_fat_pct = COALESCE(VALUES(body_fat_pct), body_fat_pct)
            """;

        try (Connection conn = requireConnection()) {
            Long userId = findUserId(conn, email);
            if (userId == null) return false;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setDate(2, toSqlDate(recordDate));
                setNullableDouble(pstmt, 3, weightKg);
                setNullableDouble(pstmt, 4, muscleKg);
                setNullableDouble(pstmt, 5, bodyFatPct);
                return pstmt.executeUpdate() > 0;
            }
        }
    }

    // ===================== 특정 날짜 기록 삭제 (본인 기록만) =====================
    public boolean deleteRecord(String email, String recordDate) throws SQLException {
        final String sql = """
            DELETE b FROM body_metrics b
            JOIN users u ON u.user_id = b.user_id
            WHERE u.email = ? AND b.record_date = ?
            """;

        try (Connection conn = requireConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setDate(2, toSqlDate(recordDate));
            return pstmt.executeUpdate() > 0;
        }
    }

    // ===================== 본인 기록 전체 삭제 =====================
    public void deleteAll(String email) throws SQLException {
        final String sql = """
            DELETE b FROM body_metrics b
            JOIN users u ON u.user_id = b.user_id
            WHERE u.email = ?
            """;

        try (Connection conn = requireConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.executeUpdate();
        }
    }

    // ===================== 내부 공통 메서드 =====================

    private static Connection requireConnection() throws SQLException {
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("데이터베이스에 연결할 수 없습니다.");
        }
        return conn;
    }

    private static Long findUserId(Connection conn, String email) throws SQLException {
        try (PreparedStatement pstmt =
                     conn.prepareStatement("SELECT user_id FROM users WHERE email = ?")) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableDouble(PreparedStatement pstmt, int index, Double value)
            throws SQLException {
        if (value == null) {
            pstmt.setNull(index, Types.DECIMAL);
        } else {
            pstmt.setDouble(index, value);
        }
    }

    // "2026-10-15" 형식 문자열 -> java.sql.Date (형식이 틀리면 DateTimeParseException)
    private static Date toSqlDate(String value) {
        return Date.valueOf(LocalDate.parse(value.trim()));
    }
}
