package com.diet.app.dao;

import com.diet.app.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 운동 기록 DB 접근 객체
// 테이블: exercise_logs(user_id, exercise_date, exercise_type, intensity, actual_amount=운동 시간(분), burned_calories)
// 소모 열량 = MET(종류×강도) × 체중(kg) × 시간(h)
//   MET 값은 2024 Adult Compendium of Physical Activities (Herrmann et al., 2024) 의 측정값입니다.
//   각 값 옆 주석은 Compendium 활동 코드입니다. (https://pacompendium.com)
public class ExerciseDAO {

    // 종류 -> [화면 이름, 낮음 MET, 보통 MET, 높음 MET]
    private static final Map<String, Object[]> TYPES = new LinkedHashMap<>();
    static {
        // 17152 걷기 3.2~3.9km/h · 17190 4.5~5.5km/h 보통 · 17200 5.6~6.3km/h 빠르게
        TYPES.put("WALKING",     new Object[]{"걷기",                2.8, 3.8, 4.8});
        // 12020 조깅(자기 속도) · 12050 9.7~10.1km/h · 12070 11.3km/h
        TYPES.put("RUNNING",     new Object[]{"달리기",              7.5, 9.3, 11.0});
        // 01015 / 01016 / 01017 자전거, 자기 속도 쉬움 / 보통 / 힘듦
        TYPES.put("CYCLING",     new Object[]{"자전거",              4.3, 7.0, 9.0});
        // 18240 자유형 느리게 · 18290 자유형 중간 속도 · 18230 자유형 빠르게
        TYPES.put("SWIMMING",    new Object[]{"수영",                5.8, 8.0, 9.8});
        // 02054 여러 운동 8~15회 · 02052 스쿼트·데드리프트 · 02050 고강도(파워리프팅·보디빌딩)
        TYPES.put("STRENGTH",    new Object[]{"근력 운동",           3.5, 5.0, 6.0});
        // 02150 하타 요가 · 02105 필라테스 · 02160 파워 요가
        TYPES.put("YOGA",        new Object[]{"요가·필라테스·스트레칭", 2.3, 2.8, 4.0});
        // 15030 배드민턴(친선) · 15610 축구(일반) · 15040 농구(경기)
        TYPES.put("BALL_SPORTS", new Object[]{"구기 종목",           5.5, 7.0, 8.0});
        // 17081 산길 천천히 · 17082 산길 보통 속도 · 17035 경사 6~10% 오르막
        TYPES.put("HIKING",      new Object[]{"등산",                3.8, 5.3, 7.0});
        // 02024 / 02022 / 02020 맨몸 운동 가볍게 / 보통 / 고강도
        TYPES.put("OTHER",       new Object[]{"맨몸 운동·기타",       2.8, 3.8, 7.5});
    }
    public static final List<String> INTENSITIES = List.of("LOW", "MODERATE", "HIGH");

    public static boolean isValidType(String type) {
        return type != null && TYPES.containsKey(type);
    }

    public static boolean isValidIntensity(String intensity) {
        return intensity != null && INTENSITIES.contains(intensity);
    }

    public static double met(String type, String intensity) {
        Object[] row = TYPES.getOrDefault(type, TYPES.get("OTHER"));
        int index = Math.max(0, INTENSITIES.indexOf(intensity));
        return (double) row[1 + index];
    }

    // 화면용 종류 목록: [{type, label, met: {LOW, MODERATE, HIGH}}]
    public static List<Map<String, Object>> typeCatalog() {
        List<Map<String, Object>> list = new ArrayList<>();
        TYPES.forEach((type, row) -> {
            Map<String, Object> mets = new LinkedHashMap<>();
            for (int i = 0; i < INTENSITIES.size(); i++) mets.put(INTENSITIES.get(i), row[1 + i]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("label", row[0]);
            item.put("met", mets);
            list.add(item);
        });
        return list;
    }

    // ===================== 기간 조회 (날짜 내림차순) =====================
    public List<Map<String, Object>> getRecords(String email, String startDate, String endDate)
            throws SQLException {
        final String sql = """
            SELECT e.exercise_log_id, e.exercise_date, e.exercise_type, e.intensity,
                   e.actual_amount, e.burned_calories
            FROM exercise_logs e
            JOIN users u ON u.user_id = e.user_id
            WHERE u.email = ? AND e.exercise_date BETWEEN ? AND ?
            ORDER BY e.exercise_date DESC, e.exercise_log_id DESC
            """;

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = requireConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setDate(2, toSqlDate(startDate));
            pstmt.setDate(3, toSqlDate(endDate));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("exercise_type");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("exerciseId", rs.getLong("exercise_log_id"));
                    row.put("date", rs.getString("exercise_date"));
                    row.put("type", type);
                    row.put("label", TYPES.containsKey(type) ? TYPES.get(type)[0] : "기타");
                    row.put("intensity", rs.getString("intensity"));
                    row.put("minutes", rs.getInt("actual_amount"));
                    row.put("calories", rs.getInt("burned_calories"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    // ===================== 저장 (소모 열량은 서버에서 계산) =====================
    // 체중은 운동 날짜 기준 가장 최근 신체 기록, 없으면 가입 시 체중을 사용합니다.
    public Map<String, Object> addRecord(String email, String date, String type, String intensity,
                                         int minutes) throws SQLException {
        final String weightSql = """
            SELECT u.user_id,
                   COALESCE((SELECT b.weight_kg FROM body_metrics b
                              WHERE b.user_id = u.user_id AND b.weight_kg IS NOT NULL AND b.record_date <= ?
                              ORDER BY b.record_date DESC LIMIT 1),
                            u.weight_kg) AS weight_kg
            FROM users u WHERE u.email = ?
            """;
        final String insertSql = """
            INSERT INTO exercise_logs (user_id, exercise_date, exercise_type, intensity, actual_amount, burned_calories)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = requireConnection()) {
            long userId;
            double weightKg;
            try (PreparedStatement pstmt = conn.prepareStatement(weightSql)) {
                pstmt.setDate(1, toSqlDate(date));
                pstmt.setString(2, email);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) return null;
                    userId = rs.getLong("user_id");
                    weightKg = rs.getDouble("weight_kg");
                }
            }

            int calories = (int) Math.round(met(type, intensity) * weightKg * minutes / 60.0);
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setLong(1, userId);
                pstmt.setDate(2, toSqlDate(date));
                pstmt.setString(3, type);
                pstmt.setString(4, intensity);
                pstmt.setInt(5, minutes);
                pstmt.setInt(6, calories);
                pstmt.executeUpdate();
            }
            return Map.of("calories", calories);
        }
    }

    // ===================== 삭제 (본인 기록만) =====================
    public boolean deleteRecord(String email, long exerciseId) throws SQLException {
        final String sql = """
            DELETE e FROM exercise_logs e
            JOIN users u ON u.user_id = e.user_id
            WHERE u.email = ? AND e.exercise_log_id = ?
            """;
        try (Connection conn = requireConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setLong(2, exerciseId);
            return pstmt.executeUpdate() > 0;
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

    private static Date toSqlDate(String value) {
        return Date.valueOf(LocalDate.parse(value.trim()));
    }
}
