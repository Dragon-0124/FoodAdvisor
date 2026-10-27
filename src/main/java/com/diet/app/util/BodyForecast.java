package com.diet.app.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 현재 식습관·운동량이 유지될 때의 체성분(체중/체지방률/골격근량) 변화 예측 - DB 와 무관한 순수 계산
//
// 근거 (신뢰도 높은 자료만 사용하며, 의학적 진단이 아닙니다)
//  - 체성분 동적 모델: Hall KD et al., Lancet 2011
//      · 체중 변화 중 제지방 비율은 Forbes 관계식 p = 10.4 / (10.4 + 체지방량kg)
//      · 조직 에너지 밀도: 지방 39.5 MJ/kg(≈9,440 kcal), 제지방 7.6 MJ/kg(≈1,816 kcal)
//      · 체중이 변하면 소비 열량도 다시 계산, 활동 소모 열량은 체중에 비례
//      · 체지방률을 모를 때: "하루 100 kJ(≈24 kcal) 차이 → 최종 약 1 kg, 절반은 약 1년" 규칙으로 체중만 예측
//  - 기초대사량: Mifflin-St Jeor (Mifflin et al., 1990)
//  - 근력 운동 + 단백질 1.6 g/kg 이상이면 제지방 증가가 더 큼 (Morton et al., BJSM 2018; Nunes et al., JCSM 2022)
//    → 효과의 방향만 근거이고, 아래 보정 크기(0.5)와 근육 증가 상한은 과대 예측을 막기 위한 설계값입니다.
public final class BodyForecast {

    private static final double FAT_KCAL_PER_KG = 9440;    // 39.5 MJ/kg (Hall 2011)
    private static final double LEAN_KCAL_PER_KG = 1816;   // 7.6 MJ/kg (Hall 2011)
    private static final double FORBES_C = 10.4;
    private static final double KCAL_PER_DAY_PER_KG = 24;  // 100 kJ/일 → 최종 1 kg (Hall 2011)
    private static final double HALF_LIFE_DAYS = 365;      // 변화의 절반에 약 1년 (Hall 2011)
    private static final double PROTEIN_ENOUGH_G_PER_KG = 1.6;

    // 설계값 (근거 문헌 없음, 보수적으로 설정)
    private static final double TRAINED_LEAN_FACTOR = 0.5;               // 근력 운동 + 단백질 충분 시 감량 중 제지방 손실 비율 축소 / 증량 중 최소 제지방 비율
    private static final double MAX_LEAN_GAIN_PER_DAY = 0.25 / 7;       // 근육 증가 상한 주 0.25 kg
    private static final double INTAKE_UNCERTAINTY = 0.10;               // 음식 기록 오차 범위 ±10%

    private BodyForecast() {
    }

    // 예측에 필요한 입력값
    public record Input(
            boolean male, int age, double heightCm,
            double weightKg, Double bodyFatPct, Double muscleKg,    // 최근 측정값 (체지방률/골격근량은 없으면 null)
            double activityMultiplier, boolean exerciseLogged,       // 평소 활동 계수, 기간 내 운동 기록 여부
            double avgIntakeKcal, double avgProteinG,                // 기록한 날 평균 섭취
            double avgExerciseKcalPerDay, boolean strengthTraining,  // 하루 평균 운동 소모 열량, 근력 운동 여부
            double calibrationKcal,                                  // 실측 체중 변화로 보정한 소비 열량 차이 (없으면 0)
            LocalDate startDate, int days) {
    }

    public record Point(LocalDate date, double weight, double weightLow, double weightHigh,
                        Double bodyFatPct, Double muscleKg) {
    }

    // weightOnly: 체지방률 기록이 없어 체중만 예측한 경우
    public record Result(List<Point> points, double estimatedTdee, boolean weightOnly) {
    }

    public static Result run(Input in) {
        boolean weightOnly = in.bodyFatPct() == null;
        double low = in.avgIntakeKcal() * (1 - INTAKE_UNCERTAINTY);
        double high = in.avgIntakeKcal() * (1 + INTAKE_UNCERTAINTY);
        List<Point> points = new ArrayList<>();

        if (weightOnly) {
            double[] middle = simulateWeightOnly(in, in.avgIntakeKcal());
            double[] lowRun = simulateWeightOnly(in, low);
            double[] highRun = simulateWeightOnly(in, high);
            for (int day = 0; day <= in.days(); day++) {
                points.add(new Point(in.startDate().plusDays(day),
                        round2(middle[day]), round2(lowRun[day]), round2(highRun[day]), null, null));
            }
        } else {
            double fat0 = in.weightKg() * in.bodyFatPct() / 100;
            double lean0 = in.weightKg() - fat0;
            Double muscleRatio = in.muscleKg() != null && lean0 > 0 ? in.muscleKg() / lean0 : null;

            double[][] middle = simulateComposition(in, fat0, lean0, in.avgIntakeKcal());
            double[][] lowRun = simulateComposition(in, fat0, lean0, low);
            double[][] highRun = simulateComposition(in, fat0, lean0, high);
            for (int day = 0; day <= in.days(); day++) {
                double fat = middle[day][0];
                double lean = middle[day][1];
                double weight = fat + lean;
                // 그래프가 계단 모양이 되지 않도록 소수 둘째 자리까지 보냅니다. (화면 표시는 첫째 자리)
                points.add(new Point(in.startDate().plusDays(day),
                        round2(weight),
                        round2(lowRun[day][0] + lowRun[day][1]),
                        round2(highRun[day][0] + highRun[day][1]),
                        round2(fat / weight * 100),
                        muscleRatio == null ? null : round2(lean * muscleRatio)));
            }
        }
        return new Result(points, Math.round(formulaTdee(in) + in.calibrationKcal()), weightOnly);
    }

    // 체지방률을 알 때: 하루 단위로 지방/제지방을 나눠 계산 -> [day][0=지방kg, 1=제지방kg]
    private static double[][] simulateComposition(Input in, double fat, double lean, double intake) {
        double[][] out = new double[in.days() + 1][];
        out[0] = new double[]{fat, lean};

        for (int day = 1; day <= in.days(); day++) {
            double weight = fat + lean;
            double balance = intake - dailyExpenditure(in, weight);
            double p = leanFraction(in, fat, weight, balance);

            double weightChange = balance / (p * LEAN_KCAL_PER_KG + (1 - p) * FAT_KCAL_PER_KG);
            double leanChange = p * weightChange;
            double fatChange = (1 - p) * weightChange;

            // 근육 증가 상한: 넘는 만큼의 에너지는 지방으로 저장
            if (leanChange > MAX_LEAN_GAIN_PER_DAY) {
                fatChange += (leanChange - MAX_LEAN_GAIN_PER_DAY) * LEAN_KCAL_PER_KG / FAT_KCAL_PER_KG;
                leanChange = MAX_LEAN_GAIN_PER_DAY;
            }

            fat = Math.max(fat + fatChange, weight * 0.03);  // 생리적 최저 체지방 방어
            lean += leanChange;
            out[day] = new double[]{fat, lean};
        }
        return out;
    }

    // 체지방률을 모를 때: Hall 2011 규칙 - 최종 변화 = 열량 차이 ÷ 24, 절반이 약 1년에 걸쳐 일어남
    private static double[] simulateWeightOnly(Input in, double intake) {
        double start = in.weightKg();
        double finalWeight = start + (intake - dailyExpenditure(in, start)) / KCAL_PER_DAY_PER_KG;
        double[] out = new double[in.days() + 1];
        for (int day = 0; day <= in.days(); day++) {
            out[day] = finalWeight + (start - finalWeight) * Math.pow(0.5, day / HALF_LIFE_DAYS);
        }
        return out;
    }

    // 하루 소비 열량 = 기초대사량(Mifflin) × 활동 계수 + 운동 열량(체중 비례) + 실측 보정
    private static double dailyExpenditure(Input in, double weight) {
        return CalBMR.restingKcal(in.male(), weight, in.heightCm(), in.age()) * lifeFactor(in)
                + in.avgExerciseKcalPerDay() * weight / in.weightKg()
                + in.calibrationKcal();
    }

    // 체중 변화 중 제지방 비율 (Forbes) + 근력 운동·단백질 보정
    private static double leanFraction(Input in, double fat, double weight, double balance) {
        double p = FORBES_C / (FORBES_C + fat);
        boolean trained = in.strengthTraining() && in.avgProteinG() / weight >= PROTEIN_ENOUGH_G_PER_KG;
        if (!trained) return p;
        return balance < 0 ? p * TRAINED_LEAN_FACTOR : Math.max(p, TRAINED_LEAN_FACTOR);
    }

    // 운동을 따로 기록했다면 평소 활동 계수에서 운동 몫을 덜어 이중 계산을 줄입니다. (설계값)
    private static double lifeFactor(Input in) {
        return in.exerciseLogged() ? Math.min(in.activityMultiplier(), 1.375) : in.activityMultiplier();
    }

    // 보정 전 공식 TDEE (시작 체중 기준)
    public static double formulaTdee(Input in) {
        return CalBMR.restingKcal(in.male(), in.weightKg(), in.heightCm(), in.age()) * lifeFactor(in)
                + in.avgExerciseKcalPerDay();
    }

    // 실측 체중 1kg 변화에 해당하는 열량 (모델과 같은 기준)
    //  체지방률을 알면 시작 시점의 지방/제지방 비율로, 모르면 Hall 2011 규칙의 초기 변화 속도로 환산
    public static double energyPerKg(Input in) {
        if (in.bodyFatPct() == null) {
            return KCAL_PER_DAY_PER_KG * HALF_LIFE_DAYS / Math.log(2);
        }
        double fat = in.weightKg() * in.bodyFatPct() / 100;
        double p = FORBES_C / (FORBES_C + fat);
        return p * LEAN_KCAL_PER_KG + (1 - p) * FAT_KCAL_PER_KG;
    }

    // 실측 보정: 최근 체중 추세(최소제곱 기울기)와 평균 섭취로 실제 소비 열량을 역산합니다.
    //  관측 TDEE = 평균 섭취 − (하루 체중 변화kg × energyPerKg)
    //  보정값 = (관측 − 공식) 의 절반, ±400kcal 로 제한 (설계값: 기록 누락·수분 변동에 과민하지 않게)
    public static double calibration(Map<LocalDate, Double> weights, double avgIntakeKcal,
                                     double formulaTdee, double energyPerKg) {
        if (weights.size() < 2) return 0;
        LocalDate first = weights.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate last = weights.keySet().stream().max(LocalDate::compareTo).orElseThrow();
        if (first.until(last).getDays() < 14) return 0;

        double n = weights.size(), sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (Map.Entry<LocalDate, Double> entry : weights.entrySet()) {
            double x = first.until(entry.getKey()).getDays();
            double y = entry.getValue();
            sumX += x; sumY += y; sumXY += x * y; sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) return 0;
        double slopeKgPerDay = (n * sumXY - sumX * sumY) / denominator;

        double observedTdee = avgIntakeKcal - slopeKgPerDay * energyPerKg;
        double adjust = (observedTdee - formulaTdee) / 2;
        return Math.max(-400, Math.min(400, adjust));
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
