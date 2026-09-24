package com.diet.app.util;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;
import java.io.InputStream;

// 구글 SMTP 기반 이메일 발송 유틸리티 클래스
public class EmailUtil {
    private static String SENDER_EMAIL;
    private static String APP_PASSWORD;

    // 설정 파일(config.properties)에서 발신자 이메일 및 앱 비밀번호 로드
    static {
        try (InputStream input = EmailUtil.class.getClassLoader().getResourceAsStream("config.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                SENDER_EMAIL = prop.getProperty("mail.sender.email");
                APP_PASSWORD = prop.getProperty("mail.app.password");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 비밀번호 재설정 인증코드 메일 발송 (수신 대상: USERS.email)
    public static void sendVerificationCode(String toEmail, String code) {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, APP_PASSWORD);
            }
        });

        try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(SENDER_EMAIL, "식단관리앱 관리자"));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            msg.setSubject("[식단관리앱] 비밀번호 찾기 인증 코드");
            msg.setText("요청하신 인증 코드는 [" + code + "] 입니다.\n3분 이내에 앱에 입력해 주세요.");

            Transport.send(msg);
            System.out.println("✅ [Email] 인증 코드 발송 완료: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ [Email] 메일 발송 실패");
            e.printStackTrace();
        }
    }
}