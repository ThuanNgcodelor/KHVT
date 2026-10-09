package com.booking.system.dto;

import lombok.Data;

@Data
public class CancelRequest {
    // Chỉ giữ để tương thích frontend cũ. Backend luôn lấy người hủy từ phiên đăng nhập.
    private String cancellerId;

    private String reason;
}
