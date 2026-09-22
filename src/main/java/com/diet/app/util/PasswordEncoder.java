package com.diet.app.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordEncoder {
    
    // encoding Password SHA-256
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