package com.booking.system.hr.api;

import com.booking.system.hr.service.HrProductionAttendanceReportService;
import com.booking.system.hr.service.HrProductionAttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HrProductionAttendanceFeatureFlagTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void controllerIsAvailableByDefaultForLocalDevelopment() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(HrProductionAttendanceController.class));
    }

    @Test
    void controllerIsNotRegisteredWhenProductionAttendanceIsDisabled() {
        contextRunner.withPropertyValues("app.hr.attendance.production.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(HrProductionAttendanceController.class));
    }

    @Test
    void controllerIsRegisteredWhenProductionAttendanceIsEnabled() {
        contextRunner.withPropertyValues("app.hr.attendance.production.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(HrProductionAttendanceController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(HrProductionAttendanceController.class)
    static class TestConfiguration {
        @Bean
        HrProductionAttendanceService productionAttendanceService() {
            return mock(HrProductionAttendanceService.class);
        }

        @Bean
        HrProductionAttendanceReportService productionAttendanceReportService() {
            return mock(HrProductionAttendanceReportService.class);
        }

        @Bean
        HrActorResolver hrActorResolver() {
            return new HrActorResolver();
        }
    }
}
