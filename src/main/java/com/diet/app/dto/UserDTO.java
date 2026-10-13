package com.diet.app.dto;

import java.util.Set;
import java.util.regex.Pattern;

// 사용자 개인정보 및 목표 데이터 전달 객체
// DB 저장 컬럼(users): user_id, email, password, name, gender, age, height_cm, weight_kg, securityQuestion, target_daily_calories
//  - preference      : user_preferences.category 로 저장
//  - securityAnswer  : securityQuestion 컬럼에 해시로 함께 저장 (UserDAO 참고)
//  - actLevel, goal  : 목표 칼로리 계산에만 사용하며 DB 에는 저장하지 않음
public record UserDTO(
		Long userId,
		String email,
		String password,
		String name,
		String gender,
		int age,
		double heightCm,
		double weightKg,
		String actLevel,
		String goal,
		String preference,
		String securityQuestion,
		String securityAnswer,
		int targetDailyCalories
) {
	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private static final Set<String> GENDERS =
			Set.of("남", "여", "기타", "MALE", "FEMALE", "M", "F", "OTHER");

	public boolean isValid() {
		return email != null
				&& EMAIL_PATTERN.matcher(email.trim()).matches()
				&& isValidPassword(password)
				&& name != null
				&& !name.isBlank()
				&& name.trim().length() <= 100
				&& gender != null
				&& GENDERS.contains(gender.trim().toUpperCase())
				&& age >= 0
				&& age <= 120
				&& Double.isFinite(heightCm)
				&& heightCm > 0
				&& heightCm <= 300
				&& Double.isFinite(weightKg)
				&& weightKg > 0
				&& weightKg <= 500
				&& securityQuestion != null
				&& !securityQuestion.isBlank()
				&& securityAnswer != null
				&& !securityAnswer.isBlank();
	}

	public static boolean isValidPassword(String password) {
		return password != null
				&& password.length() >= 8
				&& password.length() <= 128;
	}
}
