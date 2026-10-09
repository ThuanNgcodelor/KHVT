package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.api.*;
import com.booking.system.hr.entity.*;
import com.booking.system.hr.enums.*;
import com.booking.system.hr.repository.*;
import com.booking.system.hr.service.HrEmploymentContractService;
import com.booking.system.hr.service.HrSalaryRaiseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = HrEmployeeProfileHistoryTest.Config.class)
class HrEmployeeProfileHistoryTest {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:hr_profile_history;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
        r.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider", () -> LegacySchemaFilterProvider.class.getName());
    }
    @jakarta.annotation.Resource HrEmployeeRepository employees;
    @jakarta.annotation.Resource HrEmploymentContractRepository contracts;
    @jakarta.annotation.Resource HrEmploymentContractDocumentRepository documents;
    @jakarta.annotation.Resource HrEmployeeMovementRepository movements;
    @jakarta.annotation.Resource HrAuditEventRepository audit;
    @jakarta.annotation.Resource EntityManager em;

    @Test
    void contractArchiveKeepsOldVersionsPaginatesAndRejectsOtherEmployee() {
        var first = employee("TEST-360-A");
        var second = employee("TEST-360-B");
        var c = contract(first, "TEST-CONTRACT-A", HrEmploymentContractStatus.VOIDED);
        var other = contract(second, "TEST-CONTRACT-B", HrEmploymentContractStatus.EFFECTIVE);
        var old = document(c, "old.docx", 1);
        var recent = document(c, "new.docx", 2);
        document(other, "other.docx", 3);
        em.flush(); em.clear();

        var page = documents.findSummaries(c.getId(), PageRequest.of(0, 1, Sort.by("generatedAt").descending()));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().getFirst().id()).isEqualTo(recent.getId());
        assertThat(documents.findSummaries(c.getId(), PageRequest.of(1, 1, Sort.by("generatedAt").descending()))
                .getContent().getFirst().id()).isEqualTo(old.getId());
        assertThat(documents.findDetailById(old.getId()).orElseThrow().getGeneratedDocx()).containsExactly(1);
        assertThat(contracts.findByEmployee_Id(first.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(HrEmploymentContract::getStatus).containsExactly(HrEmploymentContractStatus.VOIDED);

        var service = new HrEmployeeProfileQueryService(employees, contracts, documents, audit,
                mock(HrEmploymentContractService.class), mock(HrActivityQueryService.class),
                mock(HrSalaryRaiseService.class));
        assertThatThrownBy(() -> service.documents(first.getId(), other.getId(), 0, 20))
                .isInstanceOf(HrApiException.class).hasMessageContaining("Không tìm thấy hợp đồng");
        assertThatThrownBy(() -> service.profileAudit("missing", 0, 20)).isInstanceOf(HrApiException.class);
        assertThat(service.documents(first.getId(), c.getId(), -1, 999).size()).isEqualTo(50);
    }

    @Test
    void auditAndMovementQueriesIsolateEmployeeAndKeepDraftCancelledEvents() {
        var first = employee("TEST-360-C");
        var second = employee("TEST-360-D");
        movement(first, HrMovementStatus.DRAFT, 1);
        movement(first, HrMovementStatus.CANCELLED, 2);
        movement(second, HrMovementStatus.CONFIRMED, 3);
        event(first.getId(), "HR_EMPLOYEE");
        event(second.getId(), "HR_EMPLOYEE");
        event(first.getId(), "HR_EMPLOYEE_MOVEMENT");
        em.flush(); em.clear();
        assertThat(movements.findByEmployee_IdOrderByEffectiveDateDesc(first.getId(), PageRequest.of(0, 20)))
                .extracting(HrEmployeeMovement::getStatus)
                .containsExactly(HrMovementStatus.CANCELLED, HrMovementStatus.DRAFT);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByOccurredAtDesc("HR_EMPLOYEE", first.getId(), PageRequest.of(0, 20)))
                .hasSize(1).allMatch(e -> e.getEntityId().equals(first.getId()));
    }

    private HrEmployee employee(String code) {
        var e = new HrEmployee(); e.setEmployeeCode(code); e.setFullName("Fixture " + code); actor(e);
        return employees.save(e);
    }
    private HrEmploymentContract contract(HrEmployee e, String number, HrEmploymentContractStatus status) {
        var c = new HrEmploymentContract(); c.setEmployee(e); c.setContractNumber(number); c.setIdempotencyKey(number);
        c.setContractType(HrEmploymentContractType.INDEFINITE); c.setStatus(status);
        if (status == HrEmploymentContractStatus.EFFECTIVE) {
            c.setActivatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)); c.setActivatedByActor("SYSTEM:test");
        }
        c.setSignDate(LocalDate.of(2026, 1, 1)); c.setEffectiveFrom(LocalDate.of(2026, 1, 1)); actor(c);
        return contracts.save(c);
    }
    private HrEmploymentContractDocument document(HrEmploymentContract c, String name, int day) {
        var d = new HrEmploymentContractDocument(); d.setEmploymentContract(c); d.setWorkforceGroup(HrWorkforceGroup.OFFICE);
        d.setTemplateFileName("fixture.docx"); d.setTemplateSha256("a".repeat(64)); d.setGeneratedFileName(name);
        d.setGeneratedFileSha256("b".repeat(64)); d.setGeneratedDocx(new byte[]{(byte) day}); d.setSnapshotPayload("{}");
        d.setGeneratedAt(LocalDateTime.of(2026, 1, day, 0, 0)); d.setGeneratedByActor("SYSTEM:test"); actor(d);
        return documents.save(d);
    }
    private void movement(HrEmployee e, HrMovementStatus status, int day) {
        var m = new HrEmployeeMovement(); m.setEmployee(e); m.setMovementType(HrMovementType.INCREASE);
        m.setStatus(status); m.setEffectiveDate(LocalDate.of(2026, 1, day)); m.setSourceKind(HrMovementSourceKind.MANUAL);
        m.setToEmployeeStatus(HrEmploymentStatus.ACTIVE);
        if (status == HrMovementStatus.CONFIRMED) {
            m.setConfirmedAt(LocalDateTime.of(2026, 1, day, 0, 0)); m.setConfirmedByActor("SYSTEM:test");
        }
        if (status == HrMovementStatus.CANCELLED) {
            m.setCancelledAt(LocalDateTime.of(2026, 1, day, 0, 0)); m.setCancelledByActor("SYSTEM:test");
        }
        m.setIdempotencyKey(e.getEmployeeCode() + day); actor(m); movements.save(m);
    }
    private void event(String id, String type) {
        var e = new HrAuditEvent(); e.setEntityId(id); e.setEntityType(type); e.setAction("HR_EMPLOYEE_UPDATED");
        e.setActorSubject("SYSTEM:test"); e.setActorRole("ADMIN"); audit.save(e);
    }
    private void actor(HrAuditable e) { e.setCreatedByActor("SYSTEM:test"); e.setUpdatedByActor("SYSTEM:test"); }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.booking.system.hr.entity")
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class Config { }
}
