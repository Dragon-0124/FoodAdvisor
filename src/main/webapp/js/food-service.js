/*
 * FoodAdvisor food lookup adapter.
 * 실제 서버의 FoodServlet(GET api/foods?keyword=검색어)을 호출합니다.
 *
 * 서버 응답(FoodDTO): { foodId, name, avgCalories, carbs_g, protein_g, fat_g, sugar_g }
 * 화면에서 쓰는 정규화 형태: { id, name, kcalPer100g, carbs, protein, fat, sugar }
 *
 * - 경로는 컨텍스트 경로(/FoodAdvisor 등)와 무관하게 동작하도록 상대경로(api/foods)를 사용합니다.
 * - avgCalories를 100g당 kcal로 해석합니다. DB의 avg_calories 기준 단위(100g당/1인분당)가
 *   다르다면 normalizeFood()의 kcalPer100g 계산만 수정하면 됩니다.
 */
(() => {
  const CONFIG = {
    mode: "api",
    apiBase: "api/foods",
    searchPath: "?keyword="
  };

  function toNumberOrNaN(value) {
    return value === null || value === undefined || value === "" ? NaN : Number(value);
  }

  function normalizeFood(row) {
    const per100 = row.kcalPer100g ?? row.caloriesPer100g ?? row.avgCalories ?? null;
    return {
      id: String(row.id ?? row.foodId ?? row.name ?? ""),
      name: String(row.name ?? row.foodName ?? "").trim(),
      kcalPer100g: toNumberOrNaN(per100),
      carbs: toNumberOrNaN(row.carbs_g),
      protein: toNumberOrNaN(row.protein_g),
      fat: toNumberOrNaN(row.fat_g),
      sugar: toNumberOrNaN(row.sugar_g)
    };
  }

  async function searchFoods(query) {
    const q = String(query || "").trim();
    if (!q || CONFIG.mode !== "api") return [];

    const response = await fetch(CONFIG.apiBase + CONFIG.searchPath + encodeURIComponent(q), {
      headers: { Accept: "application/json" }
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error((data && data.error) || "음식 검색 API 응답 오류: " + response.status);
    }
    const rows = Array.isArray(data) ? data : ((data && (data.foods || data.results)) || []);
    return rows.map(normalizeFood).filter(food =>
      food.id && food.name && Number.isFinite(food.kcalPer100g) && food.kcalPer100g >= 0
    ).slice(0, 8);
  }

  window.FoodService = { searchFoods, config: CONFIG };
})();
