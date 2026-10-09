package com.booking.system.hr;

import com.booking.system.hr.importer.HrBaselineWorkbookParser;
import com.booking.system.hr.importer.HrImportIssueSeverity;
import com.booking.system.hr.importer.HrWorkforceSnapshotContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "HR_WORKFORCE_SNAPSHOT_XLSX", matches = ".+")
class HrRealWorkforceSnapshotContractIT {

    @Test
    void lockedSnapshotParsesAs339ActiveEmployeesWithoutDataErrors() throws IOException {
        byte[] bytes = Files.readAllBytes(
                Path.of(System.getenv("HR_WORKFORCE_SNAPSHOT_XLSX"))
        );

        var parser = new HrBaselineWorkbookParser();
        var parsed = parser.parseWorkforceSnapshot(bytes);
        new HrWorkforceSnapshotContract(HrWorkforceSnapshotContract.LOCKED_SHA256)
                .verify(parsed);

        assertThat(parsed.rows()).hasSize(339);
        assertThat(parsed.rows().stream()
                .map(row -> row.normalizedData().employeeCode())
                .distinct()).hasSize(339);
        assertThat(parsed.rows().stream()
                .flatMap(row -> row.issues().stream())
                .filter(issue -> issue.severity() == HrImportIssueSeverity.ERROR))
                .isEmpty();

        Set<String> codes = parsed.rows().stream()
                .map(row -> row.normalizedData().employeeCode())
                .collect(Collectors.toSet());
        assertThat(codes)
                .contains("A440", "A441", "G074", "G083");
        assertThat(parsed.rows().stream()
                .filter(row -> "G083".equals(row.normalizedData().employeeCode()))
                .findFirst()
                .orElseThrow()
                .normalizedData()
                .citizenIdentityNumber()).isNull();
    }
}
