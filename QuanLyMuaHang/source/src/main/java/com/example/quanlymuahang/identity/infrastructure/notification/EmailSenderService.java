package com.example.quanlymuahang.identity.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailSenderService {
    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String fromName;

    public EmailSenderService(@Autowired(required = false) JavaMailSender mailSender,
                              @Value("${app.mail.from:noreply@khvt.local}") String fromEmail,
                              @Value("${app.mail.from-name:Quản lý mua hàng KHVT}") String fromName) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetUrl, int validityMinutes) {
        String subject = "[KHVT] Yêu cầu đặt lại mật khẩu tài khoản";
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e0e0e0; border-radius: 8px;">
                <h2 style="color: #173f35; margin-top: 0;">Hệ thống Quản lý mua hàng KHVT</h2>
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Bạn (hoặc ai đó) vừa yêu cầu đặt lại mật khẩu cho tài khoản <strong>%s</strong>.</p>
                <p>Vui lòng nhấn vào nút bên dưới để tiến hành đổi mật khẩu mới (liên kết có hiệu lực trong <strong>%d phút</strong>):</p>
                <div style="text-align: center; margin: 28px 0;">
                    <a href="%s" style="background-color: #1b6350; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold; display: inline-block;">Đặt lại mật khẩu</a>
                </div>
                <p style="color: #666; font-size: 13px;">Nếu nút trên không bấm được, bạn có thể sao chép và dán liên kết sau vào trình duyệt:<br/><a href="%s" style="color: #1b6350; word-break: break-all;">%s</a></p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;" />
                <p style="color: #888; font-size: 12px; margin-bottom: 0;">Nếu bạn không yêu cầu hành động này, vui lòng bỏ qua email này. Mật khẩu của bạn vẫn an toàn.</p>
            </div>
            """.formatted(
                recipientName != null ? recipientName : toEmail,
                toEmail,
                validityMinutes,
                resetUrl,
                resetUrl,
                resetUrl
        );

        if (mailSender == null) {
            log.info("JavaMailSender is not configured. [DEV RESET LINK] for {}: {}", toEmail, resetUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Sent password reset email successfully to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send email via SMTP to {}: {}. [DEV RESET LINK]: {}", toEmail, e.getMessage(), resetUrl);
        }
    }
}
