/* FoodAdvisor data boundary
 * Current mode: localStorage mock data only. No database/API calls are made here.
 * Once the teammate shares API endpoints and JSON shapes, replace these methods with fetch().
 */
(() => {
  const PREFIX = 'foodAdvisor.demo.';
  const parse = (key, fallback) => { try { return JSON.parse(localStorage.getItem(PREFIX + key)) ?? fallback; } catch { return fallback; } };
  const save = (key, value) => localStorage.setItem(PREFIX + key, JSON.stringify(value));
  window.FoodAdvisorData = {
    mode: 'mock',
    getMeals: date => parse('meals.' + date, []),
    saveMeals: (date, meals) => save('meals.' + date, meals),
    getWeights: () => parse('weights', []),
    saveWeights: rows => save('weights', rows),
    // Planned real API replacements (endpoint names are placeholders, not confirmed):
    // getMeals(date) -> GET /api/meals?date=YYYY-MM-DD
    // saveMeals(date, meals) -> POST/PUT/DELETE /api/meals
    // getWeights() -> GET /api/weights; saveWeights(rows) -> POST /api/weights
  };
})();
