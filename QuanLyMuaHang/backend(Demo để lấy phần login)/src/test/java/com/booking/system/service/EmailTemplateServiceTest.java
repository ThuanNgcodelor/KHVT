package com.booking.system.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private final EmailTemplateService template = new EmailTemplateService();

    @Test
    void rendersConsistentResponsiveTemplateAndEscapesUserContent() {
        String html = template.render(
                "Thông báo", "Đã xử lý", "Nguyễn <Admin>", "Nội dung & kết quả",
                EmailTemplateService.Tone.SUCCESS,
                List.of(new EmailTemplateService.Detail("Lý do", "Không dùng <script>")),
                "Mở hệ thống", "https://cfcbooking.io.vn/login", "Ghi chú");

        assertThat(html)
                .contains("CFC Base")
                .contains("width=\"100%\"")
                .contains("Nguyễn &lt;Admin&gt;")
                .contains("Nội dung &amp; kết quả")
                .contains("Không dùng &lt;script&gt;")
                .contains("https://cfcbooking.io.vn/login")
                .doesNotContain("Không dùng <script>");
    }
}
