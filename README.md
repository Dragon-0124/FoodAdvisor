# 식단 관리 앱 백엔드 시스템 (Food Logging API)

현대인의 식단 및 체중 관리를 위해 제작된 자바 기반의 소규모 푸드 다이어리 애플리케이션 백엔드 시스템입니다. 사용자가 섭취한 음식을 기록하고, 영양소 통계 및 기초대사량(BMR) 기반의 맞춤형 데이터를 제공하여 효율적인 식단 관리를 돕습니다.

## 🛠 기술 스택 (Tech Stack)

* **Language/Environment**: Java 20, Apache Tomcat 10.1 (`jakarta.servlet`)
* **Database**: MySQL 8.0.33, HikariCP 5.1.0 (Connection Pool)
* **Libraries**: Gson (JSON Serialization), SLF4J (Logging), `jakarta.mail` (SMTP)
* **Architecture**: MVC 패턴 기반 (Controller - DAO - DTO) 및 순수 Servlet 활용 (Spring Boot 미사용)

---

## 📂 프로젝트 구조 및 핵심 기능 (Project Architecture)

### 1. Controllers (`com.diet.app.controller`)

클라이언트의 HTTP 요청을 처리하고 JSON 형태로 응답하는 API 엔드포인트입니다.

* **`AuthServlet.java`**: 회원 인증 및 계정 관리 API
* 세션 검증 (`/api/auth/session`), 회원가입 (`/signup`), 로그인 (`/login`), 로그아웃 (`/logout`), 계정 탈퇴 (`/withdraw`)
* 이메일 인증 번호 발송 및 비밀번호 재설정 (`/password/code`, `/password/reset`)


* **`DietServlet.java`**: 식단 CRUD 및 통계 API
* 식단 기록 등록/수정/삭제 (`/api/diets/record`, `/update`, `/delete`)
* 일일 영양소 요약 및 기간별(주간/월간) 통계 조회 (`/daily`, `/stats`)



### 2. DAOs (`com.diet.app.dao`)

데이터베이스와 직접 통신하며 쿼리를 수행하는 데이터 접근 객체입니다.

* **`UserDAO.java`**: `USERS` 테이블 관리. 회원 정보 삽입, 이메일/비밀번호 검증, 비밀번호 업데이트 및 계정 삭제 처리.
* **`DietDAO.java`**: `USER_DIET_RECORDS` 테이블 관리. 식단 기록 삽입/수정/삭제 시 조인을 통해 영양소 데이터를 자동 계산 및 갱신. 일일 달성률 및 기간별 탄·단·지 비율 집계.
* **`FoodDAO.java`**: `FOODS` 테이블 관리. 키워드 기반 음식 검색, 페이징 처리, 단일 음식 상세 조회 및 사용자 정의(Custom) 커스텀 식품 등록.

### 3. DTOs (`com.diet.app.dto`)

계층 간 데이터 교환 및 비즈니스 로직(유효성 검사, 영양소 비례 계산)을 포함하는 Record 객체입니다.

* **`UserDTO.java`**: 계정 정보 및 신체 지수(키, 몸무게, 활동량, 목표)와 가입 유효성 검증 로직 포함.
* **`DietRequestDTO.java` & `DietDTO.java**`: 클라이언트의 식단 입력 요청 데이터(식사 유형, 음식 ID, 섭취량) 및 서버의 응답 데이터 포맷.
* **`FoodDTO.java`**: 식품의 기본 영양소 정보 및 섭취량(portion)에 따른 칼로리/영양소 비례 계산 메서드 포함.
* **`NutrientDTO.java`**: 일일 섭취 영양소 합계 및 잔여 목표치 계산용 데이터.

### 4. Utils (`com.diet.app.util`)

시스템 전반에서 사용되는 공통 모듈 및 비즈니스 유틸리티입니다.

* **`DBConnection.java`**: HikariCP를 활용한 효율적인 MySQL 데이터베이스 커넥션 풀 관리.
* **`CalBMR.java`**: Mifflin-St Jeor 공식과 사용자의 활동량/목표(감량/증량)를 반영하여 일일 권장 목표 칼로리(TDEE) 자동 계산.
* **`NutCalculator.java`**: 섭취량에 따른 정확한 영양소 산출 및 체질량지수(BMI) 계산.
* **`PasswordEncoder.java`**: SHA-256 알고리즘을 이용한 비밀번호 단방향 암호화.
* **`EmailUtil.java` & `VerificationManager.java**`: Google SMTP를 이용한 인증 코드 메일 발송 및 `ConcurrentHashMap` 기반의 3분 제한 인메모리 인증 코드 검증.

---

## 💾 데이터베이스 주요 테이블 (Database Tables)

* **`USERS`**: 사용자 개인정보, 신체 데이터, 계정 정보 및 산출된 목표 칼로리(`target_daily_calories`) 저장.
* **`FOODS`**: 공용 식품 영양 DB 및 사용자 직접 등록(Custom) 식품 데이터 보관.
* **`USER_DIET_RECORDS`**: 사용자별/날짜별/식사유형별 섭취 기록 저장. (수정 시 `FOODS` 데이터를 참조하여 부분 영양소 재계산 처리)