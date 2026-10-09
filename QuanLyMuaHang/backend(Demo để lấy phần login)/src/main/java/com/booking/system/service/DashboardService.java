package com.booking.system.service;

import com.booking.system.dto.AdminDashboardStats;
import com.booking.system.dto.ClientDashboardStats;
import com.booking.system.dto.DashboardActivity;
import com.booking.system.entity.BookingRoom;
import com.booking.system.entity.BookingCar;
import com.booking.system.enums.BookingStatus;
import com.booking.system.enums.RoomStatus;
import com.booking.system.repository.BookingCarRepository;
import com.booking.system.repository.BookingRoomRepository;
import com.booking.system.repository.RoomRepository;
import com.booking.system.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RoomRepository roomRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRoomRepository bookingRoomRepository;
    private final BookingCarRepository bookingCarRepository;

    public AdminDashboardStats getAdminStats() {
        long totalRooms = roomRepository.count();
        long activeRooms = roomRepository.countByStatus(RoomStatus.ACTIVE);

        long totalCars = vehicleRepository.count();
        long activeCars = vehicleRepository.countByStatus(RoomStatus.ACTIVE);

        long pendingRoomApprovals = bookingRoomRepository.countByStatus(BookingStatus.PENDING);
        long pendingCarApprovals = bookingCarRepository.countByStatus(BookingStatus.PENDING);
        long pendingApprovals = pendingRoomApprovals + pendingCarApprovals;

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        List<BookingRoom> roomsToday = bookingRoomRepository
                .findByStatusAndStartTimeBetweenOrderByStartTimeAsc(BookingStatus.APPROVED, startOfDay, endOfDay);
        List<BookingCar> carsToday = bookingCarRepository
                .findByStatusAndStartTimeBetweenOrderByStartTimeAsc(BookingStatus.APPROVED, startOfDay, endOfDay);

        List<DashboardActivity> todayActivities = new ArrayList<>();

        for (BookingRoom r : roomsToday) {
            todayActivities.add(DashboardActivity.builder()
                    .id(r.getId())
                    .type("ROOM")
                    .title(r.getTitle())
                    .subtitle(r.getRoom().getName())
                    .startTime(r.getStartTime())
                    .endTime(r.getEndTime())
                    .status(r.getStatus().name())
                    .requesterName(r.getRequester().getFullName())
                    .build());
        }

        for (BookingCar c : carsToday) {
            todayActivities.add(DashboardActivity.builder()
                    .id(c.getId())
                    .type("CAR")
                    .title("Đi " + c.getDestination())
                    .subtitle(c.getVehicle().getVehicleType().getName() + " (" + c.getVehicle().getLicensePlate() + ")")
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .status(c.getStatus().name())
                    .requesterName(c.getRequester().getFullName())
                    .build());
        }

        todayActivities.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        return AdminDashboardStats.builder()
                .totalRooms(totalRooms)
                .activeRooms(activeRooms)
                .totalCars(totalCars)
                .activeCars(activeCars)
                .pendingApprovals(pendingApprovals)
                .todayActivities(todayActivities)
                .build();
    }

    public ClientDashboardStats getClientStats(String userId) {
        LocalDateTime now = LocalDateTime.now();
        List<BookingRoom> upcomingRooms = bookingRoomRepository
                .findByRequesterIdAndStartTimeAfterOrderByStartTimeAsc(userId, now);
        List<BookingCar> upcomingCars = bookingCarRepository
                .findByRequesterIdAndStartTimeAfterOrderByStartTimeAsc(userId, now);

        List<DashboardActivity> upcomingActivities = new ArrayList<>();

        for (BookingRoom r : upcomingRooms) {
            upcomingActivities.add(DashboardActivity.builder()
                    .id(r.getId())
                    .type("ROOM")
                    .title(r.getTitle())
                    .subtitle(r.getRoom().getName())
                    .startTime(r.getStartTime())
                    .endTime(r.getEndTime())
                    .status(r.getStatus().name())
                    .requesterName(r.getRequester().getFullName())
                    .build());
        }

        for (BookingCar c : upcomingCars) {
            upcomingActivities.add(DashboardActivity.builder()
                    .id(c.getId())
                    .type("CAR")
                    .title("Đi " + c.getDestination())
                    .subtitle(c.getVehicle().getVehicleType().getName() + " (" + c.getVehicle().getLicensePlate() + ")")
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .status(c.getStatus().name())
                    .requesterName(c.getRequester().getFullName())
                    .build());
        }

        upcomingActivities.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        return ClientDashboardStats.builder()
                .upcomingBookings(upcomingActivities)
                .build();
    }
}
