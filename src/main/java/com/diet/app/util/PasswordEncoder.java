package com.diet.app.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// 비밀번호 SHA-256 단방향 암호화 유틸리티 클래스
public class PasswordEncoder {
    
    // 평문 비밀번호를 SHA-256 해시값으로 변환 (DB 연관 컬럼: USERS.password)
    public static String encode(String rawPassword) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update(rawPassword.getBytes());
            var byteData = md.digest();
            
            var sb = new StringBuilder();
            for (byte b : byteData) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("암호화 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}