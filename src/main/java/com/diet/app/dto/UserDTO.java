package com.diet.app.dto;

// 사용자 개인정보 및 목표 데이터 객체 (DB 연관 컬럼: user_id, email, password, name, gender, age, height_cm, weight_kg, activity_level, goal, preferance, target_daily_calories)
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
    // 회원가입 입력값 유효성 검증
    public boolean isValid() {
        return email != null && email.contains("@") &&
               password != null && password.length() >= 4 &&
               age >= 0 && weightKg > 0 && heightCm > 0;
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

	public double heightCm() {
		return heightCm;
	}
	
	public double weightKg() {
		return weightKg;
	}

	public String actLevel() {
		return actLevel;
	}
	
	public String goal() {
		return goal;
	}
	
	public String preferance() {
		return preferance;
	}
	
	public int targetDailyCalories() {
		return targetDailyCalories;
	}
}