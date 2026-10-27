package com.diet.app.controller;

import com.diet.app.dao.ExerciseDAO;
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
import java.util.Map;

// 운동 기록 API 컨트롤러 - 로그인한 본인의 기록만 다룹니다.
//  GET  /api/exercises?startDate=&endDate=   기간 기록 (생략 시 최근 60일) [{exerciseId, date, type, label, intensity, minutes, calories}]
//  GET  /api/exercises/types                 운동 종류와 강도별 MET [{type, label, met:{LOW, MODERATE, HIGH}}]
//  POST /api/exercises/save                  기록 추가 {date, type, intensity, minutes} -> {calories}
//  POST /api/exercises/delete                기록 삭제 {exerciseId}
@SuppressWarnings("serial")
@WebServlet("/api/exercises/*")
public class ExerciseServlet extends HttpServlet {
    private static final int MAX_MINUTES = 600;
    private static final int DEFAULT_RANGE_DAYS = 60;

    private final ExerciseDAO exerciseDAO = new ExerciseDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepare(req, resp);
        String email = authenticate(req, resp);
        if (email == null) return;

        String pathInfo = req.getPathInfo();
        if ("/types".equals(pathInfo)) {
            resp.getWriter().write(gson.toJson(ExerciseDAO.typeCatalog()));
            return;
        }
        if (pathInfo != null && !"/".equals(pathInfo)) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
            return;
        }

        LocalDate today = LocalDate.now();
        String startDate = req.getParameter("startDate");
        String endDate = req.getParameter("endDate");
        if (startDate == null && endDate == null) {
            startDate = today.minusDays(DEFAULT_RANGE_DAYS - 1).toString();
            endDate = today.toString();
        }
        if (!isValidDate(startDate) || !isValidDate(endDate)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "조회 기간을 YYYY-MM-DD 형식으로 입력해 주세요.");
            return;
        }

        try {
            resp.getWriter().write(gson.toJson(exerciseDAO.getRecords(email, startDate, endDate)));
        } catch (SQLException e) {
            fail(resp, "운동 기록을 불러오지 못했습니다.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepare(req, resp);
        String email = authenticate(req, resp);
        if (email == null) return;

        String pathInfo = req.getPathInfo();
        if ("/save".equals(pathInfo)) {
            handleSave(email, req, resp);
        } else if ("/delete".equals(pathInfo)) {
            handleDelete(email, req, resp);
        } else {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
        }
    }

    private void handleSave(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        String date = stringOrNull(body, "date");
        String type = stringOrNull(body, "type");
        String intensity = stringOrNull(body, "intensity");
        JsonElement minutesValue = body.get("minutes");
        int minutes = minutesValue != null && minutesValue.isJsonPrimitive()
                && minutesValue.getAsJsonPrimitive().isNumber() ? minutesValue.getAsInt() : -1;

        if (!isValidDate(date) || LocalDate.parse(date).isAfter(LocalDate.now())) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "운동 날짜를 확인해 주세요. (미래 날짜는 입력할 수 없습니다)");
            return;
        }
        if (!ExerciseDAO.isValidType(type) || !ExerciseDAO.isValidIntensity(intensity)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "운동 종류와 강도를 선택해 주세요.");
            return;
        }
        if (minutes < 1 || minutes > MAX_MINUTES) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "운동 시간은 1~" + MAX_MINUTES + "분으로 입력해 주세요.");
            return;
        }

        try {
            Map<String, Object> saved = exerciseDAO.addRecord(email, date, type, intensity, minutes);
            if (saved == null) {
                writeError(resp, HttpServletResponse.SC_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
            } else {
                resp.getWriter().write(gson.toJson(saved));
            }
        } catch (SQLException e) {
            fail(resp, "운동 기록 저장에 실패했습니다.", e);
        }
    }

    private void handleDelete(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        JsonElement idValue = body.get("exerciseId");
        if (idValue == null || !idValue.isJsonPrimitive() || !idValue.getAsJsonPrimitive().isNumber()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "삭제할 기록을 선택해 주세요.");
            return;
        }

        try {
            if (exerciseDAO.deleteRecord(email, idValue.getAsLong())) {
                resp.getWriter().write(gson.toJson(Map.of("message", "삭제되었습니다.")));
            } else {
                writeError(resp, HttpServletResponse.SC_NOT_FOUND, "삭제할 기록이 없습니다.");
            }
        } catch (SQLException e) {
            fail(resp, "운동 기록 삭제에 실패했습니다.", e);
        }
    }

    // ---------- 공통 (BodyMetricsServlet 과 같은 규칙) ----------
    private static void prepare(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private String authenticate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userEmail") == null) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요한 서비스입니다.");
            return null;
        }
        return (String) session.getAttribute("userEmail");
    }

    private JsonObject readBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseReader(req.getReader());
            if (parsed != null && parsed.isJsonObject()) return parsed.getAsJsonObject();
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

    private static String stringOrNull(JsonObject body, String key) {
        JsonElement value = body.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().trim() : null;
    }

    private void fail(HttpServletResponse resp, String message, SQLException e) throws IOException {
        getServletContext().log(message, e);
        writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(gson.toJson(Map.of("error", message)));
    }
}
