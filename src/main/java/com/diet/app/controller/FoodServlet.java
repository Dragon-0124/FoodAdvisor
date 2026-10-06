package com.diet.app.controller;

import com.diet.app.dao.FoodDAO;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

@SuppressWarnings("serial")
@WebServlet("/api/foods")
public class FoodServlet extends HttpServlet {
    private static final int MAX_PAGE_SIZE = 100;

    private final FoodDAO foodDAO = new FoodDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String keyword = req.getParameter("keyword");
        if (keyword == null || keyword.isBlank() || keyword.length() > 50) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "음식 검색어를 1~50자 입력해 주세요.");
            return;
        }

        try {
            resp.getWriter().write(gson.toJson(
                    foodDAO.searchFoodByNamePaged(keyword.trim(), 1, MAX_PAGE_SIZE)));
        } catch (SQLException e) {
            getServletContext().log("음식 정보 조회에 실패했습니다.", e);
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "음식 정보를 불러오지 못했습니다.");
        }
    }

    private void writeError(HttpServletResponse resp, int status, String message)
            throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(gson.toJson(java.util.Map.of("error", message)));
    }
}
