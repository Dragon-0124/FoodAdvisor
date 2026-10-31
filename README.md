# FoodAdvisor — 식단 관리 웹 애플리케이션

먹은 음식과 운동, 체중·체성분을 기록하고, **지금의 식습관과 운동량이 계속되면 앞으로 몸이 어떻게 변할지**를 그래프로 보여주는 식단 관리 서비스입니다.
Spring 없이 순수 Servlet(Jakarta EE) + MySQL 로 만들었습니다.

---

## 주요 기능

| 화면 | 파일 | 기능 |
|---|---|---|
| 시작 화면 | `index.html` | 로그인 / 회원가입으로 이동 |
| 회원가입 | `signup.html` | 신체 정보·평소 활동 강도·목표 입력 → 목표 칼로리 자동 계산. 골격근량·체지방률은 **선택 입력**이며, 입력한 체중과 함께 신체 기록의 첫 기록으로 저장 |
| 로그인 / 아이디 찾기 | `login.html`, `find-id.html` | 세션 로그인, 본인 확인 질문으로 이메일 찾기(일부 가림) |
| 비밀번호 재설정 | `reset-password.html` | 이메일 인증 코드 발송 → **코드 확인** → 새 비밀번호 저장 (확인 전에는 입력칸 잠금) |
| 대시보드 | `dashboard.html` | 주간 날짜 선택, 끼니별 식단, 하루 총 섭취 열량, 주간 칼로리·체중 그래프<br>**오늘의 식단 입력**: 끼니별 등록 현황 → 끼니별 음식 추가(여러 음식 담기, 탄·단·지·당·칼로리 합계)<br>**전체 기록**: 월별 달력 / 년별 12개월 요약<br>**영양정보 카드**: 선택한 날짜의 탄·단·지 섭취량, 목표 범위, 섭취 비율 → 누르면 상세 화면 |
| 영양정보 상세 | `nutrition-detail.html` | 선택한 날짜의 탄수화물·단백질·지방·당류 섭취량과 부족/적정/과잉 막대. 음식 DB 에 값이 없는 영양소(식이섬유·나트륨·비타민 등)는 '준비 중'으로 표시 |
| 신체 변화 기록 | `weight-tracker.html` | 체중·골격근량·체지방률 기록과 그래프<br>**앞으로의 변화 예측**: 30/60/90일 예측을 그래프에 **점선**으로 이어서 표시 (체중은 오차 범위 음영) |
| 운동 기록 | `exercise.html` | 운동 종류·강도·시간 입력 → 소모 열량 자동 계산(MET), 최근 7일 요약, 최근 60일 기록 |

모든 화면은 **PC / 태블릿 / 모바일**(1024px, 640px 기준) 레이아웃을 지원합니다.

---

## 기술 스택

- **Java 20**, **Apache Tomcat 10.1** (`jakarta.servlet`), Eclipse Dynamic Web Project
- **MySQL 8**, HikariCP 5.1 (커넥션 풀)
- Gson 2.10 (JSON), jakarta.mail 2.0 + jakarta.activation 2.0 (Gmail SMTP), SLF4J
- 프론트엔드: HTML / CSS / 순수 JavaScript (프레임워크 없음, 그래프는 Canvas 로 직접 그림)
- 구조: Controller(Servlet) – DAO – DTO

### 백엔드
- **Java 20 + Jakarta Servlet (Tomcat 10.1)**: Spring 없이 `@WebServlet` 으로 REST 형태의 JSON API 구성
- **MVC 구조 (Controller–DAO–DTO)**: 요청 처리 / DB 접근 / 데이터 전달을 분리. DTO 는 Java `record` + 입력값 검증
- **MySQL 8 + HikariCP**: 커넥션 풀로 연결 재사용, 서버 종료 시 Listener 가 풀 정리
- **JDBC PreparedStatement**: 모든 쿼리를 파라미터 바인딩으로 처리해 SQL 인젝션 방지. 같은 날짜 신체 기록은 `ON DUPLICATE KEY UPDATE` 로 갱신
- **Gson**: JSON 요청·응답 변환
- **세션 인증**: 로그인 상태를 서버 세션(쿠키)에 유지하고, 모든 API 가 본인 데이터만 조회·수정하도록 이메일 조건을 붙임
- **보안**: 비밀번호·본인 확인 답변 해시 저장, 인증 코드 3분 만료 · 15분에 3회 발송 제한 · 5회 오입력 시 무효, 아이디 찾기 결과 이메일 마스킹, 비밀번호 재설정은 코드 확인 기록이 세션에 있어야 진행
- **jakarta.mail (Gmail SMTP)**: 앱 비밀번호로 인증 코드 메일 발송
- **NoCacheFilter**: 화면 파일에 `Cache-Control: no-cache` 를 붙여 수정 내용이 Ctrl+F5 없이 반영되게 함

### 프론트엔드
- **순수 HTML/CSS/JavaScript**: 프레임워크 없이 `fetch` 로 API 호출. `api.js` 가 공통 호출과 세션 만료(401) 처리 담당
- **Canvas 그래프 직접 구현**: 날짜 기준 x축, 실선(실제 기록) + 점선(예측) + 오차 범위 음영
- **반응형 디자인**: PC(1025px 이상) / 태블릿(641~1024px) / 모바일(640px 이하)을 CSS Grid·Flex 로 배치. 버튼 글꼴·굵기·모양은 `style.css` 공통 규칙으로 통일
- **음식 자동완성**: 입력 시 DB 후보 검색, Tab·방향키로 선택

---

## 프로젝트 구조

```
src/main/java/com/diet/app
├─ controller/   API (Servlet)
│  ├─ AuthServlet         /api/auth/*          회원가입·로그인·세션·아이디 찾기·비밀번호 재설정·탈퇴
│  ├─ DietServlet         /api/diets/*         식단 기록 CRUD, 일일 요약, 기간 통계
│  ├─ FoodServlet         /api/foods           음식 검색
│  ├─ BodyMetricsServlet  /api/body-metrics/*  신체 기록 CRUD + 변화 예측
│  └─ ExerciseServlet     /api/exercises/*     운동 기록 CRUD, 운동 종류(MET)
├─ dao/          DB 접근 (UserDAO, DietDAO, FoodDAO, BodyMetricsDAO, ExerciseDAO)
├─ dto/          데이터 전달 객체 (record) + 입력값 검증
└─ util/
   ├─ DBConnection        HikariCP 커넥션 풀
   ├─ CalBMR              Mifflin-St Jeor 기초대사량 × 활동 계수 → 목표(감량 −500 / 유지·근육 증가 0) → 성별 최소 열량
   ├─ BodyForecast        체성분 변화 예측 모델 (아래 설명)
   ├─ NutCalculator       섭취량에 따른 열량·탄수화물·단백질·지방·당 계산, BMI
   ├─ PasswordEncoder     비밀번호 해시
   ├─ EmailUtil           Gmail SMTP 인증 메일
   ├─ VerificationManager 인증 코드 저장·만료(3분)·발송 제한·시도 횟수
   ├─ NoCacheFilter       화면 파일 캐시 방지
   └─ AppLifecycleListener 서버 종료 시 커넥션 풀 정리

src/main/webapp
├─ *.html        화면
├─ css/style.css 공통 스타일 (버튼 규칙 포함)
└─ js/
   ├─ api.js           서버 호출 공통 모듈 (세션 만료 처리 포함)
   ├─ data-adapter.js  API 응답 ↔ 화면 데이터 변환
   ├─ auth.js          로그인/회원가입/아이디 찾기/비밀번호 재설정
   ├─ food-service.js  음식 자동완성 검색
   ├─ nutrition.js     영양소 적정 범위·섭취 상태·탄단지 비율 계산 (대시보드 카드, 영양정보 상세 공통)
   └─ ui-effects.js    화면 효과
```

**요청 흐름**: 화면(JS) → `api.js` → Servlet(Controller) → DAO → MySQL → JSON 응답 → `data-adapter.js` 가 화면용 데이터로 변환

---

## 계산식과 근거 논문

근거는 **신뢰도 높은 자료(진료 가이드라인, 메타분석, 국제 표준 자료, 다수의 독립 검증을 거친 공식·모델)만** 사용했습니다. 대괄호 번호는 맨 아래 [출처](#출처)의 번호입니다.

| 용도 | 계산식 | 근거 |
|---|---|---|
| 기초대사량 (목표 칼로리·예측 공통) | 남: 10×체중 + 6.25×키 − 5×나이 + 5<br>여: … − 161 | Mifflin MD et al. (1990) [1] |
| 목표: 체중 감량 | 유지 열량 − 500 kcal/일 | 2013 AHA/ACC/TOS 비만 관리 가이드라인의 "하루 500 또는 750 kcal 부족" 중 완만한 쪽 [2] |
| 목표: 근육량 증가 | 유지 열량 (초과 열량 없음) | 적정 초과 열량을 제시한 신뢰도 높은 근거가 없어 유지 열량으로 둠. 근육 증가 근거가 확실한 것은 **근력 운동 + 단백질 1.6 g/kg/일** [5][6] |
| 최소 섭취 열량 | 여성 1,200 · 남성 1,500 kcal | 가이드라인의 감량 식단 하한 (여성 1,200~1,500 / 남성 1,500~1,800 kcal) [2] |
| 음식 열량 | 섭취 g × (100g당 kcal) ÷ 100 | DB 의 100g 기준 영양 성분 |
| 영양소 적정 범위 | 목표 칼로리 × 에너지적정비율 ÷ (탄·단·당 4 / 지방 9 kcal/g)<br>탄수화물 50~65% · 단백질 10~20% · 지방 15~30% · 총당류 20% 이내 | 2025 한국인 영양소 섭취기준 (성인) [7] |
| 탄단지 섭취 비율 | 각 영양소 열량 ÷ (탄·단·지 열량 합) | 위와 같은 kcal/g 환산 |
| 운동 소모 열량 | MET × 체중(kg) × 시간(h) | 2024 Adult Compendium 측정값 [3] (종류·강도별 코드는 `ExerciseDAO` 주석) |
| 동적 체중 변화 | 하루 단위 (섭취 − 소비), 체중이 변하면 소비도 재계산, 활동 열량은 체중에 비례 | Hall KD et al. (2011) [4] |
| 지방·근육 분배 | 제지방 비율 p = 10.4 / (10.4 + 체지방량kg) (Forbes 관계식) | Hall 2011 모델이 사용하는 관계식 [4] |
| 조직 에너지 밀도 | 지방 39.5 MJ/kg(≈9,440 kcal) · 제지방 7.6 MJ/kg(≈1,816 kcal) | Hall KD et al. (2011) [4] |
| 체지방률을 모를 때 | 최종 변화 = 하루 열량 차이 ÷ 24 kcal, 절반이 약 1년에 걸쳐 진행 → **체중만 예측** | Hall 2011 의 경험 규칙 (100 kJ/일 → 약 1 kg) [4] |
| 근력 운동 + 단백질 효과 | 근력 운동을 하고 단백질 1.6 g/kg 이상이면 제지방 증가가 더 큼 | 메타분석 Morton RW et al. (2018) [5]; Nunes EA et al. (2022) [6] |

**근거 문헌 없이 쓴 값 (관례값·설계값)**
- 활동 계수 1.2 · 1.375 · 1.55 · 1.725 · 1.9: 기초대사량에 곱하는 널리 쓰이는 관례값 (이 값을 직접 제시한 1차 연구 없음)
- 근력 운동·단백질 보정 크기 0.5, 근육 증가 상한 주 0.25kg, 실측 보정 반영 50%(최대 ±400kcal), 오차 범위 음식 기록 ±10%, 운동을 따로 기록하면 활동 계수 상한 1.375 — 예측이 과하게 나오지 않도록 보수적으로 정한 설계값
- 탄수화물·지방 비율은 따로 반영하지 않고 총열량만 사용 (모델 단순화)

---

## API 요약

모든 API 는 JSON 으로 주고받으며, `auth` 일부를 제외하면 **로그인 세션이 필요**합니다(없으면 401).

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/auth/session` | 로그인 상태와 사용자 정보 |
| POST | `/api/auth/signup` | 회원가입 (`muscleKg`, `bodyFatPct` 선택) |
| POST | `/api/auth/login` · `/logout` · `/withdraw` | 로그인 / 로그아웃 / 회원 탈퇴 |
| POST | `/api/auth/find-id` | 이름 + 본인 확인 질문/답변 → 가린 이메일 |
| POST | `/api/auth/password/code` → `/password/verify` → `/password/reset` | 인증 코드 발송 → 코드 확인(세션에 10분 기록) → 새 비밀번호 저장 |
| GET | `/api/foods?keyword=` | 음식 검색 |
| GET | `/api/diets/records?date=` · `/daily?date=` · `/stats?startDate=&endDate=` | 날짜별 기록 / 일일 요약(목표 칼로리, 섭취 열량·탄·단·지·당 g) / 기간별 날짜 합계 |
| POST | `/api/diets/record` · `/update` · `/delete` | 식단 등록 / 수정 / 삭제 |
| GET | `/api/body-metrics` | 신체 기록 전체 |
| GET | `/api/body-metrics/forecast?days=60` | 변화 예측 (7~180일) |
| POST | `/api/body-metrics/save` · `/delete` · `/clear` | 신체 기록 저장(같은 날짜는 갱신) / 삭제 / 전체 삭제 |
| GET | `/api/exercises?startDate=&endDate=` · `/types` | 운동 기록(기본 최근 60일) / 운동 종류와 MET |
| POST | `/api/exercises/save` · `/delete` | 운동 기록 추가(소모 열량은 서버 계산) / 삭제 |

---

## 데이터베이스 (`health_db`)

| 테이블 | 내용 |
|---|---|
| `users` | 계정, 성별·나이·키·체중, **평소 활동 강도(`activity_level`)**, 목표 칼로리 |
| `foods` | 음식별 100g 기준 열량·탄수화물·단백질·지방·당, 분류(`category`) |
| `user_preferences` | 선호 음식 분류 (`category` 는 `foods.category` 를 참조하는 외래 키) |
| `intake_logs` / `intake_items` | 날짜·끼니별 식단 기록 / 기록에 포함된 음식과 양(`quantity` = 섭취 g ÷ 100) |
| `body_metrics` | 날짜별 체중·골격근량·체지방률 (사용자당 날짜별 1행) |
| `exercise_logs` | 운동 날짜·종류·강도·시간(`actual_amount`, 분)·소모 열량 |
| `weekly_reports` | 주간 리포트 |

---

## 변화 예측 모델 (`BodyForecast`)

> 최근 기록이 그대로 유지된다고 가정한 **대략적인 추정**이며 의학적 진단이 아닙니다.

**입력**
- 식단: 최근 14일 중 기록한 날의 평균 열량·단백질 (**5일 이상** 기록해야 예측)
- 운동: 최근 14일 운동 소모 열량의 하루 평균, 근력 운동 여부
- 신체: 가장 최근 체중·체지방률·골격근량, 성별·나이·키, 평소 활동 강도

**계산 — 체지방률 기록이 있을 때 (하루 단위 반복, Hall 2011 모델)**
1. 소비 열량 = Mifflin-St Jeor 기초대사량 × 활동 계수 + 운동 열량(체중에 비례)
   (운동을 따로 기록했다면 활동 계수를 최대 1.375 로 낮춰 이중 계산을 줄임)
2. 섭취 − 소비 = 에너지 수지 → 체중 변화. 체중이 바뀌면 소비 열량도 다시 계산
3. 변화량 중 제지방 비율 `p = 10.4 / (10.4 + 체지방량kg)`, 조직 에너지 밀도 지방 9,440 / 제지방 1,816 kcal/kg
4. 근력 운동 + 단백질 1.6 g/kg 이상이면 감량 중 제지방 손실을 줄이고, 증량 중 제지방 비율을 높임. 근육 증가는 주당 0.25kg 상한
5. 골격근량은 처음 측정한 "골격근량 ÷ 제지방량" 비율로 환산 (골격근량 기록이 있을 때)

**계산 — 체지방률 기록이 없을 때**
- Hall 2011 규칙으로 **체중만** 예측: 최종 변화 = (섭취 − 소비) ÷ 24 kcal, 그 절반이 약 1년에 걸쳐 진행. 체지방률·골격근량 예측은 표시하지 않음
- 이 규칙은 장기 변화를 위한 것이라 처음 몇 달의 변화는 체지방률이 있을 때의 모델보다 작게(보수적으로) 나옴 (예: 하루 약 480kcal 부족 시 60일 후 −2.2kg vs −4.3kg)

**공통**
- **실측 보정**: 최근 28일 체중 기록이 14일 이상에 걸쳐 있으면, 실제 체중 추세로 역산한 소비 열량과의 차이의 절반(±400kcal 이내)을 반영. 체중 1kg 의 열량은 위 모델과 같은 기준(시작 시점의 지방·제지방 비율, 또는 Hall 2011 규칙)으로 환산
- 체중 오차 범위: 음식 기록 ±10% 로 다시 계산

---

## 출처

1~6번의 서지 정보와 주소는 PubMed 기록으로 확인했습니다. 7번은 국가 영양 기준(「국민영양관리법」에 따라 5년마다 개정)입니다. "전문"은 누구나 무료로 읽을 수 있는 원문(PubMed Central) 주소입니다.

1. Mifflin MD, St Jeor ST, Hill LA, et al. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990;51(2):241-247.
   - 사용한 곳: 기초대사량 (`CalBMR.restingKcal`)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/2305711/>
   - DOI: <https://doi.org/10.1093/ajcn/51.2.241>
   - 전문: 무료 원문 없음 (PubMed 초록만 공개)
2. Jensen MD, Ryan DH, Apovian CM, et al. 2013 AHA/ACC/TOS guideline for the management of overweight and obesity in adults: a report of the American College of Cardiology/American Heart Association Task Force on Practice Guidelines and The Obesity Society. *Circulation*. 2014;129(25 Suppl 2):S102-S138.
   - 사용한 곳: 감량 −500kcal, 최소 섭취 열량 (`CalBMR`)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/24222017/>
   - DOI: <https://doi.org/10.1161/01.cir.0000437739.71477.ee>
   - 전문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC5819889/>
3. Herrmann SD, Willis EA, Ainsworth BE, et al. 2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities. *J Sport Health Sci*. 2024;13(1):6-12.
   - 사용한 곳: 운동 종류·강도별 MET (`ExerciseDAO`)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/38242596/>
   - DOI: <https://doi.org/10.1016/j.jshs.2023.10.010>
   - 전문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC10818145/>
   - MET 목록 (공식 사이트): <https://pacompendium.com/adult-compendium/>
4. Hall KD, Sacks G, Chandramohan D, et al. Quantification of the effect of energy imbalance on bodyweight. *Lancet*. 2011;378(9793):826-837.
   - 사용한 곳: 동적 체중 변화, Forbes 관계식, 조직 에너지 밀도, 체중만 예측하는 규칙 (`BodyForecast`)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/21872751/>
   - DOI: <https://doi.org/10.1016/S0140-6736(11)60812-X>
   - 전문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC3880593/>
5. Morton RW, Murphy KT, McKellar SR, et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018;52(6):376-384.
   - 사용한 곳: 근력 운동 + 단백질 1.6 g/kg 효과 (`BodyForecast`, `CalBMR` 근육량 증가 목표)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/28698222/>
   - DOI: <https://doi.org/10.1136/bjsports-2017-097608>
   - 전문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC5867436/>
6. Nunes EA, Colenso-Semple L, McKellar SR, et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. *J Cachexia Sarcopenia Muscle*. 2022;13(2):795-810.
   - 사용한 곳: 근력 운동 + 단백질 효과 (`BodyForecast`, `CalBMR` 근육량 증가 목표)
   - PubMed: <https://pubmed.ncbi.nlm.nih.gov/35187864/>
   - DOI: <https://doi.org/10.1002/jcsm.12922>
   - 전문: <https://pmc.ncbi.nlm.nih.gov/articles/PMC8978023/>
7. 보건복지부, 한국영양학회. 2025 한국인 영양소 섭취기준 (Dietary Reference Intakes for Koreans 2025). 2025년 12월 31일 발표.
   - 사용한 곳: 영양소 적정 범위 (`js/nutrition.js`, 대시보드 영양정보 카드, `nutrition-detail.html`)
   - 한국영양학회 (원문 자료실): <https://www.kns.or.kr/>
   - 보건복지부: <https://www.mohw.go.kr/>
   - 개정 내용 보도 (2025-12-31): <https://www.ddaily.co.kr/page/view/2025123111291718936>
