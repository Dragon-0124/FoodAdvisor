package com.diet.app.dto;

public record FoodDTO(
    Long foodId,
    String name,
    int avgCalories,
    double carbsG,
    double proteinG,
    double fatG
)
{
    public FoodDTO calculateProportion(double quantity) {
        return new FoodDTO(
            this.foodId,
            this.name,
            (int) (this.avgCalories * quantity),
            this.carbsG * quantity,
            this.proteinG * quantity,
            this.fatG * quantity
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

	public double carbsG() {
		return carbsG;
	}

	public double proteinG() {
		return proteinG;
	}

	public double fatG() {
		return fatG;
	}
}