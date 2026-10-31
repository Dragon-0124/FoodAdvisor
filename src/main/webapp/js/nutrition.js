/*
 * 영양정보 기준 계산 - 대시보드 영양정보 카드와 nutrition-detail.html 에서 함께 사용합니다.
 *
 * 근거: 보건복지부·한국영양학회, 2025 한국인 영양소 섭취기준 (성인 에너지적정비율)
 *  - 탄수화물 50~65%, 단백질 10~20%, 지방 15~30% (총 에너지 대비)
 *  - 총당류 20% 이내 (하한 없음)
 * 하루 목표 칼로리(users.target_daily_calories)에 비율을 곱해 g 으로 환산합니다. (탄수화물·단백질·당 4kcal/g, 지방 9kcal/g)
 */
(() => {
  const NUTRIENTS = {
    carbs:   { label: '탄수화물', kcalPerGram: 4, minPct: 50, maxPct: 65 },
    protein: { label: '단백질',   kcalPerGram: 4, minPct: 10, maxPct: 20 },
    fat:     { label: '지방',     kcalPerGram: 9, minPct: 15, maxPct: 30 },
    sugar:   { label: '당류',     kcalPerGram: 4, minPct: 0,  maxPct: 20 }
  };

  // 목표 칼로리 기준 적정 범위(g)
  function rangeGrams(key, targetCalories) {
    const n = NUTRIENTS[key];
    const toGrams = pct => targetCalories * pct / 100 / n.kcalPerGram;
    return { min: toGrams(n.minPct), max: toGrams(n.maxPct) };
  }

  // 섭취 상태: low(부족) / ok(적정) / high(과잉), 기록이 없으면 null
  function status(key, grams, targetCalories) {
    if (!targetCalories || !(grams > 0)) return null;
    const range = rangeGrams(key, targetCalories);
    if (grams < range.min) return 'low';
    if (grams > range.max) return 'high';
    return 'ok';
  }

  // 탄·단·지가 섭취 열량에서 차지하는 비율(%) - 세 값의 합이 100 이 되도록 탄·단·지 열량끼리 비교합니다.
  function macroShares(summary) {
    const kcal = {
      carbs: summary.carbs * NUTRIENTS.carbs.kcalPerGram,
      protein: summary.protein * NUTRIENTS.protein.kcalPerGram,
      fat: summary.fat * NUTRIENTS.fat.kcalPerGram
    };
    const total = kcal.carbs + kcal.protein + kcal.fat;
    if (total <= 0) return null;
    return {
      carbs: kcal.carbs / total * 100,
      protein: kcal.protein / total * 100,
      fat: kcal.fat / total * 100
    };
  }

  const pctText = key => {
    const n = NUTRIENTS[key];
    return n.minPct > 0 ? `${n.minPct}~${n.maxPct}%` : `${n.maxPct}% 이내`;
  };

  window.FoodAdvisorNutrition = { NUTRIENTS, rangeGrams, status, macroShares, pctText };
})();
