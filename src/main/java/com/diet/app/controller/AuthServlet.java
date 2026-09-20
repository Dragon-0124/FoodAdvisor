package com.diet.app.controller;

import com.diet.app.dao.UserDAO;
import com.diet.app.dto.UserDTO;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private final UserDAO userDAO = new UserDAO();
    private final Gson gson = new Gson(); 

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        var out = resp.getWriter();
        var pathInfo = req.getPathInfo();
        var reader = req.getReader();

        // Java 14+ 화살표 switch 문: break가 필요 없고 코드 블록이 독립적임
        switch (pathInfo) {
            case "/signup" -> {
                var newUser = gson.fromJson(reader, UserDTO.class);
                
                if (!newUser.isValid()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("""
                        {"error": "입력값이 올바르지 않습니다."}
                        """);
                    return;
                }
                
                if (userDAO.insertUser(newUser)) {
                    out.write("""
                        {"message": "회원가입 완료"}
                        """);
                } else {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("""
                        {"error": "데이터베이스 저장 실패"}
                        """);
                }
            }
            case "/login" -> {
                var loginInfo = gson.fromJson(reader, UserDTO.class);
                var success = userDAO.login(loginInfo.email(), loginInfo.password());
                
                if (success) {
                    req.getSession().setAttribute("userEmail", loginInfo.email());
                    out.write("""
                        {"message": "로그인 성공", "token": "임시_세션_토큰"}
                        """);
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("""
                        {"error": "아이디 또는 비밀번호가 틀렸습니다."}
                        """);
                }
            }
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("""
                    {"error": "잘못된 요청 경로입니다."}
                    """);
            }
        }
    }
}