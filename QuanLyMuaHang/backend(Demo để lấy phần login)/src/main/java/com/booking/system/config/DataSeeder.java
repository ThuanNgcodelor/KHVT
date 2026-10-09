package com.booking.system.config;

import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.Department;
import com.booking.system.entity.Room;
import com.booking.system.entity.User;
import com.booking.system.entity.Vehicle;
import com.booking.system.entity.VehicleType;
import com.booking.system.enums.BookingStatus;
import com.booking.system.enums.RoleEnum;
import com.booking.system.enums.RoomStatus;
import com.booking.system.enums.UserStatus;
import com.booking.system.repository.BookingRoomRepository;
import com.booking.system.repository.DepartmentRepository;
import com.booking.system.repository.RoomRepository;
import com.booking.system.repository.UserRepository;
import com.booking.system.repository.VehicleRepository;
import com.booking.system.repository.VehicleTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoomRepository roomRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleTypeRepository vehicleTypeRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        User duy = createAdminIfNotExists("cfcbooking@gmail.com", "Admin CFC", "admin123@");
        createAdminIfNotExists("dienpm@gmail.com", "Phan Thị Minh Diễn", "123456");

        seedDepartments();
        Room pTruyenThong = seedRooms();
        seedVehicles();
        seedBookingRoom(duy, pTruyenThong);
    }

    private User createAdminIfNotExists(String email, String fullName, String password) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User admin = new User();
            admin.setEmail(email);
            admin.setFullName(fullName);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(RoleEnum.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setJobPosition("Quản trị viên");
            userRepository.save(admin);
            System.out.println("Đã tạo tài khoản mẫu: " + email);
            return admin;
        });
    }

    private void seedDepartments() {
        if (departmentRepository.count() > 0) {
            return;
        }

        String[] departments = {
                "Tổ chức",
                "Kế Toán",
                "Kế hoạch vật tư",
                "Kinh Doanh",
                "Xuất nhập khẩu",
                "Kĩ thuật",
                "Quản lý chất lượng",
                "Kho vận"
        };

        for (String name : departments) {
            if (!departmentRepository.existsByNameIgnoreCase(name)) {
                Department department = new Department();
                department.setName(name);
                departmentRepository.save(department);
            }
        }
    }

    private Room seedRooms() {
        if (roomRepository.count() == 0) {
            Room room1 = new Room();
            room1.setName("Phòng truyền thống");
            room1.setLocation("Tầng 2");
            room1.setCapacity(20);
            room1.setStatus(RoomStatus.ACTIVE);
            room1 = roomRepository.save(room1);

            Room room2 = new Room();
            room2.setName("Phòng đào tạo");
            room2.setLocation("Tầng 3");
            room2.setCapacity(50);
            room2.setStatus(RoomStatus.ACTIVE);
            roomRepository.save(room2);

            System.out.println("Đã tạo dữ liệu mẫu cho Phòng họp (Rooms)");
            return room1;
        }
        return roomRepository.findAll().stream().filter(r -> r.getName().equals("Phòng truyền thống")).findFirst().orElse(null);
    }

    private void seedVehicles() {
        if (vehicleRepository.count() == 0) {
            VehicleType type7Cho = new VehicleType();
            type7Cho.setName("Xe 7 chỗ");
            type7Cho = vehicleTypeRepository.save(type7Cho);

            VehicleType typeBanTai = new VehicleType();
            typeBanTai.setName("Xe bán tải");
            typeBanTai = vehicleTypeRepository.save(typeBanTai);

            saveVehicle("51L - 34574", type7Cho, 7);
            saveVehicle("51L - 846.28", typeBanTai, 5);
            saveVehicle("51L - 872.77", typeBanTai, 5);
            saveVehicle("51L - 872.78", typeBanTai, 5); // Changed last digit to avoid constraint issue
            saveVehicle("65N-0862", typeBanTai, 5);

            System.out.println("Đã tạo dữ liệu mẫu cho Xe (Vehicles)");
        }
    }

    private void saveVehicle(String plate, VehicleType type, int seats) {
        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(plate);
        vehicle.setVehicleType(type);
        vehicle.setSeatCount(seats);
        vehicle.setStatus(RoomStatus.ACTIVE);
        vehicleRepository.save(vehicle);
    }

    private void seedBookingRoom(User requester, Room room) {
        if (bookingRoomRepository.count() == 0 && requester != null && room != null) {
            BookingRoom booking = new BookingRoom();
            booking.setTitle("Họp marketing");
            booking.setRoom(room);
            booking.setRequester(requester);
            booking.setStatus(BookingStatus.APPROVED);
            
            // 09:00 - 30/06/2026
            LocalDateTime start = LocalDateTime.of(2026, 6, 30, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 6, 30, 10, 0);
            booking.setStartTime(start);
            booking.setEndTime(end);
            
            booking.setAttendeeCount(10);
            booking.setNote("Chưa có mô tả");
            
            bookingRoomRepository.save(booking);
            System.out.println("Đã tạo BookingRoom mẫu");
        }
    }
}
