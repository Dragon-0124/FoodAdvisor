package com.diet.app.dao;

import com.diet.app.dto.DietRequestDTO;
import com.diet.app.dto.FoodDTO;
import com.diet.app.dto.NutrientDTO;
import com.diet.app.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 식단 기록 DB 접근 객체 (CRUD 및 통계 조회)
// 구조: intake_logs(user_id, intake_date, meal_type) 1 : N intake_items(intake_log_id, food_id, quantity) N : 1 foods
// 칼로리/영양소 합계는 저장하지 않고 foods 와 JOIN 하여 (quantity * 영양소) 로 계산합니다.
public class DietDAO {

    // ===================== 식단 기록 추가 =====================
    // (intake_logs 를 찾거나 생성 -> intake_items INSERT, 하나의 트랜잭션)
    public boolean insertDietRecord(String email, DietRequestDTO dto) {
        if (!isValidRequest(dto) || isBlank(dto.recordDate())) return false;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            try {
                Long userId = findUserId(conn, email);
                if (userId == null) {
                    conn.rollback();
                    return false;
                }

                long logId = findOrCreateLog(conn, userId,
                        toSqlDate(dto.recordDate()), dto.mealType().trim());

                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO intake_items (intake_log_id, food_id, quantity) VALUES (?, ?, ?)")) {
                    pstmt.setLong(1, logId);
                    pstmt.setLong(2, dto.foodId());
                    pstmt.setDouble(3, dto.portion());
                    if (pstmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===================== 식단 기록 목록 조회 (수정/삭제용 intake_item_id 포함) =====================
    public List<Map<String, Object>> getDietRecords(String email, String recordDate) {
        List<Map<String, Object>> list = new ArrayList<>();
        final String sql = """
            SELECT i.intake_item_id, l.intake_date, l.meal_type,
                   f.food_id, f.name, i.quantity,
                   i.quantity * f.avg_calories AS cal,
                   i.quantity * f.carbs_g      AS carbs,
                   i.quantity * f.protein_g    AS protein,
                   i.quantity * f.fat_g        AS fat,
                   i.quantity * f.sugar_g      AS sugar
            FROM intake_logs l
            JOIN users u        ON u.user_id = l.user_id
            JOIN intake_items i ON i.intake_log_id = l.intake_log_id
            JOIN foods f        ON f.food_id = i.food_id
            WHERE u.email = ? AND l.intake_date = ?
            ORDER BY l.intake_log_id, i.intake_item_id
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                pstmt.setDate(2, toSqlDate(recordDate));

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("dietId", rs.getLong("intake_item_id"));
                        row.put("recordDate", rs.getString("intake_date"));
                        row.put("mealType", rs.getString("meal_type"));
                        row.put("foodId", rs.getLong("food_id"));
                        row.put("foodName", rs.getString("name"));
                        row.put("portion", rs.getDouble("quantity"));
                        row.put("calories", Math.round(rs.getDouble("cal")));
                        row.put("carbs", rs.getDouble("carbs"));
                        row.put("protein", rs.getDouble("protein"));
                        row.put("fat", rs.getDouble("fat"));
                        row.put("sugar", rs.getDouble("sugar"));
                        list.add(row);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================== 당일 총 섭취량 조회 =====================
    public NutrientDTO getTodayTotalIntake(Long userId, String todayDate) {
        final String sql = """
            SELECT COALESCE(SUM(i.quantity * f.avg_calories), 0) AS sum_cal,
                   COALESCE(SUM(i.quantity * f.carbs_g), 0)      AS sum_carb,
                   COALESCE(SUM(i.quantity * f.protein_g), 0)    AS sum_pro,
                   COALESCE(SUM(i.quantity * f.fat_g), 0)        AS sum_fat,
                   COALESCE(SUM(i.quantity * f.sugar_g), 0)      AS sum_sugar
            FROM intake_logs l
            JOIN intake_items i ON i.intake_log_id = l.intake_log_id
            JOIN foods f        ON f.food_id = i.food_id
            WHERE l.user_id = ? AND l.intake_date = ?
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setLong(1, userId);
                    pstmt.setDate(2, toSqlDate(todayDate));

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return new NutrientDTO(
                                (int) Math.round(rs.getDouble("sum_cal")),
                                rs.getDouble("sum_carb"),
                                rs.getDouble("sum_pro"),
                                rs.getDouble("sum_fat"),
                                rs.getDouble("sum_sugar")
                            );
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new NutrientDTO(0, 0.0, 0.0, 0.0, 0.0);
    }

    // ===================== 추천 식품 조회 (잔여 영양소 이하인 foods) =====================
    public List<FoodDTO> getRecommendedFoods(NutrientDTO remaining) {
        List<FoodDTO> list = new ArrayList<>();
        final String sql = """
            SELECT food_id, name, avg_calories, carbs_g, protein_g, fat_g, sugar_g
            FROM foods
            WHERE avg_calories <= ? AND carbs_g <= ? AND protein_g <= ? AND fat_g <= ? AND sugar_g <= ?
            ORDER BY avg_calories DESC
            LIMIT 5
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, remaining.calories());
                pstmt.setDouble(2, remaining.carbs());
                pstmt.setDouble(3, remaining.protein());
                pstmt.setDouble(4, remaining.fat());
                pstmt.setDouble(5, remaining.sugar());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new FoodDTO(
                            rs.getLong("food_id"), rs.getString("name"),
                            rs.getInt("avg_calories"), rs.getDouble("carbs_g"),
                            rs.getDouble("protein_g"), rs.getDouble("fat_g"),
                            rs.getDouble("sugar_g")
                        ));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================== 식단 기록 수정 =====================
    // dietId = intake_item_id. 날짜/끼니가 바뀌면 해당 intake_logs 로 이동하고, 비어버린 이전 로그는 정리합니다.
    public boolean updateDietRecord(Long dietId, String email, DietRequestDTO dto) {
        if (dietId == null || dto == null || dto.foodId() == null
                || !Double.isFinite(dto.portion()) || dto.portion() <= 0) {
            return false;
        }

        final String ownerSql = """
            SELECT l.intake_log_id, l.user_id, l.intake_date, l.meal_type
            FROM intake_items i
            JOIN intake_logs l ON l.intake_log_id = i.intake_log_id
            JOIN users u       ON u.user_id = l.user_id
            WHERE i.intake_item_id = ? AND u.email = ?
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            try {
                long oldLogId;
                long userId;
                Date oldDate;
                String oldMeal;

                try (PreparedStatement pstmt = conn.prepareStatement(ownerSql)) {
                    pstmt.setLong(1, dietId);
                    pstmt.setString(2, email);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        oldLogId = rs.getLong("intake_log_id");
                        userId = rs.getLong("user_id");
                        oldDate = rs.getDate("intake_date");
                        oldMeal = rs.getString("meal_type");
                    }
                }

                Date newDate = isBlank(dto.recordDate()) ? oldDate : toSqlDate(dto.recordDate());
                String newMeal = isBlank(dto.mealType()) ? oldMeal : dto.mealType().trim();
                long newLogId = findOrCreateLog(conn, userId, newDate, newMeal);

                boolean updated;
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE intake_items SET intake_log_id = ?, food_id = ?, quantity = ? WHERE intake_item_id = ?")) {
                    pstmt.setLong(1, newLogId);
                    pstmt.setLong(2, dto.foodId());
                    pstmt.setDouble(3, dto.portion());
                    pstmt.setLong(4, dietId);
                    updated = pstmt.executeUpdate() > 0;
                }

                if (!updated) {
                    conn.rollback();
                    return false;
                }
                if (newLogId != oldLogId) {
                    deleteLogIfEmpty(conn, oldLogId);
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===================== 식단 기록 삭제 =====================
    // dietId = intake_item_id. 본인(email) 기록만 삭제하며, 항목이 모두 사라진 intake_logs 는 함께 삭제합니다.
    public boolean deleteDietRecord(Long dietId, String email) {
        if (dietId == null) return false;

        final String ownerSql = """
            SELECT i.intake_log_id
            FROM intake_items i
            JOIN intake_logs l ON l.intake_log_id = i.intake_log_id
            JOIN users u       ON u.user_id = l.user_id
            WHERE i.intake_item_id = ? AND u.email = ?
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            try {
                long logId;
                try (PreparedStatement pstmt = conn.prepareStatement(ownerSql)) {
                    pstmt.setLong(1, dietId);
                    pstmt.setString(2, email);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        logId = rs.getLong(1);
                    }
                }

                boolean deleted;
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM intake_items WHERE intake_item_id = ?")) {
                    pstmt.setLong(1, dietId);
                    deleted = pstmt.executeUpdate() > 0;
                }

                if (!deleted) {
                    conn.rollback();
                    return false;
                }
                deleteLogIfEmpty(conn, logId);

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ===================== 일일 섭취 요약 및 목표 달성률 =====================
    public Map<String, Object> getDailySummary(String email, String recordDate) {
        Map<String, Object> result = new HashMap<>();
        final String sql = """
            SELECT u.target_daily_calories,
                   COALESCE(SUM(i.quantity * f.avg_calories), 0) AS total_cal,
                   COALESCE(SUM(i.quantity * f.carbs_g), 0)      AS total_carb,
                   COALESCE(SUM(i.quantity * f.protein_g), 0)    AS total_pro,
                   COALESCE(SUM(i.quantity * f.fat_g), 0)        AS total_fat,
                   COALESCE(SUM(i.quantity * f.sugar_g), 0)      AS total_sugar
            FROM users u
            LEFT JOIN intake_logs l  ON l.user_id = u.user_id AND l.intake_date = ?
            LEFT JOIN intake_items i ON i.intake_log_id = l.intake_log_id
            LEFT JOIN foods f        ON f.food_id = i.food_id
            WHERE u.email = ?
            GROUP BY u.user_id, u.target_daily_calories
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return result;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDate(1, toSqlDate(recordDate));
                pstmt.setString(2, email);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int target = rs.getInt("target_daily_calories");
                        int current = (int) Math.round(rs.getDouble("total_cal"));

                        result.put("targetCalories", target);
                        result.put("currentCalories", current);
                        result.put("carbs", rs.getDouble("total_carb"));
                        result.put("protein", rs.getDouble("total_pro"));
                        result.put("fat", rs.getDouble("total_fat"));
                        result.put("sugar", rs.getDouble("total_sugar"));

                        String status = current > target
                                ? "EXCEED"
                                : (current >= target * 0.9 ? "ACHIEVED" : "UNDER");
                        result.put("status", status);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // ===================== 기간별 주간/월간 통계 =====================
    public List<Map<String, Object>> getPeriodStats(String email, String startDate, String endDate) {
        List<Map<String, Object>> list = new ArrayList<>();
        final String sql = """
            SELECT l.intake_date,
                   SUM(i.quantity * f.avg_calories) AS daily_cal,
                   SUM(i.quantity * f.carbs_g)      AS carbs,
                   SUM(i.quantity * f.protein_g)    AS protein,
                   SUM(i.quantity * f.fat_g)        AS fat
            FROM intake_logs l
            JOIN users u        ON u.user_id = l.user_id
            JOIN intake_items i ON i.intake_log_id = l.intake_log_id
            JOIN foods f        ON f.food_id = i.food_id
            WHERE u.email = ? AND l.intake_date BETWEEN ? AND ?
            GROUP BY l.intake_date
            ORDER BY l.intake_date
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return list;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                pstmt.setDate(2, toSqlDate(startDate));
                pstmt.setDate(3, toSqlDate(endDate));

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("date", rs.getString("intake_date"));
                        data.put("calories", (int) Math.round(rs.getDouble("daily_cal")));

                        double c = rs.getDouble("carbs");
                        double p = rs.getDouble("protein");
                        double f = rs.getDouble("fat");
                        double totalMacro = c + p + f;

                        data.put("carbRatio", totalMacro > 0 ? Math.round((c / totalMacro) * 100) : 0L);
                        data.put("proteinRatio", totalMacro > 0 ? Math.round((p / totalMacro) * 100) : 0L);
                        data.put("fatRatio", totalMacro > 0 ? Math.round((f / totalMacro) * 100) : 0L);
                        list.add(data);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ===================== 날짜별 섭취 합계 (체성분 예측용, 그램 단위) =====================
    // { "YYYY-MM-DD": [kcal, carbs_g, protein_g, fat_g] } - 기록이 있는 날짜만 포함
    public Map<String, double[]> getDailyIntakeTotals(String email, String startDate, String endDate) {
        Map<String, double[]> totals = new TreeMap<>();
        final String sql = """
            SELECT l.intake_date,
                   SUM(i.quantity * f.avg_calories) AS kcal,
                   SUM(i.quantity * f.carbs_g)      AS carbs,
                   SUM(i.quantity * f.protein_g)    AS protein,
                   SUM(i.quantity * f.fat_g)        AS fat
            FROM intake_logs l
            JOIN users u        ON u.user_id = l.user_id
            JOIN intake_items i ON i.intake_log_id = l.intake_log_id
            JOIN foods f        ON f.food_id = i.food_id
            WHERE u.email = ? AND l.intake_date BETWEEN ? AND ?
            GROUP BY l.intake_date
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return totals;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);
                pstmt.setDate(2, toSqlDate(startDate));
                pstmt.setDate(3, toSqlDate(endDate));
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        totals.put(rs.getString("intake_date"), new double[]{
                                rs.getDouble("kcal"), rs.getDouble("carbs"),
                                rs.getDouble("protein"), rs.getDouble("fat")});
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return totals;
    }

    // ===================== 내부 공통 메서드 =====================

    private static Long findUserId(Connection conn, String email) throws SQLException {
        try (PreparedStatement pstmt =
                     conn.prepareStatement("SELECT user_id FROM users WHERE email = ?")) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    // 같은 사용자/날짜/끼니의 intake_logs 가 있으면 재사용, 없으면 새로 생성
    private static long findOrCreateLog(Connection conn, long userId, Date intakeDate, String mealType)
            throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT intake_log_id FROM intake_logs WHERE user_id = ? AND intake_date = ? AND meal_type = ? LIMIT 1")) {
            pstmt.setLong(1, userId);
            pstmt.setDate(2, intakeDate);
            pstmt.setString(3, mealType);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO intake_logs (user_id, intake_date, meal_type) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, userId);
            pstmt.setDate(2, intakeDate);
            pstmt.setString(3, mealType);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("intake_logs 생성에 실패했습니다.");
    }

    // 항목이 하나도 남지 않은 intake_logs 삭제
    private static void deleteLogIfEmpty(Connection conn, long logId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "DELETE FROM intake_logs WHERE intake_log_id = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM intake_items WHERE intake_log_id = ?)")) {
            pstmt.setLong(1, logId);
            pstmt.setLong(2, logId);
            pstmt.executeUpdate();
        }
    }

    private static boolean isValidRequest(DietRequestDTO dto) {
        return dto != null
                && dto.foodId() != null
                && Double.isFinite(dto.portion())
                && dto.portion() > 0
                && !isBlank(dto.mealType());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // "2026-10-15" 형식 문자열 -> java.sql.Date (형식이 틀리면 DateTimeParseException)
    private static Date toSqlDate(String value) {
        return Date.valueOf(LocalDate.parse(value.trim()));
    }
}
