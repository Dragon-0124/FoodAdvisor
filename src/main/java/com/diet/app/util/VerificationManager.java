package com.diet.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// 이메일 인증 코드 저장, 만료, 발송 제한 및 입력 횟수 관리
public final class VerificationManager {
    private static final long CODE_LIFETIME_MS = 3 * 60 * 1000L;
    private static final long SEND_WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final ConcurrentHashMap<String, VerificationData> codes =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SendWindow> sendWindows =
            new ConcurrentHashMap<>();

    private VerificationManager() {
    }

    private record VerificationData(String code, long expiresAt, int attempts) {
    }

    private record SendWindow(long startedAt, int count) {
    }

    public static boolean acquireSendSlot(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        String key = normalize(email);
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        sendWindows.compute(key, (ignored, window) -> {
            if (window == null || now - window.startedAt() >= SEND_WINDOW_MS) {
                allowed.set(true);
                return new SendWindow(now, 1);
            }

            if (window.count() < MAX_SENDS_PER_WINDOW) {
                allowed.set(true);
                return new SendWindow(window.startedAt(), window.count() + 1);
            }

            return window;
        });

        return allowed.get();
    }

    public static void saveCode(String email, String code) {
        if (email == null || code == null) {
            return;
        }

        codes.put(normalize(email),
                new VerificationData(code, System.currentTimeMillis() + CODE_LIFETIME_MS, 0));
    }

    public static boolean verifyCode(String email, String inputCode) {
        if (email == null || inputCode == null) {
            return false;
        }

        String key = normalize(email);
        long now = System.currentTimeMillis();
        AtomicBoolean verified = new AtomicBoolean(false);

        codes.compute(key, (ignored, data) -> {
            if (data == null || now >= data.expiresAt()) {
                return null;
            }

            boolean matches = MessageDigest.isEqual(
                    data.code().getBytes(StandardCharsets.US_ASCII),
                    inputCode.getBytes(StandardCharsets.US_ASCII));

            if (matches) {
                verified.set(true);
                return null;
            }

            int nextAttempt = data.attempts() + 1;
            if (nextAttempt >= MAX_VERIFY_ATTEMPTS) {
                return null;
            }

            return new VerificationData(data.code(), data.expiresAt(), nextAttempt);
        });

        return verified.get();
    }

    public static void clearCode(String email) {
        if (email != null) {
            codes.remove(normalize(email));
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}