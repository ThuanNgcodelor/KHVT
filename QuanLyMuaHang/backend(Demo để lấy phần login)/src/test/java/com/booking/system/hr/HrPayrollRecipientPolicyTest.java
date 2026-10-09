package com.booking.system.hr;

import com.booking.system.hr.entity.*;
import com.booking.system.hr.enums.HrTelegramBindingStatus;
import com.booking.system.hr.service.HrPayrollRecipientPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HrPayrollRecipientPolicyTest {
    @Test void requiresActiveBindingWithSameChatAndVerifiedUser() {
        var row = new HrPayrollImportRow(); row.setTelegramUserId(123L);
        var delivery = new HrPayrollDelivery(); delivery.setTelegramChatId(456L); delivery.setImportRow(row);
        var binding = new HrEmployeeTelegramBinding(); binding.setStatus(HrTelegramBindingStatus.ACTIVE);
        binding.setTelegramChatId(456L); binding.setTelegramUserId(123L);
        assertThat(HrPayrollRecipientPolicy.matches(delivery, binding)).isTrue();
        binding.setTelegramChatId(999L);
        assertThat(HrPayrollRecipientPolicy.matches(delivery, binding)).isFalse();
        binding.setTelegramChatId(456L); binding.setTelegramUserId(789L);
        assertThat(HrPayrollRecipientPolicy.matches(delivery, binding)).isFalse();
        binding.setTelegramUserId(123L); binding.setStatus(null);
        assertThat(HrPayrollRecipientPolicy.matches(delivery, binding)).isFalse();
        assertThat(HrPayrollRecipientPolicy.matches(delivery, null)).isFalse();
    }
}
