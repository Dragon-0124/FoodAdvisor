package com.diet.app.util;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.Properties;

public class EmailUtil {
    private static String senderEmail;
    private static String appPassword;

    static {
        try (InputStream input =
                     EmailUtil.class.getClassLoader()
                             .getResourceAsStream("config.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                senderEmail = properties.getProperty("mail.sender.email");
                appPassword = properties.getProperty("mail.app.password");
            }
        } catch (Exception e) {
            System.err.println("메일 설정을 불러오지 못했습니다.");
        }
    }

    public static boolean sendVerificationCode(String toEmail, String code) {
        if (senderEmail == null || senderEmail.isBlank()
                || appPassword == null || appPassword.isBlank()
                || toEmail == null || code == null) {
            System.err.println("메일 발송 설정 또는 입력값이 올바르지 않습니다.");
            return false;
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");

        try {
            Session mailSession = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, appPassword);
                }
            });

            Message message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(senderEmail, "식단관리앱 관리자"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("[식단관리앱] 비밀번호 찾기 인증 코드");
            message.setText("요청하신 인증 코드는 [" + code
                    + "] 입니다.\n3분 이내에 앱에 입력해 주세요.");

            Transport.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("인증 메일 발송에 실패했습니다.");
            return false;
        }
    }
}