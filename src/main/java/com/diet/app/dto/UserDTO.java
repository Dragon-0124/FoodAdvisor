package com.diet.app.dto;

import java.util.Set;
import java.util.regex.Pattern;

// 사용자 개인정보 및 목표 데이터 전달 객체
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
		String preferance,
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
				&& weightKg <= 500;
	}

	public static boolean isValidPassword(String password) {
		return password != null
				&& password.length() >= 8
				&& password.length() <= 128;
	}
}