package com.diet.app.dao;

import com.diet.app.dto.*;
import com.diet.app.util.DBConnection;

// 식단 기록 DB 접근 객체 (CRUD 및 통계 조회)
public class DietDAO {

    // 식단 기록 추가 (DB INSERT: user_id, record_date, meal_type, food_name, portion, total_calories, total_carbs, total_protein, total_fat, total_sugar)
    public boolean insertDietRecord(String email, DietRequestDTO dto) {
        var sql = """
            INSERT INTO USER_DIET_RECORDS (
                user_id, record_date, meal_type, food_name, portion,
                total_calories, total_carbs, total_protein, total_fat, total_sugar
            )
            SELECT u.user_id, ?, ?, f.name, ?,
                   CAST(f.calories * ? AS SIGNED),
                   f.carbs_g * ?, f.protein_g * ?, f.fat_g * ?, f.sugar_g * ?
            FROM USERS u, FOODS f
            WHERE u.email = ? AND f.food_id = ?
            """;

        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            
            if (conn == null) return false;

            double p = dto.portion();

            pstmt.setString(1, dto.recordDate());
            pstmt.setString(2, dto.mealType());
            pstmt.setDouble(3, p);
            
            pstmt.setDouble(4, p);
            pstmt.setDouble(5, p);
            pstmt.setDouble(6, p);
            pstmt.setDouble(7, p);
            pstmt.setDouble(8, p);
            
            pstmt.setString(9, email);
            pstmt.setLong(10, dto.foodId());

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 당일 총 섭취량 조회 (DB SELECT: 특정 user_id와 record_date의 total_calories, total_carbs, total_protein, total_fat, total_sugar 합계)
    public NutrientDTO getTodayTotalIntake(Long userId, String todayDate) {
        var sql = """
            SELECT 
                COALESCE(SUM(total_calories), 0) as sum_cal,
                COALESCE(SUM(total_carbs), 0) as sum_carb,
                COALESCE(SUM(total_protein), 0) as sum_pro,
                COALESCE(SUM(total_fat), 0) as sum_fat,
                COALESCE(SUM(total_sugar), 0) as sum_sugar
            FROM USER_DIET_RECORDS 
            WHERE user_id = ? AND record_date = ?
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setLong(1, userId);
            pstmt.setString(2, todayDate);
            
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new NutrientDTO(
                        rs.getInt("sum_cal"), rs.getDouble("sum_carb"),
                        rs.getDouble("sum_pro"), rs.getDouble("sum_fat"),
                        rs.getDouble("sum_sugar")
                    );
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        return new NutrientDTO(0, 0.0, 0.0, 0.0, 0.0);
    }

    // 추천 식품 조회 (DB SELECT: 잔여 영양소 기준 이하인 food_id, name, calories, carbs_g, protein_g, fat_g, sugar_g)
    public java.util.List<FoodDTO> getRecommendedFoods(NutrientDTO remaining) {
        var list = new java.util.ArrayList<FoodDTO>();
        
        var sql = """
            SELECT food_id, name, calories, carbs_g, protein_g, fat_g, sugar_g
            FROM FOODS 
            WHERE calories <= ? AND carbs_g <= ? AND protein_g <= ? AND fat_g <= ? AND sugar_g <= ?
            ORDER BY calories DESC 
            LIMIT 5
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, remaining.calories());
            pstmt.setDouble(2, remaining.carbs());
            pstmt.setDouble(3, remaining.protein());
            pstmt.setDouble(4, remaining.fat());
            pstmt.setDouble(5, remaining.sugar());
            
            try (var rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new FoodDTO(
                        rs.getLong("food_id"), rs.getString("name"),
                        rs.getInt("calories"), rs.getDouble("carbs_g"),
                        rs.getDouble("protein_g"), rs.getDouble("fat_g"),
                        rs.getDouble("sugar_g")
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // 식단 기록 수정 (DB UPDATE: WHERE record_id 기준 meal_type, portion, total_calories, total_carbs, total_protein, total_fat, total_sugar 덮어쓰기)
    public boolean updateDietRecord(Long dietId, String email, DietRequestDTO dto) {
        String sql = """
            UPDATE USER_DIET_RECORDS r
            JOIN USERS u ON r.user_id = u.user_id
            JOIN FOODS f ON f.food_id = ?
            SET r.meal_type = ?,
                r.portion = ?,
                r.total_calories = CAST(f.calories * ? AS SIGNED),
                r.total_carbs = f.carbs_g * ?,
                r.total_protein = f.protein_g * ?,
                r.total_fat = f.fat_g * ?,
                r.total_sugar = f.sugar_g * ?
            WHERE r.record_id = ? AND u.email = ?
            """;

        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            
            if (conn == null) return false;

            double p = dto.portion();

            pstmt.setLong(1, dto.foodId());
            pstmt.setString(2, dto.mealType());
            pstmt.setDouble(3, p);
            
            pstmt.setDouble(4, p);
            pstmt.setDouble(5, p);
            pstmt.setDouble(6, p);
            pstmt.setDouble(7, p);
            pstmt.setDouble(8, p);
            
            pstmt.setLong(9, dietId);
            pstmt.setString(10, email);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 식단 기록 삭제 (DB DELETE: WHERE record_id 및 email 권한 검증)
    public boolean deleteDietRecord(Long dietId, String email) {
        String sql = "DELETE r FROM USER_DIET_RECORDS r JOIN USERS u ON r.user_id = u.user_id WHERE r.record_id = ? AND u.email = ?";
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, dietId);
            pstmt.setString(2, email);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // 일일 섭취 요약 및 목표 달성률 (DB SELECT: target_daily_calories, total_calories, total_carbs, total_protein, total_fat 합계)
    public java.util.Map<String, Object> getDailySummary(String email, String recordDate) {
        var result = new java.util.HashMap<String, Object>();
        String sql = """
            SELECT 
                u.target_daily_calories,
                COALESCE(SUM(r.total_calories), 0) as total_cal,
                COALESCE(SUM(r.total_carbs), 0) as total_carb,
                COALESCE(SUM(r.total_protein), 0) as total_pro,
                COALESCE(SUM(r.total_fat), 0) as total_fat
            FROM USERS u
            LEFT JOIN USER_DIET_RECORDS r ON u.user_id = r.user_id AND r.record_date = ?
            WHERE u.email = ?
            GROUP BY u.target_daily_calories
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordDate);
            pstmt.setString(2, email);
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int target = rs.getInt("target_daily_calories");
                    int current = rs.getInt("total_cal");
                    
                    result.put("targetCalories", target);
                    result.put("currentCalories", current);
                    result.put("carbs", rs.getDouble("total_carb"));
                    result.put("protein", rs.getDouble("total_pro"));
                    result.put("fat", rs.getDouble("total_fat"));
                    
                    String status = current > target ? "EXCEED" : (current >= target * 0.9 ? "ACHIEVED" : "UNDER");
                    result.put("status", status); 
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    // 기간별 주간/월간 통계 (DB SELECT: record_date 기준 total_calories, total_carbs, total_protein, total_fat 합계 및 탄단지 비율 산출)
    public java.util.List<java.util.Map<String, Object>> getPeriodStats(String email, String startDate, String endDate) {
        var list = new java.util.ArrayList<java.util.Map<String, Object>>();
        String sql = """
            SELECT 
                r.record_date, 
                SUM(r.total_calories) as daily_cal,
                SUM(r.total_carbs) as carbs,
                SUM(r.total_protein) as protein,
                SUM(r.total_fat) as fat
            FROM USER_DIET_RECORDS r 
            JOIN USERS u ON r.user_id = u.user_id
            WHERE u.email = ? AND r.record_date BETWEEN ? AND ?
            GROUP BY r.record_date
            ORDER BY r.record_date
            """;
            
        try (var conn = DBConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setString(2, startDate);
            pstmt.setString(3, endDate);
            try (var rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    var data = new java.util.HashMap<String, Object>();
                    data.put("date", rs.getString("record_date"));
                    data.put("calories", rs.getInt("daily_cal"));
                    
                    double c = rs.getDouble("carbs");
                    double p = rs.getDouble("protein");
                    double f = rs.getDouble("fat");
                    double totalMacro = c + p + f;
                    
                    if (totalMacro > 0) {
                        data.put("carbRatio", Math.round((c / totalMacro) * 100));
                        data.put("proteinRatio", Math.round((p / totalMacro) * 100));
                        data.put("fatRatio", Math.round((f / totalMacro) * 100));
                    }
                    list.add(data);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }
}