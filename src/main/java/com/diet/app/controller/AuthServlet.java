package com.diet.app.controller;

import com.diet.app.dao.UserDAO;
import com.diet.app.dto.UserDTO;
import com.diet.app.util.CalBMR;
import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

// 회원 인증 및 계정 관리 API 컨트롤러
@SuppressWarnings("serial")
@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private final UserDAO userDAO = new UserDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        var out = resp.getWriter();
        var pathInfo = req.getPathInfo();

        // 로그인 세션 상태 및 남은 시간 조회 API (세션 검증용)
        if ("/session".equals(pathInfo)) {
            var session = req.getSession(false); 
            
            if (session != null && session.getAttribute("userEmail") != null) {
                var email = (String) session.getAttribute("userEmail");
                
                long lastAccessedTime = session.getLastAccessedTime();
                long currentTime = System.currentTimeMillis();
                int maxInactiveInterval = session.getMaxInactiveInterval();
                
                long usedTimeSeconds = (currentTime - lastAccessedTime) / 1000;
                long remainingTime = maxInactiveInterval - usedTimeSeconds;

	                if (remainingTime > 0) {
                        var userInfo = userDAO.getUserByEmail(email);
                        
                        var responseMap = new java.util.HashMap<String, Object>();
                        responseMap.put("loggedIn", true);
                        responseMap.put("email", email);
                        responseMap.put("remainingTime", remainingTime);
                        responseMap.put("user", userInfo);
                        
                        out.write(gson.toJson(responseMap));
                    } else {
                        session.invalidate(); 
                        out.write("{\"loggedIn\": false}");
                    }
                } else {
                    session.invalidate();
                    out.write("{\"loggedIn\": false}");
                }
            } else {
                out.write("{\"loggedIn\": false}");
            }
        }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        var out = resp.getWriter();
        var pathInfo = req.getPathInfo();
        
        // 로그아웃 API (세션 만료 처리)
        if ("/logout".equals(pathInfo)) {
            var session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            out.write("""
                {"message": "로그아웃 되었습니다."}
                """);
            return;
        }

        var reader = req.getReader();

        switch (pathInfo) {
            // 회원가입 API (DB INSERT: email, password, name, gender, age, height_cm, weight_kg, activity_level, goal, preferance, target_daily_calories)
            case "/signup" -> {
                var rawUser = gson.fromJson(reader, UserDTO.class);
                if (!rawUser.isValid()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\": \"입력값이 올바르지 않습니다.\"}");
                    return;
                }
                
                int calculatedCalories = CalBMR.calculateTargetCalories(rawUser);
                var newUser = new UserDTO(
                    rawUser.userId(), rawUser.email(), rawUser.password(), rawUser.name(),
                    rawUser.gender(), rawUser.age(), rawUser.heightCm(), rawUser.weightKg(),
                    rawUser.actLevel(), rawUser.goal(), rawUser.preferance(), calculatedCalories
                );
                
                if (userDAO.insertUser(newUser)) {
                    out.write("{\"message\": \"회원가입 완료\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"error\": \"데이터베이스 저장 실패\"}");
                }
            }
            
            // 로그인 API (DB SELECT: email 조건으로 password 대조 및 세션 생성)
            case "/login" -> {
                var loginInfo = gson.fromJson(reader, UserDTO.class);
                var success = userDAO.login(loginInfo.email(), loginInfo.password());
                
                if (success) {
                    var session = req.getSession(true);
                    session.setAttribute("userEmail", loginInfo.email());
                    session.setMaxInactiveInterval(1800);
                    
                    out.write("{\"message\": \"로그인 성공\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("{\"error\": \"아이디 또는 비밀번호 불일치\"}");
                }
            }
            
            // 인증코드 이메일 발송 API (DB SELECT: email 존재 여부 확인 후 메일 전송)
            case "/password/code" -> {
                var requestData = gson.fromJson(reader, java.util.Map.class);
                String targetEmail = (String) requestData.get("email");
                
                if (userDAO.getUserByEmail(targetEmail) == null) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.write("{\"error\": \"가입되지 않은 이메일입니다.\"}");
                    return;
                }

                String code = String.format("%06d", new java.util.Random().nextInt(1000000));
                com.diet.app.util.VerificationManager.saveCode(targetEmail, code);
                com.diet.app.util.EmailUtil.sendVerificationCode(targetEmail, code);
                
                out.write("{\"message\": \"인증 코드가 발송되었습니다.\"}");
            }
            
            // 비밀번호 재설정 API (DB UPDATE: email 조건으로 password 갱신)
            case "/password/reset" -> {
                var requestData = gson.fromJson(reader, java.util.Map.class);
                String targetEmail = (String) requestData.get("email");
                String inputCode = (String) requestData.get("code");
                String newPassword = (String) requestData.get("newPassword");

                if (com.diet.app.util.VerificationManager.verifyCode(targetEmail, inputCode)) {
                    if (userDAO.updatePassword(targetEmail, newPassword)) {
                        out.write("{\"message\": \"비밀번호가 성공적으로 변경되었습니다.\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        out.write("{\"error\": \"DB 업데이트 실패\"}");
                    }
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("{\"error\": \"인증 코드가 올바르지 않거나 만료되었습니다.\"}");
                }
            }
            
            // 회원 탈퇴 API (DB DELETE: WHERE email 조건으로 계정 삭제)
            case "/withdraw" -> {
                var session = req.getSession(false);
                if (session == null || session.getAttribute("userEmail") == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    out.write("{\"error\": \"로그인이 필요합니다.\"}");
                    return;
                }

                String currentEmail = (String) session.getAttribute("userEmail");
                
                if (userDAO.deleteUser(currentEmail)) {
                    session.invalidate();
                    out.write("{\"message\": \"회원 탈퇴가 완료되었습니다.\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"error\": \"회원 탈퇴 처리 중 오류가 발생했습니다.\"}");
                }
            }
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\": \"잘못된 요청\"}");
            }
        }
    }
}