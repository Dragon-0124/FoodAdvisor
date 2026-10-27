package com.diet.app.dao;

import com.diet.app.dto.UserDTO;
import com.diet.app.util.DBConnection;
import com.diet.app.util.PasswordEncoder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
public class UserDAO {


    private static final String PREFERENCE_TYPE = "LIKE";
    private static final String SECURITY_SEPARATOR = "|";
    public boolean insertUser(UserDTO user) {
        final String insertUserSql = """
            INSERT INTO users (email, password, name, gender, age, height_cm, weight_kg,
                               activity_level, securityQuestion, target_daily_calories)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;

            long userId;
            try (PreparedStatement pstmt =
                         conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, user.email());
                pstmt.setString(2, PasswordEncoder.encode(user.password()));
                pstmt.setString(3, user.name());
                pstmt.setString(4, user.gender() != null ? user.gender() : "기타");
                pstmt.setInt(5, user.age());
                pstmt.setDouble(6, user.heightCm());
                pstmt.setDouble(7, user.weightKg());
                pstmt.setString(8, user.actLevel());
                pstmt.setString(9, packSecurity(user.securityQuestion(), user.securityAnswer()));
                pstmt.setInt(10, user.targetDailyCalories());

                if (pstmt.executeUpdate() == 0) return false;

                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (!keys.next()) return false;
                    userId = keys.getLong(1);
                }
            }

            System.out.println("✅ [DB] 회원가입 성공: " + user.email()
                    + " (목표 칼로리: " + user.targetDailyCalories() + "kcal)");

            // 선호 음식 저장은 실패해도 회원가입 자체는 성공으로 처리합니다.
            insertPreference(conn, userId, user.preference());
            return true;
        } catch (Exception e) {
            System.err.println("❌ [DB] 회원가입 에러 (중복된 이메일이거나 DB 컬럼이 없을 수 있습니다)");
            e.printStackTrace();
            return false;
        }
    }

    // 선호 음식 저장 (user_preferences: user_id, category, preference_type)
    private static void insertPreference(Connection conn, long userId, String preference) {
        if (preference == null || preference.isBlank()) return;

        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO user_preferences (user_id, category, preference_type) VALUES (?, ?, ?)")) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, preference.trim());
            pstmt.setString(3, PREFERENCE_TYPE);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ [DB] 선호 음식 저장 실패 (회원가입은 완료됨): " + e.getMessage());
        }
    }

    // 로그인 비밀번호 검증 (users: email 조건으로 password 조회 및 대조)
    public boolean login(String email, String rawPassword) {
        final String sql = "SELECT password FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String dbEncryptedPassword = rs.getString("password");
                        boolean isMatch = dbEncryptedPassword != null
                                && dbEncryptedPassword.equals(PasswordEncoder.encode(rawPassword));

                        System.out.println(isMatch
                                ? "✅ [DB] 로그인 성공: " + email
                                : "❌ [DB] 로그인 실패: 비밀번호 불일치");
                        return isMatch;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("❌ [DB] 로그인 실패: 존재하지 않는 계정");
        return false;
    }

    // 비밀번호 재설정 (users: WHERE email 조건으로 password 갱신)
    public boolean updatePassword(String email, String newRawPassword) {
        final String sql = "UPDATE users SET password = ? WHERE email = ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, PasswordEncoder.encode(newRawPassword));
                pstmt.setString(2, email);
                return pstmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 회원 탈퇴 (연관 데이터를 먼저 삭제한 뒤 users 삭제, 하나의 트랜잭션)
    public boolean deleteUser(String email) {
        final String[] childDeletes = {
            "DELETE i FROM intake_items i "
                + "JOIN intake_logs l ON i.intake_log_id = l.intake_log_id "
                + "JOIN users u ON l.user_id = u.user_id WHERE u.email = ?",
            "DELETE l FROM intake_logs l JOIN users u ON l.user_id = u.user_id WHERE u.email = ?",
            "DELETE e FROM exercise_logs e JOIN users u ON e.user_id = u.user_id WHERE u.email = ?",
            "DELETE w FROM weekly_reports w JOIN users u ON w.user_id = u.user_id WHERE u.email = ?",
            "DELETE p FROM user_preferences p JOIN users u ON p.user_id = u.user_id WHERE u.email = ?"
        };

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(false);

            try {
                for (String sql : childDeletes) {
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, email);
                        pstmt.executeUpdate();
                    }
                }

                boolean deleted;
                try (PreparedStatement pstmt =
                             conn.prepareStatement("DELETE FROM users WHERE email = ?")) {
                    pstmt.setString(1, email);
                    deleted = pstmt.executeUpdate() > 0;
                }

                if (deleted) {
                    conn.commit();
                } else {
                    conn.rollback();
                }
                return deleted;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 회원 정보 조회 (users + user_preferences 의 첫 번째 선호 category)
    // goal, securityAnswer 는 DB 에 저장되지 않으므로 null 로 반환합니다.
    public UserDTO getUserByEmail(String email) {
        final String sql = """
            SELECT u.user_id, u.email, u.name, u.gender, u.age, u.height_cm, u.weight_kg,
                   u.activity_level, u.securityQuestion, u.target_daily_calories,
                   (SELECT p.category FROM user_preferences p
                     WHERE p.user_id = u.user_id AND p.category IS NOT NULL
                     ORDER BY p.preference_id LIMIT 1) AS preference
            FROM users u
            WHERE u.email = ?
            """;

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return null;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, email);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return new UserDTO(
                            rs.getLong("user_id"),
                            rs.getString("email"),
                            null,
                            rs.getString("name"),
                            rs.getString("gender"),
                            rs.getInt("age"),
                            rs.getDouble("height_cm"),
                            rs.getDouble("weight_kg"),
                            rs.getString("activity_level"),
                            null,
                            rs.getString("preference"),
                            unpackQuestion(rs.getString("securityQuestion")),
                            null,
                            rs.getInt("target_daily_calories")
                        );
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 아이디(이메일) 찾기 (users: name 과 securityQuestion("질문|답변해시") 일치 조회)
    public String findEmailBySecurityInfo(String name, String question, String answer) {
        if (question == null || answer == null || answer.isBlank()) return null;

        final String sql = "SELECT email FROM users WHERE name = ? AND securityQuestion = ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return null;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, packSecurity(question, answer));

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("email");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String packSecurity(String question, String answer) {
        if (question == null) return null;
        if (answer == null || answer.isBlank()) return question;

        String hash = PasswordEncoder.encode(answer.trim().toLowerCase(Locale.ROOT));
        return question + SECURITY_SEPARATOR + hash.substring(0, 32);
    }

    private static String unpackQuestion(String stored) {
        if (stored == null) return null;
        int idx = stored.indexOf(SECURITY_SEPARATOR);
        return idx < 0 ? stored : stored.substring(0, idx);
    }
}
