package com.diet.app.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

// HikariCP 기반 데이터베이스 커넥션 풀 관리 클래스
public class DBConnection {
    private static HikariDataSource dataSource;

    // 정적 블록에서 HikariCP 커넥션 풀 초기 설정 및 데이터소스 생성 (DB 연관 정보: diet_db, root 계정)
    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3066/diet_db?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true");
        config.setUsername("root");
        config.setPassword("1234");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);

        dataSource = new HikariDataSource(config);
    }

    // 커넥션 풀에서 데이터베이스 연결 객체(Connection)를 대여하여 반환
    public static Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
            return null;
        }
    }
}