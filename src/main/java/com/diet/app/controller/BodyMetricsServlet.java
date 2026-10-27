package com.diet.app.controller;

import com.diet.app.dao.BodyMetricsDAO;
import com.diet.app.dao.DietDAO;
import com.diet.app.dao.ExerciseDAO;
import com.diet.app.dao.UserDAO;
import com.diet.app.dto.UserDTO;
import com.diet.app.util.BodyForecast;
import com.diet.app.util.CalBMR;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// 신체 기록(체중/골격근량/체지방률) API 컨트롤러 - 로그인한 본인의 기록만 다룹니다.
//  GET  /api/body-metrics           전체 기록 (날짜 오름차순) [{recordDate, weightKg, muscleKg, bodyFatPct}]
//  GET  /api/body-metrics/forecast?days=60
//                                   현재 식습관·운동량 유지 시 예측 {available, reason?, baselineDate, assumptions, points:[{date, weight, weightLow, weightHigh, bodyFat, muscle}]}
//  POST /api/body-metrics/save      기록 저장/갱신 {recordDate, weightKg?, muscleKg?, bodyFatPct?}
//                                   (생략하거나 null 인 항목은 기존 값 유지, 최소 1개 필요)
//  POST /api/body-metrics/delete    특정 날짜 기록 삭제 {recordDate}
//  POST /api/body-metrics/clear     본인 기록 전체 삭제
@SuppressWarnings("serial")
@WebServlet("/api/body-metrics/*")
public class BodyMetricsServlet extends HttpServlet {
    private static final double WEIGHT_MAX = 500;
    private static final double MUSCLE_MAX = 200;
    private static final double BODY_FAT_MAX = 75;

    private static final int FORECAST_DEFAULT_DAYS = 60;
    private static final int FORECAST_MAX_DAYS = 180;
    private static final int FORECAST_MIN_LOGGED_DAYS = 5;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BodyMetricsDAO bodyMetricsDAO = new BodyMetricsDAO();
    private final UserDAO userDAO = new UserDAO();
    private final DietDAO dietDAO = new DietDAO();
    private final ExerciseDAO exerciseDAO = new ExerciseDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepare(req, resp);
        String email = authenticate(req, resp);
        if (email == null) return;

        String pathInfo = req.getPathInfo();
        if ("/forecast".equals(pathInfo)) {
            handleForecast(email, req, resp);
            return;
        }
        if (pathInfo != null && !"/".equals(pathInfo)) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
            return;
        }

        try {
            resp.getWriter().write(gson.toJson(bodyMetricsDAO.getRecords(email)));
        } catch (SQLException e) {
            fail(resp, "신체 기록을 불러오지 못했습니다.", e);
        }
    }

    // ---------- 예측: 현재 식습관·운동량이 유지될 때 (BodyForecast 참고) ----------
    //  - 식단: 최근 14일 중 기록한 날의 평균 (5일 이상 필요)
    //  - 운동: 최근 14일 운동 소모 열량의 하루 평균
    //  - 보정: 최근 28일 체중 추세가 14일 이상이면 실측으로 소비 열량을 보정
    private void handleForecast(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        int days = FORECAST_DEFAULT_DAYS;
        try {
            if (req.getParameter("days") != null) days = Integer.parseInt(req.getParameter("days"));
        } catch (NumberFormatException e) {
            // 기본값 사용
        }
        days = Math.max(7, Math.min(FORECAST_MAX_DAYS, days));

        UserDTO user = userDAO.getUserByEmail(email);
        if (user == null) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
            return;
        }

        LocalDate today = LocalDate.now(SEOUL);
        try {
            List<Map<String, Object>> records = bodyMetricsDAO.getRecords(email);
            String baselineDate = null;
            Double weight = null, bodyFat = null, muscle = null;
            Map<LocalDate, Double> recentWeights = new TreeMap<>();
            for (Map<String, Object> record : records) {  // 날짜 오름차순 → 마지막 값이 최신
                LocalDate date = LocalDate.parse((String) record.get("recordDate"));
                if (record.get("weightKg") != null) {
                    weight = (Double) record.get("weightKg");
                    baselineDate = date.toString();
                    if (!date.isBefore(today.minusDays(27))) recentWeights.put(date, weight);
                }
                if (record.get("bodyFatPct") != null) bodyFat = (Double) record.get("bodyFatPct");
                if (record.get("muscleKg") != null) muscle = (Double) record.get("muscleKg");
            }
            if (weight == null) weight = user.weightKg();

            Map<String, double[]> intake = dietDAO.getDailyIntakeTotals(
                    email, today.minusDays(13).toString(), today.toString());
            if (intake.size() < FORECAST_MIN_LOGGED_DAYS) {
                Map<String, Object> unavailable = new LinkedHashMap<>();
                unavailable.put("available", false);
                unavailable.put("loggedDays", intake.size());
                unavailable.put("reason", "최근 14일 중 식단을 기록한 날이 " + FORECAST_MIN_LOGGED_DAYS
                        + "일 이상이어야 예측할 수 있어요. (현재 " + intake.size() + "일)");
                resp.getWriter().write(gson.toJson(unavailable));
                return;
            }
            double avgKcal = intake.values().stream().mapToDouble(v -> v[0]).average().orElse(0);
            double avgProtein = intake.values().stream().mapToDouble(v -> v[2]).average().orElse(0);

            List<Map<String, Object>> exercises = exerciseDAO.getRecords(
                    email, today.minusDays(13).toString(), today.toString());
            double exerciseKcalPerDay = exercises.stream()
                    .mapToInt(e -> (Integer) e.get("calories")).sum() / 14.0;
            boolean strength = exercises.stream().anyMatch(e -> "STRENGTH".equals(e.get("type")));

            boolean male = CalBMR.isMale(user.gender());
            BodyForecast.Input base = new BodyForecast.Input(
                    male, user.age(), user.heightCm(), weight, bodyFat, muscle,
                    CalBMR.getActivityMultiplier(user.actLevel()), !exercises.isEmpty(),
                    avgKcal, avgProtein, exerciseKcalPerDay, strength, 0, today, days);

            // 최근 28일 체중 추세로 소비 열량 보정 (같은 기간 식단 기록 7일 이상일 때)
            double calibration = 0;
            Map<String, double[]> intake28 = dietDAO.getDailyIntakeTotals(
                    email, today.minusDays(27).toString(), today.toString());
            if (intake28.size() >= 7) {
                double avgKcal28 = intake28.values().stream().mapToDouble(v -> v[0]).average().orElse(0);
                calibration = BodyForecast.calibration(recentWeights, avgKcal28,
                        BodyForecast.formulaTdee(base), BodyForecast.energyPerKg(base));
            }

            BodyForecast.Result result = BodyForecast.run(new BodyForecast.Input(
                    base.male(), base.age(), base.heightCm(), base.weightKg(), base.bodyFatPct(), base.muscleKg(),
                    base.activityMultiplier(), base.exerciseLogged(), base.avgIntakeKcal(), base.avgProteinG(),
                    base.avgExerciseKcalPerDay(), base.strengthTraining(), calibration, today, days));

            List<Map<String, Object>> points = new ArrayList<>();
            for (BodyForecast.Point point : result.points()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", point.date().toString());
                row.put("weight", point.weight());
                row.put("weightLow", point.weightLow());
                row.put("weightHigh", point.weightHigh());
                row.put("bodyFat", point.bodyFatPct());
                row.put("muscle", point.muscleKg());
                points.add(row);
            }

            Map<String, Object> assumptions = new LinkedHashMap<>();
            assumptions.put("loggedDays", intake.size());
            assumptions.put("avgIntakeKcal", Math.round(avgKcal));
            assumptions.put("avgProteinG", Math.round(avgProtein));
            assumptions.put("avgExerciseKcalPerDay", Math.round(exerciseKcalPerDay));
            assumptions.put("estimatedTdee", Math.round(result.estimatedTdee()));
            assumptions.put("calibrated", calibration != 0);
            assumptions.put("weightOnly", result.weightOnly());  // 체지방률 기록이 없어 체중만 예측
            assumptions.put("strengthTraining", strength);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("available", true);
            response.put("baselineDate", baselineDate);
            response.put("assumptions", assumptions);
            response.put("points", points);
            resp.getWriter().write(gson.toJson(response));
        } catch (SQLException e) {
            fail(resp, "예측을 계산하지 못했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepare(req, resp);
        String email = authenticate(req, resp);
        if (email == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
            return;
        }

        switch (pathInfo) {
            case "/save" -> handleSave(email, req, resp);
            case "/delete" -> handleDelete(email, req, resp);
            case "/clear" -> handleClear(email, resp);
            default -> writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
        }
    }

    // ---------- 저장/갱신 ----------
    private void handleSave(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        String recordDate = stringOrNull(body, "recordDate");
        final Double weightKg;
        final Double muscleKg;
        final Double bodyFatPct;
        try {
            weightKg = optionalNumber(body, "weightKg");
            muscleKg = optionalNumber(body, "muscleKg");
            bodyFatPct = optionalNumber(body, "bodyFatPct");
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "측정값은 숫자로 입력해 주세요.");
            return;
        }

        if (!isValidDate(recordDate)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "측정 날짜를 YYYY-MM-DD 형식으로 입력해 주세요.");
            return;
        }
        if (weightKg == null && muscleKg == null && bodyFatPct == null) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "체중, 골격근량, 체지방률 중 하나 이상 입력해 주세요.");
            return;
        }
        if (!inRange(weightKg, WEIGHT_MAX) || !inRange(muscleKg, MUSCLE_MAX)
                || !inRange(bodyFatPct, BODY_FAT_MAX)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "체중은 1~" + (int) WEIGHT_MAX + "kg, 골격근량은 1~" + (int) MUSCLE_MAX
                            + "kg, 체지방률은 1~" + (int) BODY_FAT_MAX + "% 범위로 입력해 주세요.");
            return;
        }

        try {
            if (bodyMetricsDAO.saveRecord(email, recordDate, weightKg, muscleKg, bodyFatPct)) {
                writeMessage(resp, "측정 기록을 저장했습니다.");
            } else {
                writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "측정 기록 저장에 실패했습니다.");
            }
        } catch (SQLException e) {
            fail(resp, "측정 기록 저장에 실패했습니다.", e);
        }
    }

    // ---------- 특정 날짜 삭제 ----------
    private void handleDelete(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        String recordDate = stringOrNull(body, "recordDate");
        if (!isValidDate(recordDate)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "삭제할 날짜를 YYYY-MM-DD 형식으로 입력해 주세요.");
            return;
        }

        try {
            if (bodyMetricsDAO.deleteRecord(email, recordDate)) {
                writeMessage(resp, "삭제되었습니다.");
            } else {
                writeError(resp, HttpServletResponse.SC_NOT_FOUND, "삭제할 기록이 없습니다.");
            }
        } catch (SQLException e) {
            fail(resp, "측정 기록 삭제에 실패했습니다.", e);
        }
    }

    // ---------- 전체 삭제 ----------
    private void handleClear(String email, HttpServletResponse resp) throws IOException {
        try {
            bodyMetricsDAO.deleteAll(email);
            writeMessage(resp, "모든 측정 기록을 삭제했습니다.");
        } catch (SQLException e) {
            fail(resp, "측정 기록 삭제에 실패했습니다.", e);
        }
    }

    // ---------- 공통 ----------
    private static void prepare(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    // 세션 검증: 로그인 상태면 이메일 반환, 아니면 401 응답 후 null
    private String authenticate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userEmail") == null) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요한 서비스입니다.");
            return null;
        }
        return (String) session.getAttribute("userEmail");
    }

    private JsonObject readBody(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        try {
            JsonElement parsed = JsonParser.parseReader(req.getReader());
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (JsonParseException | IllegalStateException e) {
            // 아래에서 400 처리
        }
        writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "요청 JSON 형식이 올바르지 않습니다.");
        return null;
    }

    private static boolean isValidDate(String value) {
        if (value == null) return false;
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 항목이 없거나 null 이면 null, 숫자면 값, 그 외 타입이면 IllegalArgumentException
    private static Double optionalNumber(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            if (Double.isFinite(number)) return number;
        }
        throw new IllegalArgumentException(key + " must be a finite number");
    }

    private static boolean inRange(Double value, double max) {
        return value == null || (value >= 1 && value <= max);
    }

    private static String stringOrNull(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        return null;
    }

    private void fail(HttpServletResponse resp, String message, SQLException e) throws IOException {
        getServletContext().log(message, e);
        writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
    }

    private void writeMessage(HttpServletResponse resp, String message) throws IOException {
        resp.getWriter().write(gson.toJson(Map.of("message", message)));
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(gson.toJson(Map.of("error", message)));
    }
}
