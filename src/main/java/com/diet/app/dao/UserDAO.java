package com.diet.app.dao;

import com.diet.app.dto.UserDTO;
import com.diet.app.util.DBConnection;

public class UserDAO {
	
	//Register
    public boolean insertUser(UserDTO user) {
        var sql = """
            INSERT INTO USERS (email, password, name, age, weight_kg)
            VALUES (?, ?, ?, ?, ?)
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.email());
            pstmt.setString(2, user.password()); // 암호화 추가 필수
            pstmt.setString(3, user.name());
            pstmt.setInt(4, user.age());
            pstmt.setDouble(5, user.weightKg());
            
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // login
    public boolean login(String email, String password) {
        var sql = """
            SELECT password FROM USERS WHERE email = ?
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password").equals(password);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}