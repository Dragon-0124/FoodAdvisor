package com.diet.app.controller;

import com.diet.app.dao.DietDAO;
import com.diet.app.dto.DietRequestDTO;
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
import java.time.LocalDate;
import java.util.Map;

// 식단 CRUD 및 통계 API 컨트롤러
//  GET  /api/diets/records?date=YYYY-MM-DD            당일 식단 기록 목록 (dietId = intake_item_id 포함)
//  GET  /api/diets/daily?date=YYYY-MM-DD              일일 섭취량/목표 달성률
//  GET  /api/diets/stats?startDate=...&endDate=...    기간별 통계
//  POST /api/diets/record | /update | /delete         식단 등록 / 수정 / 삭제
//  (기존 호환: POST 로도 /records, /daily, /stats 조회 가능)
@SuppressWarnings("serial")
@WebServlet("/api/diets/*")
public class DietServlet extends HttpServlet {
    private final DietDAO dietDAO = new DietDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepare(req, resp);
        String email = authenticate(req, resp);
        if (email == null) return;

        handleRead(email, req, resp);
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
            case "/record" -> handleRecord(email, req, resp);
            case "/update" -> handleUpdate(email, req, resp);
            case "/delete" -> handleDelete(email, req, resp);
            case "/records", "/daily", "/stats" -> handleRead(email, req, resp);
            default -> writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
        }
    }

    // ---------- 조회 ----------
    private void handleRead(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String pathInfo = req.getPathInfo();

        if ("/records".equals(pathInfo)) {
            String date = req.getParameter("date");
            if (!isValidDate(date)) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "날짜를 YYYY-MM-DD 형식으로 입력해 주세요.");
                return;
            }
            resp.getWriter().write(gson.toJson(dietDAO.getDietRecords(email, date)));
            return;
        }

        if ("/daily".equals(pathInfo)) {
            String date = req.getParameter("date");
            if (!isValidDate(date)) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "날짜를 YYYY-MM-DD 형식으로 입력해 주세요.");
                return;
            }
            resp.getWriter().write(gson.toJson(dietDAO.getDailySummary(email, date)));
            return;
        }

        if ("/stats".equals(pathInfo)) {
            String startDate = req.getParameter("startDate");
            String endDate = req.getParameter("endDate");
            if (!isValidDate(startDate) || !isValidDate(endDate)) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "시작일과 종료일을 YYYY-MM-DD 형식으로 입력해 주세요.");
                return;
            }
            resp.getWriter().write(gson.toJson(dietDAO.getPeriodStats(email, startDate, endDate)));
            return;
        }

        writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
    }

    // ---------- 식단 등록 (intake_logs + intake_items INSERT) ----------
    private void handleRecord(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        DietRequestDTO dietReq = gson.fromJson(body, DietRequestDTO.class);
        if (dietReq == null || !isValidDate(dietReq.recordDate())
                || dietReq.foodId() == null || dietReq.portion() <= 0
                || dietReq.mealType() == null || dietReq.mealType().isBlank()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "입력값이 올바르지 않습니다.");
            return;
        }

        if (dietDAO.insertDietRecord(email, dietReq)) {
            writeMessage(resp, "식단이 성공적으로 기록되었습니다.");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "식단 기록에 실패했습니다.");
        }
    }

    // ---------- 식단 수정 (intake_items UPDATE, dietId = intake_item_id) ----------
    private void handleUpdate(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        Long dietId = longOrNull(body, "dietId");
        Long foodId = longOrNull(body, "foodId");
        Double portion = doubleOrNull(body, "portion");
        String recordDate = stringOrNull(body, "recordDate");

        if (dietId == null || foodId == null || portion == null || portion <= 0
                || (recordDate != null && !recordDate.isBlank() && !isValidDate(recordDate))) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "입력값이 올바르지 않습니다.");
            return;
        }

        DietRequestDTO updateReq = new DietRequestDTO(
                recordDate, stringOrNull(body, "mealType"), foodId, portion);

        if (dietDAO.updateDietRecord(dietId, email, updateReq)) {
            writeMessage(resp, "식단이 성공적으로 수정되었습니다.");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "식단 수정에 실패했습니다.");
        }
    }

    // ---------- 식단 삭제 (intake_items DELETE, dietId = intake_item_id) ----------
    private void handleDelete(String email, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        JsonObject body = readBody(req, resp);
        if (body == null) return;

        Long dietId = longOrNull(body, "dietId");
        if (dietId == null) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "삭제할 식단 ID가 필요합니다.");
            return;
        }

        if (dietDAO.deleteDietRecord(dietId, email)) {
            writeMessage(resp, "삭제되었습니다. 총 섭취량이 변경되었습니다.");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "삭제 실패");
        }
    }

    // ---------- 공통 ----------
    private static void prepare(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
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

    private static Long longOrNull(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsJsonPrimitive().getAsLong();
        }
        return null;
    }

    private static Double doubleOrNull(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsJsonPrimitive().getAsDouble();
        }
        return null;
    }

    private static String stringOrNull(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return value.getAsString();
        }
        return null;
    }

    private void writeMessage(HttpServletResponse resp, String message) throws IOException {
        resp.getWriter().write(gson.toJson(Map.of("message", message)));
    }

    private void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(gson.toJson(Map.of("error", message)));
    }
}
