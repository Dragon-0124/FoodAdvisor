package com.diet.app.dto;

public record UserDTO(
    Long userId,
    String email,
    String password,
    String name,
    String gender,
    int age,
    double weightKg,
    int targetDailyCalories
) {
    // 데이터 유효성 검증
    public boolean isValid() {
        return email != null && email.contains("@") &&
               password != null && password.length() >= 4 &&
               age >= 0 &&
               weightKg > 0;
    }

	public Long userId() {
		return userId;
	}

	public String email() {
		return email;
	}

	public String password() {
		return password;
	}

	public String name() {
		return name;
	}

	public String gender() {
		return gender;
	}

	public int age() {
		return age;
	}

	public double weightKg() {
		return weightKg;
	}

	public int targetDailyCalories() {
		return targetDailyCalories;
	}
}