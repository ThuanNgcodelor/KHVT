package com.booking.system.repository;

import com.booking.system.entity.BookingCar;
import com.booking.system.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingCarRepository extends JpaRepository<BookingCar, String> {

    @Query("SELECT COUNT(b) FROM BookingCar b WHERE b.vehicle.id = :vehicleId " +
           "AND b.status IN ('PENDING', 'APPROVED') " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    long countOverlappingBookings(@Param("vehicleId") String vehicleId, 
                                  @Param("startTime") LocalDateTime startTime, 
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * Lấy booking giao với khoảng thời gian calendar đang hiển thị.
     * Logic giao khoảng: ExistingStart < RangeEnd AND ExistingEnd > RangeStart.
     */
    @Query("SELECT b FROM BookingCar b WHERE b.startTime < :endTime AND b.endTime > :startTime " +
           "AND (:vehicleId IS NULL OR b.vehicle.id = :vehicleId) " +
           "AND (:status IS NULL OR b.status = :status) " +
           "ORDER BY b.startTime ASC")
    List<BookingCar> findByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("vehicleId") String vehicleId,
            @Param("status") BookingStatus status);

    long countByStatus(com.booking.system.enums.BookingStatus status);

    java.util.List<BookingCar> findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
            com.booking.system.enums.BookingStatus status, 
            LocalDateTime start, 
            LocalDateTime end);

    java.util.List<BookingCar> findByRequesterIdAndStartTimeAfterOrderByStartTimeAsc(
            String requesterId, 
            LocalDateTime time);
}
