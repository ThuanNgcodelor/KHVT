package com.booking.system.hr.service;

import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.enums.HrProductionAttendanceShiftStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HrProductionShiftMatcherTest {
    private final HrProductionShiftMatcher matcher = new HrProductionShiftMatcher();
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Test
    void rawTimeOnlyMatchingShowsWhyTheDay18OutageNeedsAManualAnchor() throws Exception {
        Path workbook = Path.of("..", "CongXn.xlsx");
        var parsed = new com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser()
                .parse(Files.readAllBytes(workbook), "2026-08");
        List<HrProductionShiftMatcher.WorkDay> days = parsed.days().stream()
                .filter(value -> value.employeeCode().equals("B124"))
                .map(value -> new HrProductionShiftMatcher.WorkDay(value.sourceRowNumber(), value.workDate(),
                        java.util.stream.IntStream.range(0, value.punches().size())
                                .mapToObj(index -> new HrProductionShiftMatcher.Punch(
                                        value.sourceRowNumber() + "-" + index, value.punches().get(index).punchedAt())).toList(), false))
                .toList();
        var results = matcher.match(days, List.of(dayShift(), night()), List.of(
                rule("day-1", "day", "12:00", "14:59", "1", 300),
                rule("day-15", "day", "15:00", "17:44", "1.5", 200),
                rule("day-2", "day", "17:45", "23:59", "2", 100),
                rule("night-credit", "night", "04:00", "05:59", "1.5", 100)));

        assertThat(days).hasSize(31);
        assertThat(results).hasSize(31);
        assertThat(results.stream().map(HrProductionShiftMatcher.MatchResult::workValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("45");
        // 19 is deliberately the ambiguous raw result: after the missing morning punch on day 18,
        // a time-only matcher cannot know that 17:24 is a day checkout rather than a night check-in.
        assertThat(results).filteredOn(value -> "CN_18_5".equals(value.shiftCode())).hasSize(19);
        assertThat(results.stream().map(HrProductionShiftMatcher.MatchResult::nightAllowanceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("950000");
        assertThat(results.stream().filter(value -> value.workDate().getDayOfMonth() == 9).findFirst().orElseThrow().workValue())
                .isEqualByComparingTo("0");
        for (int day : List.of(25, 30, 31)) {
            assertThat(results.stream().filter(value -> value.workDate().getDayOfMonth() == day).findFirst().orElseThrow().workValue())
                    .as("day " + day).isEqualByComparingTo("1.5");
        }
        assertThat(results.get(30).status()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThat(results.get(30).checkOutAt()).isNull();
    }

    @Test
    void productionDayUsesConfiguredDiscreteCredits() {
        assertDayCredit("14:00", "1");
        assertDayCredit("16:00", "1.5");
        assertDayCredit("17:34", "1.5");
        assertDayCredit("17:44", "1.5");
        assertDayCredit("17:45", "2");
        assertDayCredit("17:59", "2");
        assertDayCredit("18:01", "2");
    }

    @Test
    void kcsSecondShiftIsRecognizedInsteadOfNoPunch() {
        LocalDate day = LocalDate.of(2026, 8, 12);
        var result = matcher.match(List.of(day(1, day, "13:02", "21:58")), List.of(kcsCa2()), List.of(
                rule("kcs-credit", "kcs-ca2", "20:30", "23:59", "1", 100))).get(0);

        assertThat(result.shiftCode()).isEqualTo("KCS_CA2");
        assertThat(result.workValue()).isEqualByComparingTo("1");
        assertThat(result.status()).isEqualTo(HrProductionAttendanceShiftStatus.AUTO_MATCHED);
    }

    @Test
    void acceptsAllConfirmedNightWindowCombinations() {
        for (String start : List.of("17:30", "18:30")) {
            for (String end : List.of("04:30", "05:30")) {
                LocalDate day = LocalDate.of(2026, 8, 18);
                List<HrProductionShiftMatcher.WorkDay> days = List.of(
                        day(1, day, start), day(2, day.plusDays(1), end));
                var first = matcher.match(days, List.of(night()), List.of(
                        rule("night-credit", "night", "04:00", "05:59", "1.5", 100))).get(0);

                assertThat(first.shiftCode()).as(start + " - " + end).isEqualTo("CN_18_5");
                assertThat(first.workValue()).isEqualByComparingTo("1.5");
                assertThat(first.nightAllowanceAmount()).isEqualByComparingTo("50000");
                assertThat(first.checkOutAt().toLocalDate()).isEqualTo(day.plusDays(1));
            }
        }
    }

    @Test
    void consumesNightCheckoutOnlyOnceAndKeepsEverySourceDay() {
        LocalDate day = LocalDate.of(2026, 8, 4);
        List<HrProductionShiftMatcher.WorkDay> days = List.of(
                day(4, day, "17:20"),
                day(5, day.plusDays(1), "05:10", "17:25"),
                day(6, day.plusDays(2), "04:50"));
        var results = matcher.match(days, List.of(night()), List.of(
                rule("night-credit", "night", "04:00", "05:59", "1.5", 100)));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).checkOutAt().toLocalTime()).isEqualTo(LocalTime.of(5, 10));
        assertThat(results.get(1).checkInAt().toLocalTime()).isEqualTo(LocalTime.of(17, 25));
        assertThat(results.get(1).checkOutAt().toLocalTime()).isEqualTo(LocalTime.of(4, 50));
        assertThat(results.get(2).status()).isEqualTo(HrProductionAttendanceShiftStatus.NO_PUNCH);
    }

    @Test
    void missingNightCheckoutIsReviewWithNoFakeCheckout() {
        LocalDate day = LocalDate.of(2026, 8, 31);
        var result = matcher.match(List.of(day(31, day, "17:08")), List.of(night()), List.of(
                rule("night-credit", "night", "04:00", "05:59", "1.5", 100))).get(0);

        assertThat(result.status()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThat(result.checkInAt()).isNotNull();
        assertThat(result.checkOutAt()).isNull();
        assertThat(result.workValue()).isEqualByComparingTo("1.5");
        assertThat(result.nightAllowanceAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void matchesEndOfMonthNightWithSupplementalNextMonthPunch() {
        LocalDate august31 = LocalDate.of(2026, 8, 31);
        var result = matcher.match(List.of(day(31, august31, "17:30")),
                List.of(new HrProductionShiftMatcher.Punch("sep-1", LocalDateTime.of(2026, 9, 1, 5, 30))),
                List.of(night()), List.of(rule("night-credit", "night", "04:00", "05:59", "1.5", 100))).get(0);

        assertThat(result.workValue()).isEqualByComparingTo("1.5");
        assertThat(result.checkOutAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 5, 30));
        assertThat(result.resolutionType().name()).isEqualTo("MONTH_BOUNDARY");
    }

    private void assertDayCredit(String checkout, String expected) {
        LocalDate date = LocalDate.of(2026, 8, 10);
        var result = matcher.match(List.of(day(1, date, "06:00", checkout)), List.of(dayShift()), List.of(
                rule("day-1", "day", "12:00", "14:59", "1", 300),
                rule("day-15", "day", "15:00", "17:44", "1.5", 200),
                rule("day-2", "day", "17:45", "23:59", "2", 100))).get(0);
        assertThat(result.workValue()).isEqualByComparingTo(expected);
    }

    private HrProductionShiftMatcher.WorkDay day(int row, LocalDate date, String... times) {
        List<HrProductionShiftMatcher.Punch> punches = java.util.stream.IntStream.range(0, times.length)
                .mapToObj(index -> new HrProductionShiftMatcher.Punch(row + "-" + index,
                        LocalDateTime.of(date, LocalTime.parse(times[index])))).toList();
        return new HrProductionShiftMatcher.WorkDay(row, date, punches, false);
    }

    private HrProductionShiftMatcher.ShiftPolicy dayShift() {
        return policy("day", "CN_DAY", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                "06:00", "18:00", "04:00", "09:30", "12:00", "23:59", false, "0", 100);
    }

    private HrProductionShiftMatcher.ShiftPolicy night() {
        return policy("night", "CN_18_5", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                "18:00", "05:00", "16:45", "18:59", "04:00", "05:59", true, "50000", 200);
    }

    private HrProductionShiftMatcher.ShiftPolicy kcsCa2() {
        return policy("kcs-ca2", "KCS_CA2", HrAttendancePolicyGroup.KCS,
                "13:00", "22:00", "11:30", "14:30", "20:30", "23:59", false, "0", 200);
    }

    private HrProductionShiftMatcher.ShiftPolicy policy(String id, String code, HrAttendancePolicyGroup group,
                                                         String standardStart, String standardEnd, String inFrom,
                                                         String inUntil, String outFrom, String outUntil,
                                                         boolean overnight, String allowance, int priority) {
        return new HrProductionShiftMatcher.ShiftPolicy(id, code, group, LocalTime.parse(standardStart),
                LocalTime.parse(standardEnd), LocalTime.parse(inFrom), LocalTime.parse(inUntil),
                LocalTime.parse(outFrom), LocalTime.parse(outUntil), overnight, new BigDecimal(allowance),
                priority, FROM, null);
    }

    private HrProductionShiftMatcher.CreditRule rule(String id, String policyId, String from, String until,
                                                      String credit, int priority) {
        return new HrProductionShiftMatcher.CreditRule(id, policyId, LocalTime.parse(from), LocalTime.parse(until),
                new BigDecimal(credit), priority, FROM, null);
    }
}
