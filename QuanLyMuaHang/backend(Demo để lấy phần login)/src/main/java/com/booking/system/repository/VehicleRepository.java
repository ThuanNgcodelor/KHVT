package com.booking.system.repository;

import com.booking.system.entity.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :vehicleId")
    Optional<Vehicle> findByIdWithLock(@Param("vehicleId") String vehicleId);

    long countByStatus(com.booking.system.enums.RoomStatus status);
}
