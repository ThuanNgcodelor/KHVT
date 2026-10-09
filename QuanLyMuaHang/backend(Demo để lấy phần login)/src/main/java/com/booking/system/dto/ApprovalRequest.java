package com.booking.system.dto;

import lombok.Data;

@Data
public class ApprovalRequest {
    private String approverId;
    
    private String reason;

    // Older frontend builds sent this field. Keep it as an alias so reject reason is not lost.
    private String note;

    public String getEffectiveReason() {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return note;
    }
}
