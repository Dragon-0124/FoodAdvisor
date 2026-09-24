package com.diet.app.dto;

import java.time.LocalDateTime;

// 식단 기록 응답 및 전달용 데이터 객체 (DB 연관 컬럼: record_id, user_id, food_id, portion, record_date)
public record DietDTO(
    Long dietId,
    Long userId,
    Long foodId,
    double quantity,
    LocalDateTime consumedAt
) {
}