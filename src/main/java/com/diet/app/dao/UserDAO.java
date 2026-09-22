package com.diet.app.dao;

import com.diet.app.dto.UserDTO;
import com.diet.app.util.DBConnection;
import com.diet.app.util.PasswordEncoder;
public class UserDAO {

    @SuppressWarnings("unused")
	public boolean insertUser(UserDTO user) {
        var sql = """
            INSERT INTO USERS (email, password, name, gender, age, height_cm, weight_kg, activity_level, goal, target_daily_calories)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            
            if (conn == null) return false;

            var encryptedPassword = PasswordEncoder.encode(user.password());

            pstmt.setString(1, user.email());
            pstmt.setString(2, encryptedPassword);
            pstmt.setString(3, user.name());
            pstmt.setString(4, user.gender() != null ? user.gender() : "기타");
            pstmt.setInt(5, user.age());
            pstmt.setDouble(6, user.heightCm());
            pstmt.setDouble(7, user.weightKg());
            pstmt.setString(8, user.actLevel() != null ? user.actLevel() : "SEDENTARY");
            pstmt.setString(9, user.goal() != null ? user.goal() : "체중유지");
            pstmt.setInt(10, user.targetDailyCalories());

            var result = pstmt.executeUpdate();
            if (result > 0) {
                System.out.println("✅ [DB] 회원가입 성공: " + user.email() + " (목표 칼로리: " + user.targetDailyCalories() + "kcal)");
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ [DB] 회원가입 에러 (중복된 이메일이거나 DB 컬럼이 없을 수 있습니다)");
            e.printStackTrace();
        }
        return false;
    }

    @SuppressWarnings("unused")
	public boolean login(String email, String rawPassword) {
        var sql = """
            SELECT password FROM USERS WHERE email = ?
            """;

        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
             
            if (conn == null)return false;

            pstmt.setString(1, email);

            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    var dbEncryptedPassword = rs.getString("password");
                    var inputEncryptedPassword = PasswordEncoder.encode(rawPassword);
                    var isMatch = dbEncryptedPassword.equals(inputEncryptedPassword);
                    
                    if (isMatch) {
                        System.out.println("✅ [DB] 로그인 성공: " + email);
                    } else {
                        System.out.println("❌ [DB] 로그인 실패: 비밀번호 불일치");
                    }
                    return isMatch;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("❌ [DB] 로그인 실패: 존재하지 않는 계정");
        return false;
    }
    
    // 데이터 입력 테스트를 위한 코드
    public UserDTO getUserByEmail(String email) {
        var sql = "SELECT * FROM USERS WHERE email = ?";
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
             
            if (conn == null) return null;
            pstmt.setString(1, email);
            
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new UserDTO(
                        rs.getLong("user_id"), rs.getString("email"), null,
                        rs.getString("name"), rs.getString("gender"), rs.getInt("age"), 
                        rs.getDouble("height_cm"), rs.getDouble("weight_kg"),
                        rs.getString("activity_level"), rs.getString("goal"), rs.getInt("target_daily_calories")
                    );
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
}