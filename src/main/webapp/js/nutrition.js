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

  // ---------- 미량 영양소 (2025 한국인 영양소 섭취기준 요약표, 성별·연령별) ----------
  // [시작 나이, 식이섬유 충분, 비타민A 권장, 비타민A 상한, 비타민C 권장, 비타민C 상한, 칼슘 권장, 칼슘 상한,
  //  나트륨 충분, 나트륨 만성질환위험감소, 칼륨 충분, 철 권장, 철 상한]  (19세 미만은 19-29세 값 사용)
  const MICRO_TABLE = {
    male: [
      [19, 30, 800, 3000, 100, 2000, 800, 3000, 1500, 2300, 3500, 8, 45],
      [30, 30, 800, 3000, 100, 2000, 800, 2500, 1500, 2300, 3500, 8, 45],
      [50, 30, 750, 3000, 100, 2000, 800, 2500, 1500, 2300, 3500, 8, 45],
      [65, 30, 700, 3000, 100, 2000, 800, 2500, 1300, 1900, 3500, 8, 45],
      [75, 30, 700, 3000, 100, 2000, 800, 2500, 1200, 1800, 3500, 7, 45]
    ],
    female: [
      [19, 20, 650, 3000, 100, 2000, 650, 2500, 1500, 2300, 3500, 12, 45],
      [30, 20, 650, 3000, 100, 2000, 650, 2000, 1500, 2300, 3500, 12, 45],
      [50, 25, 600, 3000, 100, 2000, 750, 2000, 1500, 2300, 3500, 7, 45],
      [65, 25, 600, 3000, 100, 2000, 750, 2000, 1300, 1900, 3500, 6, 45],
      [75, 25, 600, 3000, 100, 2000, 750, 2000, 1200, 1800, 3500, 6, 45]
    ]
  };
  const SAT_FAT_MAX_PCT = 7;  // 19세 이상 포화지방산 에너지 7% 미만

  // 미량 영양소 정의: unit, 기준 종류 이름
  const MICROS = {
    fiber:     { label: '식이섬유', unit: 'g',   basis: '충분섭취량' },
    satFat:    { label: '포화 지방', unit: 'g',  basis: '에너지 7% 미만' },
    sodium:    { label: '나트륨',   unit: 'mg',  basis: '충분섭취량 · 만성질환위험감소섭취량' },
    potassium: { label: '칼륨',     unit: 'mg',  basis: '충분섭취량' },
    calcium:   { label: '칼슘',     unit: 'mg',  basis: '권장섭취량 · 상한섭취량' },
    iron:      { label: '철분',     unit: 'mg',  basis: '권장섭취량 · 상한섭취량' },
    vitaminA:  { label: '비타민 A', unit: 'μg RAE', basis: '권장섭취량 · 상한섭취량' },
    vitaminC:  { label: '비타민 C', unit: 'mg',  basis: '권장섭취량 · 상한섭취량' }
  };

  const isMale = gender => ['남', 'MALE', 'M'].includes(String(gender || '').trim().toUpperCase());

  // 하루 기준 범위 { min, max } (없으면 null). days 를 주면 기간 기준으로 곱합니다.
  function microRange(key, profile, targetCalories, days = 1) {
    const rows = MICRO_TABLE[isMale(profile && profile.gender) ? 'male' : 'female'];
    const age = Number(profile && profile.age) || 19;
    const row = rows.filter(r => age >= r[0]).pop() || rows[0];
    const [, fiber, vitA, vitAUl, vitC, vitCUl, ca, caUl, na, naCdrr, k, fe, feUl] = row;
    const base = {
      fiber: { min: fiber, max: null },
      satFat: { min: null, max: targetCalories ? targetCalories / days * SAT_FAT_MAX_PCT / 100 / 9 : null },
      sodium: { min: na, max: naCdrr },
      potassium: { min: k, max: null },
      calcium: { min: ca, max: caUl },
      iron: { min: fe, max: feUl },
      vitaminA: { min: vitA, max: vitAUl },
      vitaminC: { min: vitC, max: vitCUl }
    }[key];
    const scale = value => value == null ? null : value * days;
    return { min: scale(base.min), max: scale(base.max) };
  }

  function microStatus(value, range) {
    if (value == null || !(value > 0)) return null;
    if (range.min != null && value < range.min) return 'low';
    if (range.max != null && value > range.max) return 'high';
    return 'ok';
  }

  window.FoodAdvisorNutrition = { NUTRIENTS, rangeGrams, status, macroShares, pctText, MICROS, microRange, microStatus };
})();
