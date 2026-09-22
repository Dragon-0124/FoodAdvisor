package com.diet.app.dto;

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