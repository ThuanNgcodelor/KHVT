package com.booking.system.service;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.List;

@Component
public class EmailTemplateService {

  public enum Tone {
    INFO("#2563eb", "#eff6ff", "#bfdbfe"),
    SUCCESS("#15803d", "#f0fdf4", "#bbf7d0"),
    WARNING("#b45309", "#fffbeb", "#fde68a"),
    DANGER("#b91c1c", "#fef2f2", "#fecaca");

    private final String accent;
    private final String background;
    private final String border;

    Tone(String accent, String background, String border) {
      this.accent = accent;
      this.background = background;
      this.border = border;
    }
  }

  public record Detail(String label, String value) {
  }

  public String render(
      String preheader,
      String heading,
      String greetingName,
      String message,
      Tone tone,
      List<Detail> details,
      String actionLabel,
      String actionUrl,
      String note) {
    String safeGreeting = greetingName == null || greetingName.isBlank()
        ? "Xin chào,"
        : "Xin chào " + escape(greetingName) + ",";
    String detailRows = renderDetails(details, tone);
    String action = actionLabel == null || actionUrl == null ? ""
        : """
            <table role="presentation" border="0" cellpadding="0" cellspacing="0" style="margin:28px auto 8px">
              <tr><td style="border-radius:8px;background:%s">
                <a href="%s" style="display:inline-block;padding:13px 24px;color:#ffffff;text-decoration:none;font-size:14px;font-weight:700">%s</a>
              </td></tr>
            </table>
            """
            .formatted(tone.accent, escapeAttribute(actionUrl), escape(actionLabel));
    String noteBlock = note == null || note.isBlank() ? "" : """
        <p style="margin:20px 0 0;color:#64748b;font-size:13px;line-height:1.6">%s</p>
        """.formatted(escape(note));

    return """
        <!doctype html>
        <html lang="vi">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,'Segoe UI',sans-serif;color:#0f172a">
          <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s</div>
          <table role="presentation" width="100%%" border="0" cellpadding="0" cellspacing="0" style="background:#f1f5f9">
            <tr><td align="center" style="padding:24px 12px">
              <table role="presentation" width="100%%" border="0" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;overflow:hidden">
                <tr><td style="padding:24px 28px;background:#0f2f57;border-bottom:4px solid #16a34a">
                  <div style="font-size:22px;line-height:1.2;font-weight:800;color:#ffffff">CFC Base</div>
                  <div style="margin-top:5px;font-size:12px;color:#cbd5e1">Hệ thống đặt phòng họp &amp; xe nội bộ</div>
                </td></tr>
                <tr><td style="padding:30px 28px">
                  <div style="display:inline-block;padding:5px 10px;border-radius:999px;background:%s;color:%s;font-size:12px;font-weight:700">THÔNG BÁO HỆ THỐNG</div>
                  <h1 style="margin:16px 0 18px;font-size:24px;line-height:1.35;color:#0f172a">%s</h1>
                  <p style="margin:0 0 12px;font-size:15px;line-height:1.7;color:#334155">%s</p>
                  <p style="margin:0;font-size:15px;line-height:1.7;color:#334155">%s</p>
                  %s
                  %s
                  %s
                </td></tr>
                <tr><td style="padding:20px 28px;background:#f8fafc;border-top:1px solid #e2e8f0;text-align:center">
                  <p style="margin:0;color:#64748b;font-size:12px;line-height:1.6">Đây là email tự động từ CFC Base. Vui lòng không trả lời email này.</p>
                  <p style="margin:6px 0 0;color:#94a3b8;font-size:12px">© %d Công ty CP Phân bón &amp; Hóa chất Cần Thơ</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """
        .formatted(
            escape(preheader), tone.background, tone.accent, escape(heading), safeGreeting,
            escape(message), detailRows, action, noteBlock, Year.now().getValue());
  }

  private String renderDetails(List<Detail> details, Tone tone) {
    if (details == null || details.isEmpty())
      return "";
    StringBuilder rows = new StringBuilder();
    for (Detail detail : details) {
      if (detail == null || detail.value() == null || detail.value().isBlank())
        continue;
      rows.append("<tr><td style=\"padding:5px 12px 5px 0;color:#64748b;font-size:13px;white-space:nowrap\">")
          .append(escape(detail.label()))
          .append("</td><td style=\"padding:5px 0;color:#0f172a;font-size:14px;font-weight:700\">")
          .append(escape(detail.value()))
          .append("</td></tr>");
    }
    if (rows.isEmpty())
      return "";
    return "<table role=\"presentation\" width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:22px;padding:12px 16px;background:"
        + tone.background + ";border:1px solid " + tone.border + ";border-left:4px solid " + tone.accent
        + ";border-radius:8px\">" + rows + "</table>";
  }

  private String escape(String value) {
    if (value == null)
      return "";
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;");
  }

  private String escapeAttribute(String value) {
    return escape(value).replace("`", "&#96;");
  }
}
