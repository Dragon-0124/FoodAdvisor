package com.diet.app.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// 화면 파일(html/css/js)을 브라우저가 쓰기 전에 항상 서버에 변경 여부를 확인하게 합니다.
// 바뀌지 않았으면 Tomcat 이 ETag 로 304(본문 없음)만 보내므로 느려지지 않고,
// 바뀌었으면 새 파일을 받아서 Ctrl+F5 없이도 최신 화면이 보입니다.
@WebFilter(urlPatterns = {"/", "*.html", "*.css", "*.js"})
public class NoCacheFilter extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        resp.setHeader("Cache-Control", "no-cache");
        chain.doFilter(req, resp);
    }
}
