package com.booking.system.repository;

import com.booking.system.entity.BookingRoom;
import com.booking.system.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRoomRepository extends JpaRepository<BookingRoom, String> {

    /**
     * Đếm số lượng booking của 1 phòng trong khoảng thời gian có bị trùng hay không.
     * Logic trùng: NewStart < ExistingEnd AND NewEnd > ExistingStart
     */
    @Query("SELECT COUNT(b) FROM BookingRoom b WHERE b.room.id = :roomId " +
           "AND b.status IN ('PENDING', 'APPROVED') " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    long countOverlappingBookings(@Param("roomId") String roomId, 
                                  @Param("startTime") LocalDateTime startTime, 
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * Lấy booking giao với khoảng thời gian calendar đang hiển thị.
     * Logic giao khoảng: ExistingStart < RangeEnd AND ExistingEnd > RangeStart.
     */
    @Query("SELECT b FROM BookingRoom b WHERE b.startTime < :endTime AND b.endTime > :startTime " +
           "AND (:roomId IS NULL OR b.room.id = :roomId) " +
           "AND (:status IS NULL OR b.status = :status) " +
           "ORDER BY b.startTime ASC")
    List<BookingRoom> findByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("roomId") String roomId,
            @Param("status") BookingStatus status);

    long countByStatus(com.booking.system.enums.BookingStatus status);

    java.util.List<BookingRoom> findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
            com.booking.system.enums.BookingStatus status, 
            LocalDateTime start, 
            LocalDateTime end);

    java.util.List<BookingRoom> findByRequesterIdAndStartTimeAfterOrderByStartTimeAsc(
            String requesterId, 
            LocalDateTime time);
}
