package com.booking.system.hr;

import com.booking.system.hr.importer.HrBaselineImportContract;
import com.booking.system.hr.importer.HrBaselineWorkbookParser;
import com.booking.system.hr.importer.HrImportIssueCode;
import com.booking.system.hr.importer.HrImportIssueSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "HR_BASELINE_XLSX", matches = ".+")
class HrRealBaselineContractIT {

    @Test
    void lockedBaselineParsesWithoutExposingPersonnelValues() throws IOException {
        Path path = Path.of(System.getenv("HR_BASELINE_XLSX"));
        byte[] bytes = Files.readAllBytes(path);

        var parsed = new HrBaselineWorkbookParser().parse(bytes);
        new HrBaselineImportContract(HrBaselineImportContract.LOCKED_SHA256).verify(parsed);

        assertThat(parsed.fileSize()).isEqualTo(118_239);
        assertThat(parsed.rows()).hasSize(329);
        assertThat(parsed.rows().stream().map(row -> row.normalizedData().employeeCode()).distinct())
                .hasSize(329);
        assertThat(parsed.rows().stream()
                .flatMap(row -> row.issues().stream())
                .filter(issue -> issue.code() == HrImportIssueCode.VALUE_NEEDS_REVIEW)
                .filter(issue -> issue.cell().startsWith("Z")))
                .hasSize(29);
        assertThat(parsed.rows().stream()
                .filter(row -> row.normalizedData().birthPlaceCurrent() == null))
                .hasSize(111);
        assertThat(parsed.rows().stream()
                .flatMap(row -> row.issues().stream())
                .filter(issue -> issue.severity() == HrImportIssueSeverity.ERROR))
                .isEmpty();
    }
}
