package com.diet.app.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 이메일 인증코드 메모리 저장 및 검증 유틸리티 클래스
public class VerificationManager{
    private static final Map<String, VerificationData> store = new ConcurrentHashMap<>();

    private record VerificationData(String code, long expireTime) {}

    // 인증코드 저장 및 3분 유효기간 설정 (관련 데이터: USERS.email 기준 임시 인증코드)
    public static void saveCode(String email, String code) {
        store.put(email, new VerificationData(code, System.currentTimeMillis() + (3 * 60 * 1000)));
    }

    // 입력된 인증코드 유효성 및 만료 여부 검증 (관련 데이터: USERS.email 기준 입력 인증코드)
    public static boolean verifyCode(String email, String inputCode) {
        VerificationData data = store.get(email);
        if (data == null) return false;
        
        if (System.currentTimeMillis() > data.expireTime()) {
            store.remove(email);
            return false;
        }
        
        if (data.code().equals(inputCode)) {
            store.remove(email);
            return true;
        }
        return false;
    }
}