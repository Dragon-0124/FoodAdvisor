package com.diet.app.controller;

import com.diet.app.dao.UserDAO;
import com.diet.app.dto.UserDTO;
import com.diet.app.util.CalBMR;
import com.diet.app.util.EmailUtil;
import com.diet.app.util.VerificationManager;
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
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings("serial")
@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserDAO userDAO = new UserDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepareJsonResponse(req, resp);

        if (!"/session".equals(req.getPathInfo())) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userEmail") == null) {
            if (session != null) {
                session.invalidate();
            }
            resp.getWriter().write("{\"loggedIn\":false}");
            return;
        }

        String email = (String) session.getAttribute("userEmail");
        long elapsedSeconds =
                (System.currentTimeMillis() - session.getLastAccessedTime()) / 1000;
        long remainingSeconds = session.getMaxInactiveInterval() - elapsedSeconds;

        UserDTO user = remainingSeconds > 0
                ? userDAO.getUserByEmail(email)
                : null;

        if (user == null) {
            session.invalidate();
            resp.getWriter().write("{\"loggedIn\":false}");
            return;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("loggedIn", true);
        response.put("email", email);
        response.put("remainingTime", remainingSeconds);
        response.put("user", user);

        resp.getWriter().write(gson.toJson(response));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        prepareJsonResponse(req, resp);
        String pathInfo = req.getPathInfo();

        if ("/logout".equals(pathInfo)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            resp.getWriter().write("{\"message\":\"로그아웃 되었습니다.\"}");
            return;
        }

        if (!isSupportedPath(pathInfo)) {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "잘못된 요청입니다.");
            return;
        }

        final JsonObject body;
        try {
            JsonElement parsed = JsonParser.parseReader(req.getReader());
            if (parsed == null || !parsed.isJsonObject()) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "JSON 객체 형식의 요청이 필요합니다.");
                return;
            }
            body = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "요청 JSON 형식이 올바르지 않습니다.");
            return;
        }

        switch (pathInfo) {
            case "/signup" -> handleSignup(body, resp);
            case "/login" -> handleLogin(body, req, resp);
            case "/password/code" -> handlePasswordCode(body, resp);
            case "/password/reset" -> handlePasswordReset(body, resp);
            case "/withdraw" -> handleWithdraw(req, resp);
            default -> writeError(resp, HttpServletResponse.SC_NOT_FOUND,
                    "잘못된 요청입니다.");
        }
    }

    private void handleSignup(JsonObject body, HttpServletResponse resp)
            throws IOException {
        UserDTO user = gson.fromJson(body, UserDTO.class);
        if (user == null || !user.isValid()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "입력값이 올바르지 않습니다.");
            return;
        }

        int targetCalories = CalBMR.calculateTargetCalories(user);
        UserDTO newUser = new UserDTO(
                user.userId(),
                user.email().trim().toLowerCase(Locale.ROOT),
                user.password(),
                user.name().trim(),
                user.gender(),
                user.age(),
                user.heightCm(),
                user.weightKg(),
                user.actLevel(),
                user.goal(),
                user.preferance(),
                targetCalories
        );

        if (userDAO.insertUser(newUser)) {
            resp.getWriter().write("{\"message\":\"회원가입 완료\"}");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "회원가입 처리에 실패했습니다.");
        }
    }

    private void handleLogin(JsonObject body, HttpServletRequest req,
                             HttpServletResponse resp) throws IOException {
        String email = stringValue(body, "email");
        String password = stringValue(body, "password");

        if (!isValidEmail(email) || password == null || password.isBlank()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "이메일과 비밀번호를 확인해 주세요.");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        if (!userDAO.login(email, password)) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "아이디 또는 비밀번호가 올바르지 않습니다.");
            return;
        }

        // 기존 로그인 전 세션을 폐기하고 새 세션을 발급합니다.
        HttpSession oldSession = req.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = req.getSession(true);
        session.setAttribute("userEmail", email);
        session.setMaxInactiveInterval(1800);

        resp.getWriter().write("{\"message\":\"로그인 성공\"}");
    }

    private void handlePasswordCode(JsonObject body, HttpServletResponse resp)
            throws IOException {
        String email = stringValue(body, "email");
        if (!isValidEmail(email)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "이메일 형식이 올바르지 않습니다.");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        UserDTO account = userDAO.getUserByEmail(email);

        if (account != null && VerificationManager.acquireSendSlot(email)) {
            String code = String.format(
                    Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));

            VerificationManager.saveCode(email, code);
            if (!EmailUtil.sendVerificationCode(email, code)) {
                VerificationManager.clearCode(email);
            }
        }

        // 계정 존재 여부를 응답으로 알려주지 않습니다.
        resp.getWriter().write(
                "{\"message\":\"계정이 존재하면 인증 코드를 발송했습니다.\"}");
    }

    private void handlePasswordReset(JsonObject body, HttpServletResponse resp)
            throws IOException {
        String email = stringValue(body, "email");
        String code = stringValue(body, "code");
        String newPassword = stringValue(body, "newPassword");

        if (!isValidEmail(email)
                || code == null
                || !code.matches("\\d{6}")
                || !UserDTO.isValidPassword(newPassword)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "이메일, 인증 코드 또는 새 비밀번호를 확인해 주세요.");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        if (!VerificationManager.verifyCode(email, code)) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "인증 코드가 올바르지 않거나 만료되었습니다.");
            return;
        }

        if (userDAO.updatePassword(email, newPassword)) {
            resp.getWriter().write("{\"message\":\"비밀번호가 변경되었습니다.\"}");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "비밀번호 변경에 실패했습니다.");
        }
    }

    private void handleWithdraw(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userEmail") == null) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "로그인이 필요합니다.");
            return;
        }

        String email = (String) session.getAttribute("userEmail");
        if (userDAO.deleteUser(email)) {
            session.invalidate();
            resp.getWriter().write("{\"message\":\"회원 탈퇴가 완료되었습니다.\"}");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "회원 탈퇴 처리에 실패했습니다.");
        }
    }

    private static boolean isSupportedPath(String path) {
        return "/signup".equals(path)
                || "/login".equals(path)
                || "/password/code".equals(path)
                || "/password/reset".equals(path)
                || "/withdraw".equals(path);
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static String stringValue(JsonObject body, String key) {
        JsonElement value = body.get(key);
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static void prepareJsonResponse(HttpServletRequest req,
                                            HttpServletResponse resp) {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static void writeError(HttpServletResponse resp, int status,
                                   String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(new Gson().toJson(Map.of("error", message)));
    }
}