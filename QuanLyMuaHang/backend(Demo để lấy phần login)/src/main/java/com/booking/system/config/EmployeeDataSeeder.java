package com.booking.system.config;

import com.booking.system.entity.User;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tạo các tài khoản nhân viên ban đầu từ danh sách CFC Cờ Bay.
 * Chỉ tạo email chưa tồn tại để không reset mật khẩu hoặc quyền của tài khoản
 * đang dùng.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class EmployeeDataSeeder implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    private static final List<EmployeeSeed> EMPLOYEES = List.of(
            new EmployeeSeed("Trần Ngọc Văn", "tranngocvan@cfccobay.com"),
            new EmployeeSeed("Trần Siêu Đẳng", "dangtran@cfccobay.com"),
            new EmployeeSeed("Võ Quỳnh Như", "thanhtung@cfccobay.com"),
            new EmployeeSeed("Nguyễn Thanh Duy", "thanhduy@cfccobay.com"),
            new EmployeeSeed("Trầm Trung Bích Thảo", "thaotram@cfccobay.com"),
            new EmployeeSeed("Lê Huy Minh", "huyminh@cfccobay.com"),
            new EmployeeSeed("Lê Minh Toàn", "leminhtoan@cfccobay.com"),
            new EmployeeSeed("Huỳnh Phú Nhân", "huynhphunhon362@gmail.com"),
            new EmployeeSeed("Lê Trọng Nhơn", "ltnhon30192@gmail.com"),
            new EmployeeSeed("Mạch Chí Thiện", "thiencfccobay@gmail.com"),
            new EmployeeSeed("Nguyễn Duy Vũ", "tycan78@gmail.com"),
            new EmployeeSeed("Phùng Thị Ngọc Thanh", "phungthanhct@gmail.com"),
            new EmployeeSeed("Lê Thị Tố Uyên", "lttuyen6868@gmail.com"),
            new EmployeeSeed("Nguyễn Ngọc Diệp Trang", "dieptrang@cfccobay.com"),
            new EmployeeSeed("Bùi Hữu Thọ", "huutho573@gmail.com"),
            new EmployeeSeed("Lê Bá Toàn", "toanle@cfccobay.com"),
            new EmployeeSeed("Dương Quang Minh", "dqminh1309@gmail.com"),
            new EmployeeSeed("Phan Trường Tiền", "pttien@cfccobay.com"),
            new EmployeeSeed("Đặng Phương Thảo", "phuongthao.ct@gmail.com"),
            new EmployeeSeed("Nguyễn Bá Tân", "tan090392@gmail.com"),
            new EmployeeSeed("Giang Minh Tú", "minhtu104000@gmail.com"));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        int createdCount = 0;

        for (EmployeeSeed employee : EMPLOYEES) {
            String email = employee.email().trim().toLowerCase();
            if (userRepository.existsByEmail(email)) {
                continue;
            }

            User user = new User();
            user.setFullName(employee.fullName());
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setRole(RoleEnum.EMPLOYEE);
            user.setStatus(UserStatus.ACTIVE);
            user.setJobPosition(null);
            userRepository.save(user);
            createdCount++;
        }

        System.out.println("Đã tạo " + createdCount + " tài khoản nhân viên mẫu.");
    }

    private record EmployeeSeed(String fullName, String email) {
    }
}
