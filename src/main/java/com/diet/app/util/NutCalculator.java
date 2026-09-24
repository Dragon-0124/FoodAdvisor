package com.diet.app.util;

import com.diet.app.dto.FoodDTO;
import com.diet.app.dto.NutrientDTO;

// 영양소 섭취량 계산 및 BMI 측정 유틸리티 클래스
public class NutCalculator {

    // 섭취 수량에 따른 칼로리 및 탄단지/당류 비례 계산 (DB 연관 컬럼: avg_calories, carbs_g, protein_g, fat_g, sugar_g -> total_calories, total_carbs, total_protein, total_fat, total_sugar)
    public static NutrientDTO calculateIntake(FoodDTO food, double quantity) {
        return new NutrientDTO(
            (int) (food.avgCalories() * quantity),
            food.carbs_g() * quantity,
            food.protein_g() * quantity,
            food.fat_g() * quantity,
            food.sugar_g() * quantity
        );
    }

    // 신장 및 체중 기반 BMI 지수 산출 (관련 데이터: weight_kg, height_cm)
    public static double calculateBMI(double weightKg, double heightCm) {
        double heightM = heightCm / 100.0;
        return Math.round((weightKg / (heightM * heightM)) * 10.0) / 10.0;
    }
}