package com.diet.app.controller;

import com.diet.app.dao.BodyMetricsDAO;
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
import java.time.LocalDate;
import java.time.ZoneId;
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
    // 인증 코드 확인 후 비밀번호 변경까지 허용되는 시간 (세션에 저장)
    private static final long RESET_VERIFIED_LIFETIME_MS = 10 * 60 * 1000L;
    private static final String RESET_VERIFIED_EMAIL = "resetVerifiedEmail";
    private static final String RESET_VERIFIED_AT = "resetVerifiedAt";

    // 가입 시 선택 입력하는 신체 값의 허용 범위 (BodyMetricsServlet 과 동일)
    private static final double MUSCLE_MAX = 200;
    private static final double BODY_FAT_MAX = 75;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserDAO userDAO = new UserDAO();
    private final BodyMetricsDAO bodyMetricsDAO = new BodyMetricsDAO();
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
            case "/find-id" -> handleFindId(body, resp); // 아이디 찾기 라우팅 추가
            case "/password/code" -> handlePasswordCode(body, resp);
            case "/password/verify" -> handlePasswordVerify(body, req, resp);
            case "/password/reset" -> handlePasswordReset(body, req, resp);
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

        // 선택 입력: 골격근량/체지방률 (비어 있으면 null)
        final Double muscleKg;
        final Double bodyFatPct;
        try {
            muscleKg = optionalNumber(body, "muscleKg");
            bodyFatPct = optionalNumber(body, "bodyFatPct");
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "골격근량/체지방률은 숫자로 입력해 주세요.");
            return;
        }
        if (!inRange(muscleKg, MUSCLE_MAX) || !inRange(bodyFatPct, BODY_FAT_MAX)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "골격근량은 1~200kg, 체지방률은 1~75% 범위로 입력해 주세요.");
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
                CalBMR.normalizeActivityLevel(user.actLevel()),
                user.goal(),
                user.preference(),
                user.securityQuestion(),
                user.securityAnswer(),
                targetCalories
        );

        if (userDAO.insertUser(newUser)) {
            saveInitialBodyMetrics(newUser.email(), newUser.weightKg(), muscleKg, bodyFatPct);
            resp.getWriter().write("{\"message\":\"회원가입 완료\"}");
        } else {
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "회원가입 처리에 실패했습니다.");
        }
    }

    // 가입 시 입력한 체중(+선택 입력한 골격근량/체지방률)을 오늘 날짜의 신체 기록으로 저장합니다.
    // 실패해도 회원가입 자체는 성공으로 처리합니다. (신체 기록 페이지에서 다시 입력 가능)
    private void saveInitialBodyMetrics(String email, double weightKg,
                                        Double muscleKg, Double bodyFatPct) {
        try {
            bodyMetricsDAO.saveRecord(email, LocalDate.now(SEOUL).toString(),
                    weightKg, muscleKg, bodyFatPct);
        } catch (Exception e) {
            System.err.println("⚠️ [DB] 가입 시 신체 기록 저장 실패 (회원가입은 완료됨): " + e.getMessage());
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

        HttpSession oldSession = req.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = req.getSession(true);
        session.setAttribute("userEmail", email);
        session.setMaxInactiveInterval(1800);

        resp.getWriter().write("{\"message\":\"로그인 성공\"}");
    }

    // ========== 아이디 찾기 처리 로직 ==========
    private void handleFindId(JsonObject body, HttpServletResponse resp) throws IOException {
        String name = stringValue(body, "name");
        String securityQuestion = stringValue(body, "securityQuestion");
        String securityAnswer = stringValue(body, "securityAnswer");

        if (name == null || name.isBlank() || 
            securityQuestion == null || securityQuestion.isBlank() || 
            securityAnswer == null || securityAnswer.isBlank()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "이름, 질문, 답변을 모두 입력해 주세요.");
            return;
        }

        // DB에서 이름, 질문, 답변으로 이메일 조회
        String email = userDAO.findEmailBySecurityInfo(name.trim(), securityQuestion, securityAnswer.trim());

        if (email != null) {
            String maskedEmail = maskEmail(email);
            resp.getWriter().write(gson.toJson(Map.of("email", maskedEmail)));
        } else {
            writeError(resp, HttpServletResponse.SC_NOT_FOUND, "일치하는 계정을 찾지 못했습니다.");
        }
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

    // 인증 코드 확인: 성공하면 세션에 인증된 이메일을 기록합니다.
    private void handlePasswordVerify(JsonObject body, HttpServletRequest req,
                                      HttpServletResponse resp) throws IOException {
        String email = stringValue(body, "email");
        String code = stringValue(body, "code");

        if (!isValidEmail(email) || code == null || !code.matches("\\d{6}")) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "이메일과 6자리 인증 코드를 확인해 주세요.");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        if (!VerificationManager.verifyCode(email, code)) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "인증 코드가 올바르지 않거나 만료되었습니다.");
            return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute(RESET_VERIFIED_EMAIL, email);
        session.setAttribute(RESET_VERIFIED_AT, System.currentTimeMillis());
        resp.getWriter().write("{\"message\":\"인증이 완료되었습니다. 새 비밀번호를 입력해 주세요.\"}");
    }

    private void handlePasswordReset(JsonObject body, HttpServletRequest req,
                                     HttpServletResponse resp) throws IOException {
        String email = stringValue(body, "email");
        String newPassword = stringValue(body, "newPassword");

        if (!isValidEmail(email) || !UserDTO.isValidPassword(newPassword)) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "이메일 또는 새 비밀번호를 확인해 주세요.");
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);
        HttpSession session = req.getSession(false);
        if (!isResetVerified(session, email)) {
            writeError(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "인증 코드 확인을 먼저 완료해 주세요.");
            return;
        }

        // 인증 1회당 비밀번호 변경 1회만 허용합니다.
        session.removeAttribute(RESET_VERIFIED_EMAIL);
        session.removeAttribute(RESET_VERIFIED_AT);

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
                || "/find-id".equals(path) // 지원 경로 추가
                || "/password/code".equals(path)
                || "/password/verify".equals(path)
                || "/password/reset".equals(path)
                || "/withdraw".equals(path);
    }

    private static boolean isResetVerified(HttpSession session, String email) {
        if (session == null) return false;
        Object verifiedEmail = session.getAttribute(RESET_VERIFIED_EMAIL);
        Object verifiedAt = session.getAttribute(RESET_VERIFIED_AT);
        return email.equals(verifiedEmail)
                && verifiedAt instanceof Long at
                && System.currentTimeMillis() - at < RESET_VERIFIED_LIFETIME_MS;
    }

    // 항목이 없거나 null 이면 null, 숫자면 값, 그 외 타입이면 IllegalArgumentException (BodyMetricsServlet 과 동일 규칙)
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
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static void writeError(HttpServletResponse resp, int status,
                                   String message) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(new Gson().toJson(Map.of("error", message)));
    }

    // 이메일 앞자리 마스킹 처리 유틸리티
    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email; 
        
        String idPart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);
        
        int visibleCount = Math.max(1, (int) Math.ceil(idPart.length() / 2.0));
        String maskedId = idPart.substring(0, visibleCount) + "*".repeat(idPart.length() - visibleCount);
        
        return maskedId + domainPart;
    }
}