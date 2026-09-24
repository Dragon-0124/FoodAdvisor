package com.diet.app.controller;

import com.diet.app.dao.DietDAO;
import com.diet.app.dto.DietRequestDTO;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

// 식단 CRUD 및 통계 API 컨트롤러
@SuppressWarnings("serial")
@WebServlet("/api/diets/*")
public class DietServlet extends HttpServlet {
    private final DietDAO dietDAO = new DietDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        var out = resp.getWriter();
        
        // 세션 검증
        var session = req.getSession(false);
        if (session == null || session.getAttribute("userEmail") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.write("{\"error\": \"로그인이 필요한 서비스입니다.\"}");
            return;
        }
        
        var email = (String) session.getAttribute("userEmail");
        var pathInfo = req.getPathInfo();

        // 식단 등록 (DB INSERT: user_id, record_date, meal_type, food_name, portion, total_calories, total_carbs, total_protein, total_fat, total_sugar)
        if ("/record".equals(pathInfo)) {
            var dietReq = gson.fromJson(req.getReader(), DietRequestDTO.class);
            
            boolean isSaved = dietDAO.insertDietRecord(email, dietReq);
            
            if (isSaved) {
                out.write("{\"message\": \"식단이 성공적으로 기록되었습니다.\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("{\"error\": \"식단 기록에 실패했습니다.\"}");
            }
        }
     
        // 일일 섭취량/달성률 조회 (DB SELECT: total_calories, total_carbs, total_protein, total_fat, target_daily_calories)
        if ("/daily".equals(pathInfo)) {
            String date = req.getParameter("date");
            var summary = dietDAO.getDailySummary(email, date);
            out.write(gson.toJson(summary));
            return;
        }

        // 기간별 통계 조회 (DB SELECT: record_date 기준 total_calories, total_carbs, total_protein, total_fat 합계)
        if ("/stats".equals(pathInfo)) {
            String startDate = req.getParameter("startDate");
            String endDate = req.getParameter("endDate");
            var stats = dietDAO.getPeriodStats(email, startDate, endDate);
            out.write(gson.toJson(stats));
            return;
        }
     
        // 식단 수정 (DB UPDATE: meal_type, portion, total_calories, total_carbs, total_protein, total_fat, total_sugar 갱신 / WHERE record_id)
        if ("/update".equals(pathInfo)) {
            var requestData = gson.fromJson(req.getReader(), java.util.Map.class);
            Long dietId = ((Number) requestData.get("dietId")).longValue();
            DietRequestDTO updateReq = new DietRequestDTO(
                (String) requestData.get("recordDate"),
                (String) requestData.get("mealType"),
                ((Number) requestData.get("foodId")).longValue(),
                ((Number) requestData.get("portion")).doubleValue()
            );
            
            boolean isUpdated = dietDAO.updateDietRecord(dietId, email, updateReq);
            
            if (isUpdated) {
                out.write("{\"message\": \"식단이 성공적으로 수정되었습니다.\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("{\"error\": \"식단 수정에 실패했습니다.\"}");
            }
        }
        
        // 식단 삭제 (DB DELETE: WHERE record_id)
        if ("/delete".equals(pathInfo)) {
            var requestData = gson.fromJson(req.getReader(), java.util.Map.class);
            Long dietId = ((Number) requestData.get("dietId")).longValue(); 
            
            if (dietDAO.deleteDietRecord(dietId, email)) {
                out.write("{\"message\": \"삭제되었습니다. 총 섭취량이 변경되었습니다.\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("{\"error\": \"삭제 실패\"}");
            }
        }
    }
}