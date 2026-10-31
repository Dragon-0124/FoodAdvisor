/*
 * FoodAdvisor data boundary - 서버 API(DB)와 화면 사이의 변환 계층입니다.
 * 이전의 localStorage 목업을 제거하고 실제 Servlet API 를 호출합니다. (js/api.js 필요)
 *
 *  식단   : GET  api/diets/records?date=        POST api/diets/record | delete
 *           GET  api/diets/stats?startDate&endDate (주간 그래프)
 *  신체   : GET  api/body-metrics               POST api/body-metrics/save | delete | clear
 *           GET  api/body-metrics/forecast?days= (현재 패턴 유지 시 체성분 예측)
 *  운동   : GET  api/exercises?startDate&endDate  GET api/exercises/types   POST api/exercises/save | delete
 */
(() => {
  const api = window.FoodAdvisorApi;

  // 서버는 섭취량을 quantity(곱하는 배수)로 저장하고 칼로리를 quantity * foods.avg_calories 로 계산합니다.
  // 화면은 avg_calories 를 100g당 kcal 로 다루므로 quantity = 섭취 g / 100 입니다.
  // (foods.avg_calories 가 '1인분당' 값이라면 이 상수와 gramsToQuantity 만 바꾸면 됩니다.)
  const GRAMS_PER_UNIT = 100;

  const gramsToQuantity = grams => Math.round(grams / GRAMS_PER_UNIT * 1000) / 1000;
  const dateOnly = value => String(value || '').slice(0, 10);
  const numberOrNull = value => (value === null || value === undefined || value === '' || !Number.isFinite(Number(value))) ? null : Number(value);

  // ---------- 식단 ----------
  // 선택한 날짜의 식단 기록 -> [{ id, date, meal, name, calories, foodId, quantity }]
  async function getMeals(date) {
    const rows = await api.get('diets/records?date=' + encodeURIComponent(date));
    return (Array.isArray(rows) ? rows : []).map(row => ({
      id: row.dietId,
      date: dateOnly(row.recordDate),
      meal: row.mealType,
      name: row.foodName,
      calories: Number(row.calories) || 0,
      foodId: row.foodId,
      quantity: Number(row.portion) || 0
    }));
  }

  // 식단 1건 저장 (foodId: 음식 검색 결과의 id, grams: 먹은 양(g))
  function addMeal({ date, meal, foodId, grams }) {
    return api.post('diets/record', {
      recordDate: date,
      mealType: meal,
      foodId: Number(foodId),
      portion: gramsToQuantity(Number(grams))
    });
  }

  function deleteMeal(id) {
    return api.post('diets/delete', { dietId: Number(id) });
  }

  // 기간별 날짜 총 섭취 열량 -> { 'YYYY-MM-DD': kcal }
  async function getDailyCalories(startDate, endDate) {
    const rows = await api.get('diets/stats?startDate=' + encodeURIComponent(startDate) + '&endDate=' + encodeURIComponent(endDate));
    const result = {};
    (Array.isArray(rows) ? rows : []).forEach(row => { result[dateOnly(row.date)] = Number(row.calories) || 0; });
    return result;
  }

  // 하루 영양 합계 -> { targetCalories, currentCalories, carbs, protein, fat, sugar } (g)
  async function getDailyNutrition(date) {
    const row = await api.get('diets/daily?date=' + encodeURIComponent(date)) || {};
    return {
      targetCalories: Number(row.targetCalories) || 0,
      currentCalories: Number(row.currentCalories) || 0,
      carbs: Number(row.carbs) || 0,
      protein: Number(row.protein) || 0,
      fat: Number(row.fat) || 0,
      sugar: Number(row.sugar) || 0
    };
  }

  // ---------- 신체 기록 ----------
  // -> [{ date, weight, muscle, bodyFat }] (없는 항목은 null, 날짜 오름차순)
  async function getBodyMetrics() {
    const rows = await api.get('body-metrics');
    return (Array.isArray(rows) ? rows : [])
      .map(row => ({
        date: dateOnly(row.recordDate),
        weight: numberOrNull(row.weightKg),
        muscle: numberOrNull(row.muscleKg),
        bodyFat: numberOrNull(row.bodyFatPct)
      }))
      .sort((a, b) => a.date.localeCompare(b.date));
  }

  // null 로 보낸 항목은 서버에서 기존 값을 유지합니다.
  function saveBodyMetric({ date, weight, muscle, bodyFat }) {
    return api.post('body-metrics/save', {
      recordDate: date,
      weightKg: weight,
      muscleKg: muscle,
      bodyFatPct: bodyFat
    });
  }

  function deleteBodyMetric(date) {
    return api.post('body-metrics/delete', { recordDate: date });
  }

  function clearBodyMetrics() {
    return api.post('body-metrics/clear');
  }

  // ---------- 운동 기록 ----------
  // -> [{ id, date, type, label, intensity, minutes, calories }] (날짜 내림차순)
  async function getExercises(startDate, endDate) {
    const query = startDate && endDate
      ? '?startDate=' + encodeURIComponent(startDate) + '&endDate=' + encodeURIComponent(endDate) : '';
    const rows = await api.get('exercises' + query);
    return (Array.isArray(rows) ? rows : []).map(row => ({
      id: row.exerciseId,
      date: dateOnly(row.date),
      type: row.type,
      label: row.label,
      intensity: row.intensity,
      minutes: Number(row.minutes) || 0,
      calories: Number(row.calories) || 0
    }));
  }

  // 운동 종류와 강도별 MET -> [{ type, label, met: { LOW, MODERATE, HIGH } }]
  const getExerciseTypes = () => api.get('exercises/types');

  // 소모 열량은 서버가 MET × 체중 × 시간으로 계산합니다. -> { calories }
  function addExercise({ date, type, intensity, minutes }) {
    return api.post('exercises/save', { date, type, intensity, minutes: Number(minutes) });
  }

  function deleteExercise(id) {
    return api.post('exercises/delete', { exerciseId: Number(id) });
  }

  // ---------- 체성분 예측 ----------
  // 현재 식습관·운동량이 유지될 때의 예측 -> { available, reason?, assumptions, points: [{ date, weight, weightLow, weightHigh, bodyFat, muscle }] }
  const getBodyForecast = (days = 60) => api.get('body-metrics/forecast?days=' + encodeURIComponent(days));

  window.FoodAdvisorData = {
    mode: 'api',
    getMeals, addMeal, deleteMeal, getDailyCalories, getDailyNutrition,
    getBodyMetrics, saveBodyMetric, deleteBodyMetric, clearBodyMetrics,
    getExercises, getExerciseTypes, addExercise, deleteExercise,
    getBodyForecast
  };
})();
