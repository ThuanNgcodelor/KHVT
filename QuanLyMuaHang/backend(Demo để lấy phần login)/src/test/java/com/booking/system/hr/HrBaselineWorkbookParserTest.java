package com.booking.system.hr;

import com.booking.system.hr.importer.HrBaselineImportException;
import com.booking.system.hr.importer.HrBaselineWorkbookParser;
import com.booking.system.hr.importer.HrImportIssueCode;
import com.booking.system.hr.importer.HrImportIssueSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrBaselineWorkbookParserTest {

    private final HrBaselineWorkbookParser parser = new HrBaselineWorkbookParser();

    @Test
    void parsesOnlyTheExplicitT6ContractAndConvertsNaToReviewWarning() {
        var parsed = parser.parse(HrBaselineWorkbookFixture.validWorkbook());

        assertThat(parsed.rows()).hasSize(329);
        assertThat(parsed.rows().stream().map(row -> row.normalizedData().employeeCode()).distinct())
                .hasSize(329);
        assertThat(parsed.rows().stream()
                .flatMap(row -> row.issues().stream())
                .filter(issue -> issue.code() == HrImportIssueCode.VALUE_NEEDS_REVIEW))
                .hasSize(29);
        assertThat(parsed.rows().stream()
                .flatMap(row -> row.issues().stream())
                .filter(issue -> issue.severity() == HrImportIssueSeverity.ERROR))
                .isEmpty();
        assertThat(parsed.rows().getFirst().normalizedData().birthPlaceCurrent()).isNull();
        assertThat(parsed.rows().getFirst().rawCells().get("Z").value()).isEqualTo("#N/A");
    }

    @Test
    void rejectsHeaderFormulaAndDataOutsideLockedRange() {
        assertCode(HrBaselineWorkbookFixture.withWrongHeader(), "HEADER_MISMATCH");
        assertCode(HrBaselineWorkbookFixture.withFormula(), "FORMULA_NOT_ALLOWED");
        assertCode(HrBaselineWorkbookFixture.withDataAfterContract(), "DATA_OUTSIDE_CONTRACT");
    }

    @Test
    void numericIdentifierIsPreservedAsTextButRequiresReview() {
        var first = parser.parse(HrBaselineWorkbookFixture.withNumericLegacyIdentity()).rows().getFirst();

        assertThat(first.normalizedData().legacyIdentityNumber()).isEqualTo("123456789");
        assertThat(first.issues()).anyMatch(issue ->
                issue.code() == HrImportIssueCode.NUMERIC_IDENTIFIER_COERCED
                        && issue.cell().equals("T5")
        );
    }

    @Test
    void workforceBaselineContains339EmployeesInVisibleJuneSheet() {
        byte[] bundle = HrBaselineWorkbookFixture.workforceSnapshotWorkbook();

        var baseline = parser.parseWorkforceSnapshot(bundle);

        assertThat(baseline.rows()).hasSize(339);
        assertThat(baseline.rows().stream()
                .map(row -> row.normalizedData().employeeCode()))
                .contains("U012")
                .doesNotContain("T001", "T002");
    }

    private void assertCode(byte[] workbook, String code) {
        assertThatThrownBy(() -> parser.parse(workbook))
                .isInstanceOf(HrBaselineImportException.class)
                .extracting(error -> ((HrBaselineImportException) error).getCode())
                .isEqualTo(code);
    }
}
