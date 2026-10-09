package com.booking.system.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpMailService {

    private final JavaMailSender mailSender;
    private final EmailTemplateService template;

    @Value("${spring.mail.username:admin@cfcbooking.io.vn}")
    private String fromEmail;

    public void sendRegisterOtp(String email, String otp) {
        sendOtp(email, "Mã OTP đăng ký CFC Base", "Xác minh email đăng ký",
                "Dùng mã bên dưới để xác minh email và tiếp tục đăng ký tài khoản.", otp);
    }

    public void sendForgotPasswordOtp(String email, String otp) {
        sendOtp(email, "Mã OTP đặt lại mật khẩu CFC Base", "Đặt lại mật khẩu",
                "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.", otp);
    }

    private void sendOtp(String email, String subject, String heading, String message, String otp) {
        String html = template.render(
                subject, heading, null, message, EmailTemplateService.Tone.INFO,
                List.of(new EmailTemplateService.Detail("Mã OTP", otp),
                        new EmailTemplateService.Detail("Hiệu lực", "5 phút")),
                null, null,
                "Không chia sẻ mã OTP này cho bất kỳ ai. Nếu bạn không thực hiện yêu cầu, hãy bỏ qua email.");
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromEmail, "CFC Base");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new MailSendException("Không thể gửi email OTP", e);
        }
    }
}
