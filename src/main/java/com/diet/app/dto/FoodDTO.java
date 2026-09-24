package com.diet.app.dto;

// 식품 정보 및 영양소 비례 계산 객체 (DB 연관 컬럼: food_id, name, avg_calories, carbs_g, protein_g, fat_g, sugar_g)
public record FoodDTO(
    Long foodId,
    String name,
    int avgCalories,
    double carbs_g,
    double protein_g,
    double fat_g,
    double sugar_g
)
{
    // 섭취량(quantity)에 따른 칼로리 및 영양소 비례 계산
    public FoodDTO calculateProportion(double quantity) {
        return new FoodDTO(
            this.foodId,
            this.name,
            (int) (this.avgCalories * quantity),
            this.carbs_g * quantity,
            this.protein_g * quantity,
            this.fat_g * quantity,
            this.sugar_g * quantity
        );
    }

	public Long foodId() {
		return foodId;
	}

	public String name() {
		return name;
	}

	public int avgCalories() {
		return avgCalories;
	}

	public double carbs_g() {
		return carbs_g;
	}

	public double protein_g() {
		return protein_g;
	}

	public double fat_g() {
		return fat_g;
	}

	public double sugar_g() {
		return sugar_g;
	}

}