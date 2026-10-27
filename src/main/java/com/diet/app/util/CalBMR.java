package com.diet.app.util;

import com.diet.app.dto.UserDTO;

// 기초대사량(BMR) 및 활동량 기반 목표 칼로리 계산 유틸리티 (관련 데이터: weight_kg, height_cm, age, gender, activity_level, goal, target_daily_calories)
public class CalBMR {

    // 활동 강도 수준(activity_level)에 따른 승수 반환 (체성분 예측 BodyForecast 에서도 사용)
    //  기초대사량에 곱하는 널리 쓰이는 관례값이며, 이 값들을 직접 제시한 1차 연구는 없습니다.
    public static double getActivityMultiplier(String actLevel) {
        return switch (normalizeActivityLevel(actLevel)) {
            case "SEDENTARY" -> 1.2;
            case "MODERATELY_ACTIVE" -> 1.55;
            case "VERY_ACTIVE" -> 1.725;
            case "EXTRA_ACTIVE" -> 1.9;
            default -> 1.375; // LIGHTLY_ACTIVE
        };
    }

    // 알 수 없는 값이나 미입력은 '가벼움(LIGHTLY_ACTIVE)'으로 저장/계산합니다.
    public static String normalizeActivityLevel(String actLevel) {
        String value = actLevel == null ? "" : actLevel.trim().toUpperCase();
        return switch (value) {
            case "SEDENTARY", "LIGHTLY_ACTIVE", "MODERATELY_ACTIVE", "VERY_ACTIVE", "EXTRA_ACTIVE" -> value;
            default -> "LIGHTLY_ACTIVE";
        };
    }

    // 감량: 하루 500kcal 부족 (2013 AHA/ACC/TOS 가이드라인의 500 또는 750kcal/일 부족 중 완만한 쪽)
    private static final int WEIGHT_LOSS_DEFICIT = 500;
    // 최소 섭취 열량: 2013 AHA/ACC/TOS 가이드라인의 감량 식단 하한 (여성 1,200 / 남성 1,500kcal)
    private static final int MIN_CALORIES_FEMALE = 1200;
    private static final int MIN_CALORIES_MALE = 1500;

    public static boolean isMale(String gender) {
        return gender != null && switch (gender.trim().toUpperCase()) {
            case "남", "MALE", "M" -> true;
            default -> false;
        };
    }

    // 기초대사량 - Mifflin-St Jeor (Mifflin et al., 1990). 체성분 예측(BodyForecast)에서도 사용합니다.
    public static double restingKcal(boolean male, double weightKg, double heightCm, int age) {
        double base = (10 * weightKg) + (6.25 * heightCm) - (5 * age);
        return male ? base + 5 : base - 161;
    }

    // 사용자 목표(goal)에 따른 칼로리 가감치 반환
    //  근육량 증가는 '얼마나 더 먹어야 하는지'에 대한 신뢰도 높은 근거(메타분석·가이드라인)가 없어 유지 열량으로 둡니다.
    //  근육 증가의 근거가 확실한 것은 근력 운동 + 단백질 섭취입니다. (Morton et al., 2018; Nunes et al., 2022)
    private static int getGoalAdjustment(String goal) {
        return switch (goal != null ? goal : "체중유지") {
            case "체중감량" -> -WEIGHT_LOSS_DEFICIT;
            default -> 0;  // 체중유지, 근육량증가
        };
    }

    // 기초대사량 × 활동 계수(TDEE) + 목표 보정 → 최종 목표 칼로리
    public static int calculateTargetCalories(UserDTO user) {
        // 1. 기초대사량(BMR) 계산
        boolean male = isMale(user.gender());
        double bmr = restingKcal(male, user.weightKg(), user.heightCm(), user.age());

        // 2. 활동량 지수 반영 (TDEE = 유지 열량)
        double tdee = bmr * getActivityMultiplier(user.actLevel());

        // 3. 사용자의 목표(감량/유지/증가) 반영 및 반올림
        int finalCalories = (int) Math.round(tdee) + getGoalAdjustment(user.goal());

        // 최소 섭취 열량 방어 (성별 기준)
        return Math.max(finalCalories, male ? MIN_CALORIES_MALE : MIN_CALORIES_FEMALE);
    }
}