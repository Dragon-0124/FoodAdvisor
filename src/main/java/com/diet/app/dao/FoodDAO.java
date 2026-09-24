package com.diet.app.dao;

import com.diet.app.dto.FoodDTO;
import com.diet.app.util.DBConnection; 

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// 식품 DB 접근 객체 (음식 검색 및 커스텀 식품 등록)
public class FoodDAO {

    // 음식 이름 키워드 전체 검색 (DB SELECT: name 조건으로 food_id, name, avg_calories, carbs_g, protein_g, fat_g, sugar_g 조회)
    public List<FoodDTO> searchFoodByName(String keyword) {
        List<FoodDTO> foodList = new ArrayList<>();
        String sql = "SELECT * FROM foods WHERE name LIKE ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "%" + keyword + "%"); 
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            foodList.add(new FoodDTO(
                                rs.getLong("food_id"),
                                rs.getString("name"),
                                rs.getInt("avg_calories"),
                                rs.getDouble("carbs_g"),
                                rs.getDouble("protein_g"),
                                rs.getDouble("fat_g"),
                                rs.getDouble("sugar_g")
                            ));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return foodList;
    }

    // 음식 ID로 단일 상세 조회 (DB SELECT: food_id 조건으로 food_id, name, avg_calories, carbs_g, protein_g, fat_g, sugar_g 조회)
    public FoodDTO getFoodById(Long foodId) {
        String sql = "SELECT * FROM foods WHERE food_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setLong(1, foodId);
                    
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return new FoodDTO(
                                rs.getLong("food_id"),
                                rs.getString("name"),
                                rs.getInt("avg_calories"),
                                rs.getDouble("carbs_g"),
                                rs.getDouble("protein_g"),
                                rs.getDouble("fat_g"),
                                rs.getDouble("sugar_g")
                            );
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    // 음식 검색 페이징 처리 (DB SELECT: name 조건 및 LIMIT, OFFSET 적용하여 food_id, name, avg_calories, carbs_g, protein_g, fat_g, sugar_g 조회)
    public List<FoodDTO> searchFoodByNamePaged(String keyword, int page, int pageSize) {
        List<FoodDTO> foodList = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM foods WHERE name LIKE ? ORDER BY name LIMIT ? OFFSET ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setInt(2, pageSize);
            pstmt.setInt(3, offset);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    foodList.add(new FoodDTO(
                        rs.getLong("food_id"), rs.getString("name"),
                        rs.getInt("avg_calories"), rs.getDouble("carbs_g"),
                        rs.getDouble("protein_g"), rs.getDouble("fat_g"), rs.getDouble("sugar_g")
                    ));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return foodList;
    }

    // 커스텀 식품 직접 등록 (DB INSERT: name, avg_calories, carbs_g, protein_g, fat_g, sugar_g, user_id, is_custom)
    public boolean insertCustomFood(Long userId, FoodDTO food) {
        String sql = "INSERT INTO foods (name, avg_calories, carbs_g, protein_g, fat_g, sugar_g, user_id, is_custom) VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, food.name());
            pstmt.setInt(2, food.avgCalories());
            pstmt.setDouble(3, food.carbs_g());
            pstmt.setDouble(4, food.protein_g());
            pstmt.setDouble(5, food.fat_g());
            pstmt.setDouble(6, food.sugar_g());
            pstmt.setLong(7, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
}