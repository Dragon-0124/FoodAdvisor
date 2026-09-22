package com.diet.app.util;

import com.diet.app.dto.UserDTO;

public class CalBMR {

    private static double getActivityMultiplier(String activityLevel) {
        return switch (activityLevel != null ? activityLevel.toUpperCase() : "SEDENTARY") {
            case "SEDENTARY" -> 1.2;
            case "MODERATELY_ACTIVE" -> 1.55;
            case "VERY_ACTIVE" -> 1.725;
            case "EXTRA_ACTIVE" -> 1.9;
            default -> 1.375; // LIGHTLY_ACTIVE
        };
    }

    private static int getGoalAdjustment(String goal) {
        return switch (goal != null ? goal : "체중유지") {
            case "체중감량" -> -500;
            case "근육량증가" -> 300;
            default -> 0;
        };
    }

    public static int calculateTargetCalories(UserDTO user) {
        // 1. 기초대사량(BMR) 계산
        double bmrBase = (10 * user.weightKg()) + (6.25 * user.heightCm()) - (5 * user.age());
        
        double bmr = switch (user.gender()) {
            case "남", "MALE", "M" -> bmrBase + 5;
            default -> bmrBase - 161;
        };

        // 2. 활동량 지수 반영 (TDEE)
        double tdee = bmr * getActivityMultiplier(user.actLevel());

        // 3. 사용자의 목표(감량/유지/증가) 반영 및 반올림
        int finalCalories = (int) Math.round(tdee) + getGoalAdjustment(user.goal());

        // 최소 권장 섭취량(안전 기준 1200kcal) 방어
        return Math.max(finalCalories, 1200);
    }
}