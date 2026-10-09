package com.booking.system.repository;

import com.booking.system.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {

    /**
     * Lấy thông tin phòng kèm theo Pessimistic Write Lock (khóa dòng trong CSDL).
     * Dùng để ngăn chặn Race Condition khi nhiều người đặt cùng 1 phòng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :roomId")
    Optional<Room> findByIdWithLock(@Param("roomId") String roomId);

    long countByStatus(com.booking.system.enums.RoomStatus status);
}
