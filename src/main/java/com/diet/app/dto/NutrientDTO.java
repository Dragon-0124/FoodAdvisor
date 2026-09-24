package com.diet.app.dto;

// 일일 영양소 섭취량 합계 및 잔여 영양소 데이터 객체 (DB 연관 컬럼: total_calories, total_carbs, total_protein, total_fat, total_sugar)
public record NutrientDTO(
    int calories,
    double carbs,
    double protein,
    double fat,
    double sugar
) {
}