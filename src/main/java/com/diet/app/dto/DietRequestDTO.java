package com.diet.app.dto;

// 클라이언트의 식단 등록/수정 요청 데이터를 담는 객체 (DB 연관 컬럼: record_date, meal_type, food_id, portion)
public record DietRequestDTO(
    String recordDate, // "2026-10-15" 형식
    String mealType,   // "아침", "점심", "저녁"
    Long foodId,       // 선택한 공용 식품 ID
    double portion     // 0.5, 1.0, 2.0 등
) {}