package com.booking.system.hr;
import com.booking.system.hr.service.TelegramBotClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TelegramPayrollResponseTest {
    @Test void onlySuccessfulTelegramAcknowledgementIsSent() {
        assertThat(TelegramBotClient.classifyPayrollResponse(200, "{\"ok\":true,\"result\":{\"message_id\":12}}").sent()).isTrue();
        assertThat(TelegramBotClient.classifyPayrollResponse(200, "{}").error()).startsWith("UNCERTAIN:");
        assertThat(TelegramBotClient.classifyPayrollResponse(500, "gateway error").error()).startsWith("UNCERTAIN:");
    }
    @Test void rateLimitAndRejectedRequestsAreDistinguishedWithoutRawResponse() {
        assertThat(TelegramBotClient.classifyPayrollResponse(429, "{\"ok\":false,\"error_code\":429,\"parameters\":{\"retry_after\":30}}").error())
                .startsWith("RETRYABLE:").contains("30 giây");
        assertThat(TelegramBotClient.classifyPayrollResponse(403, "{\"ok\":false,\"error_code\":403,\"description\":\"sensitive text\"}").error())
                .startsWith("REJECTED:").doesNotContain("sensitive");
    }
    @Test void payrollCaptionEscapesHtmlAndKeepsPayrollColumnsMonospaced() {
        assertThat(TelegramBotClient.payrollHtml("A < B & C > D"))
                .isEqualTo("<pre>A &lt; B &amp; C &gt; D</pre>");
    }
}
