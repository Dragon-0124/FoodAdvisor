# FoodAdvisor UI - mock preview

## 이번 단계에서 가능한 것
- 로그인/회원가입/아이디 찾기/비밀번호 재설정 화면의 브라우저 내 동작
- 날짜 선택, 식단 추가·삭제, 하루 섭취 열량 합계 표시
- 체중 기록 추가 및 그래프/표 확인
- PC와 모바일 화면 크기에 맞춘 UI 확인

## 실행 방법
1. 압축을 풉니다.
2. `index.html`을 더블 클릭해 시작 화면을 엽니다.
3. 회원가입 화면에서 계정을 만든 뒤 로그인하거나, `dashboard.html`을 직접 열어 식단 화면을 테스트합니다.

## 반드시 알아둘 점
- 현재는 **UI 확인용 목업(Mock) 단계**입니다. 실제 서버·MySQL DB와 통신하지 않습니다.
- 로그인/회원가입 정보와 식단·체중 데이터는 브라우저 `localStorage`에 저장됩니다. 실제 개인정보를 입력하지 마세요.
- 실제 API 연결 시 `js/auth.js`의 브라우저 인증 로직과 식단/체중 데이터 저장 부분을 조원 프로젝트의 API 계약에 맞춰 교체해야 합니다.
- `js/data-adapter.js`는 데이터 연결 지점을 정리하기 위한 초안입니다. 실제 API 경로와 요청/응답 형식은 아직 확정된 것이 아니므로 예시 경로를 실제 경로로 간주하지 마세요.
- Java, Servlet, DAO, DTO, DB 설정 파일은 이 UI 미리보기 ZIP에서 수정하지 않았습니다.

## 실제 연동 때 확인할 내용
1. 식단 조회/추가/삭제 API의 URL, HTTP 메서드, JSON 형식
2. 체중 기록 조회/추가/수정/삭제 API 형식
3. 로그인 세션 방식 및 인증이 필요한 API
4. 사용자별 데이터 연결 기준과 날짜 형식
5. 프로젝트의 실제 웹 경로(context path)


## UI theme update (v3)
- Updated the shared palette to a soft lavender background with violet primary actions, inspired by the general mood of nutrition/wellness apps such as Pillyze without copying its exact screens or assets.
- Added matching visual overrides to dashboard, weight tracker, landing, and account pages.
- This is a visual refresh only; validation and API integration remain for the next step.

## UI polish update (v6)
- Applied consistent soft-violet hover/focus states across the pages using `css/style.css`.
- Added subtle page entrance motion, small button hover/press movement, and a translucent violet click ripple (`js/ui-effects.js`).
- Native `details/summary` disclosure panels animate gently when opened; native browser `select` menus remain controlled by the browser/OS, with smooth focus styling.
- Respects the operating system's `prefers-reduced-motion` accessibility setting.
- Effects are visual only and do not change the mock-data adapter or API behavior.

## 음식 자동완성·열량 계산 (v17)
- `js/food-service.js`가 음식 검색을 담당합니다. 샘플 음식 데이터는 모두 제거했습니다. 학교 API가 설정되기 전에는 검색 결과가 비어 있으며, 임의의 음식/열량을 보여주지 않습니다.
- 실제 연동 시 `mode: "api"`, `apiBase`, `searchPath`를 조원 Servlet의 실제 경로에 맞춰 설정하고, 응답을 `{ id, name, kcalPer100g }` 또는 `{ foods: [...] }` / `{ results: [...] }` 형태로 맞춥니다. 현재 경로 `/api/foods/search?q=`는 예시이며 확정된 주소가 아닙니다.
- DB에는 100g당 평균 칼로리가 있으므로 계산식은 `섭취 g × kcal/100g ÷ 100`입니다. 인분 방식은 사용자가 `1인분 기준(g)`을 직접 입력합니다.
- 음식 이름 입력 후 Tab으로 첫 후보를 선택하고, 선택 후 다음 Tab은 다음 입력란으로 이동합니다. 위/아래 방향키로 후보를 바꿀 수 있습니다.
- 식단 기록 저장은 여전히 브라우저 localStorage 기반입니다. 로그인/회원가입, 체중 기록, 식단 저장은 실제 서버/DB 연동이 아니므로 제출용 통합 전에 조원의 실제 API에 연결해야 합니다.

## 프로젝트 병합 메모 (FoodAdvisor)
- UI 파일은 `src/main/webapp/` 아래로 병합했습니다. (index/login/signup/find-id/reset-password/weight-tracker/dashboard, `css/style.css`, `js/*`)
- 기존 `index.html`(식단 입력 화면)은 `dashboard.html`로 대체되었고, `index.html`은 시작 화면입니다. 이전 버전은 Git 기록에 남아 있습니다.
- `js/food-service.js`는 위 v17 설명의 `empty` 모드가 아니라 `api` 모드로 바꿔 실제 `GET api/foods?keyword=` 에 연결했습니다.
- 로그인/회원가입, 식단 저장, 체중 기록은 아직 localStorage 목업입니다. (`AuthServlet`, `DietServlet` 미연결)
