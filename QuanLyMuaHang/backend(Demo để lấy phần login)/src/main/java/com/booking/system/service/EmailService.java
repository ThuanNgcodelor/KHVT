package com.booking.system.service;

import com.booking.system.event.NotificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;

import static com.booking.system.service.EmailTemplateService.Detail;
import static com.booking.system.service.EmailTemplateService.Tone;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final EmailTemplateService template;

    @Value("${spring.mail.username:admin@cfcbooking.io.vn}")
    private String fromEmail;

    @Value("${app.frontend-url:https://cfcbooking.io.vn}")
    private String frontendUrl;

    @Async
    public void sendBookingCreatedEmailToAdmin(String email, String requester, String resource, String title,
            NotificationEvent.BookingEmailDetails booking) {
        send(email, "Yêu cầu đặt " + resource + " mới", template.render(
                "Có yêu cầu đặt " + resource + " mới đang chờ xử lý.",
                "Yêu cầu đặt " + resource + " mới", "Admin",
                "Một yêu cầu mới đã được gửi và đang chờ bạn xem xét.", Tone.INFO,
                bookingDetails(requester, title, booking, null),
                "Xem yêu cầu", url("/admin/approvals"), "Vui lòng kiểm tra thông tin trước khi phê duyệt."));
    }

    @Async
    public void sendBookingApprovedEmail(String email, String resource, String title,
            NotificationEvent.BookingEmailDetails booking) {
        send(email, "Yêu cầu đặt " + resource + " đã được phê duyệt", template.render(
                "Yêu cầu đặt " + resource + " của bạn đã được phê duyệt.",
                "Yêu cầu đã được phê duyệt", null,
                "Yêu cầu đặt " + resource + " của bạn đã được quản trị viên chấp thuận.", Tone.SUCCESS,
                bookingDetails(null, title, booking, "Đã phê duyệt"),
                "Xem lịch đặt", url("/"), null));
    }

    @Async
    public void sendBookingRejectedEmail(String email, String resource, String title, String reason,
            NotificationEvent.BookingEmailDetails booking) {
        send(email, "Yêu cầu đặt " + resource + " không được chấp thuận", template.render(
                "Yêu cầu đặt " + resource + " của bạn đã bị từ chối.",
                "Yêu cầu không được chấp thuận", null,
                "Yêu cầu đặt " + resource + " của bạn đã được quản trị viên xử lý.", Tone.DANGER,
                rejectedBookingDetails(title, reason, booking),
                "Trở về hệ thống", url("/"), "Liên hệ người phê duyệt nếu bạn cần thêm thông tin."));
    }

    @Async
    public void sendProfileUpdateRequestedEmailToAdmin(String email, String requester, String title) {
        send(email, "Yêu cầu cập nhật hồ sơ mới", template.render(
                "Có yêu cầu cập nhật hồ sơ mới đang chờ duyệt.",
                "Yêu cầu cập nhật hồ sơ", "Admin",
                "Một nhân viên vừa gửi yêu cầu cập nhật thông tin hồ sơ.", Tone.INFO,
                List.of(new Detail("Người yêu cầu", requester), new Detail("Nội dung", title)),
                "Duyệt hồ sơ", url("/admin/profile-approvals"), null));
    }

    @Async
    public void sendProfileUpdateApprovedEmail(String email, String title) {
        send(email, "Hồ sơ đã được phê duyệt", template.render(
                "Thông tin hồ sơ của bạn đã được cập nhật.", "Hồ sơ đã được phê duyệt", null,
                "Yêu cầu cập nhật hồ sơ của bạn đã được quản trị viên chấp thuận.", Tone.SUCCESS,
                List.of(new Detail("Nội dung", title), new Detail("Trạng thái", "Đã phê duyệt")),
                "Xem hồ sơ", url("/profile"), null));
    }

    @Async
    public void sendProfileUpdateRejectedEmail(String email, String title, String reason) {
        send(email, "Yêu cầu cập nhật hồ sơ không được chấp thuận", template.render(
                "Yêu cầu cập nhật hồ sơ của bạn đã bị từ chối.",
                "Yêu cầu không được chấp thuận", null,
                "Quản trị viên đã xử lý yêu cầu cập nhật hồ sơ của bạn.", Tone.DANGER,
                List.of(new Detail("Nội dung", title), new Detail("Lý do", fallbackReason(reason))),
                "Xem hồ sơ", url("/profile"), "Liên hệ người phê duyệt nếu bạn cần thêm thông tin."));
    }

    @Async
    public void sendAccountRegistrationPendingEmail(String email, String fullName) {
        send(email, "Tài khoản đang chờ phê duyệt", template.render(
                "Email đã được xác minh và tài khoản đang chờ phê duyệt.",
                "Đăng ký đã được ghi nhận", fullName,
                "Email của bạn đã được xác minh. Tài khoản đang chờ quản trị viên phê duyệt.", Tone.WARNING,
                List.of(new Detail("Email", email), new Detail("Trạng thái", "Chờ phê duyệt")),
                null, null, "Bạn sẽ nhận được email mới ngay khi tài khoản được xử lý."));
    }

    @Async
    public void sendAccountRegistrationApprovedEmail(String email, String fullName) {
        send(email, "Tài khoản đã được phê duyệt", template.render(
                "Tài khoản CFC Base của bạn đã được kích hoạt.",
                "Tài khoản đã được kích hoạt", fullName,
                "Quản trị viên đã phê duyệt tài khoản. Bạn có thể đăng nhập và sử dụng hệ thống ngay.", Tone.SUCCESS,
                List.of(new Detail("Email", email), new Detail("Trạng thái", "Đã kích hoạt")),
                "Đăng nhập", url("/login"), null));
    }

    @Async
    public void sendAccountRegistrationRejectedEmail(String email, String fullName, String reason) {
        send(email, "Yêu cầu đăng ký không được chấp thuận", template.render(
                "Yêu cầu đăng ký tài khoản của bạn không được chấp thuận.",
                "Yêu cầu đăng ký bị từ chối", fullName,
                "Quản trị viên đã xem xét yêu cầu đăng ký tài khoản của bạn.", Tone.DANGER,
                List.of(new Detail("Email", email), new Detail("Lý do", fallbackReason(reason))),
                null, null, "Vui lòng liên hệ quản trị viên nếu bạn cần thêm thông tin."));
    }

    private String fallbackReason(String reason) {
        return reason == null || reason.isBlank() ? "Không có lý do cụ thể" : reason.trim();
    }

    private List<Detail> rejectedBookingDetails(
            String title, String reason, NotificationEvent.BookingEmailDetails booking) {
        List<Detail> details = bookingDetails(null, title, booking, "Bị từ chối");
        details.add(new Detail("Lý do", fallbackReason(reason)));
        return details;
    }

    private List<Detail> bookingDetails(
            String requester, String title, NotificationEvent.BookingEmailDetails booking, String status) {
        List<Detail> details = new ArrayList<>();
        addDetail(details, "Người yêu cầu", requester);
        addDetail(details, "Nội dung", title);
        if (booking != null) {
            addDetail(details, "Phòng / Xe", booking.resourceName());
            addDetail(details, "Địa điểm", booking.location());
            addDetail(details, "Điểm đi", booking.departure());
            addDetail(details, "Điểm đến", booking.destination());
            if (booking.startTime() != null) {
                addDetail(details, "Ngày", booking.startTime().format(DATE_FORMAT));
            }
            if (booking.startTime() != null && booking.endTime() != null) {
                String timeRange = booking.startTime().format(TIME_FORMAT)
                        + " – " + booking.endTime().format(TIME_FORMAT);
                if (!booking.startTime().toLocalDate().equals(booking.endTime().toLocalDate())) {
                    timeRange += " ngày " + booking.endTime().format(DATE_FORMAT);
                }
                addDetail(details, "Thời gian", timeRange);
            }
        }
        addDetail(details, "Trạng thái", status);
        return details;
    }

    private void addDetail(List<Detail> details, String label, String value) {
        if (value != null && !value.isBlank()) {
            details.add(new Detail(label, value));
        }
    }

    private String url(String path) {
        return frontendUrl.replaceAll("/+$", "") + path;
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, "CFC Base");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to prepare email for {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while sending email to {}: {}", to, e.getMessage());
        }
    }
}
