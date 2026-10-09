package com.booking.system.repository;

import com.booking.system.entity.ApprovalStep;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.booking.system.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, String> {
    @EntityGraph(attributePaths = {"approver", "approver.department"})
    List<ApprovalStep> findByBookingRoomIdOrderByActedAtDesc(String bookingRoomId);

    @EntityGraph(attributePaths = {"approver", "approver.department"})
    List<ApprovalStep> findByBookingCarIdOrderByActedAtDesc(String bookingCarId);

    @EntityGraph(attributePaths = {
            "approver", "approver.department",
            "bookingRoom", "bookingRoom.requester", "bookingRoom.requester.department", "bookingRoom.room",
            "bookingRoom.cancelledBy", "bookingRoom.cancelledBy.department",
            "bookingCar", "bookingCar.requester", "bookingCar.requester.department",
            "bookingCar.cancelledBy", "bookingCar.cancelledBy.department",
            "bookingCar.vehicle", "bookingCar.vehicle.vehicleType"
    })
    @Query(value = """
            SELECT s FROM ApprovalStep s
            LEFT JOIN s.bookingRoom br
            LEFT JOIN br.requester brr
            LEFT JOIN br.room room
            LEFT JOIN s.bookingCar bc
            LEFT JOIN bc.requester bcr
            LEFT JOIN bc.vehicle vehicle
            LEFT JOIN vehicle.vehicleType vehicleType
            LEFT JOIN s.approver approver
            WHERE s.status IN ('APPROVED', 'REJECTED')
              AND (:type IS NULL OR (:type = 'ROOM' AND br.id IS NOT NULL) OR (:type = 'CAR' AND bc.id IS NOT NULL))
              AND (:status IS NULL OR br.status = :status OR bc.status = :status)
              AND (:fromTime IS NULL OR s.actedAt >= :fromTime)
              AND (:toTime IS NULL OR s.actedAt < :toTime)
              AND (:keyword IS NULL OR
                   LOWER(COALESCE(brr.fullName, bcr.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(brr.email, bcr.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(br.title, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(room.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(bc.departure, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(bc.destination, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(vehicle.licensePlate, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(vehicleType.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(approver.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY
              CASE WHEN :ascending = true THEN COALESCE(br.cancelledAt, bc.cancelledAt, s.actedAt) END ASC,
              CASE WHEN :ascending = false THEN COALESCE(br.cancelledAt, bc.cancelledAt, s.actedAt) END DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM ApprovalStep s
            LEFT JOIN s.bookingRoom br
            LEFT JOIN br.requester brr
            LEFT JOIN br.room room
            LEFT JOIN s.bookingCar bc
            LEFT JOIN bc.requester bcr
            LEFT JOIN bc.vehicle vehicle
            LEFT JOIN vehicle.vehicleType vehicleType
            LEFT JOIN s.approver approver
            WHERE s.status IN ('APPROVED', 'REJECTED')
              AND (:type IS NULL OR (:type = 'ROOM' AND br.id IS NOT NULL) OR (:type = 'CAR' AND bc.id IS NOT NULL))
              AND (:status IS NULL OR br.status = :status OR bc.status = :status)
              AND (:fromTime IS NULL OR s.actedAt >= :fromTime)
              AND (:toTime IS NULL OR s.actedAt < :toTime)
              AND (:keyword IS NULL OR
                   LOWER(COALESCE(brr.fullName, bcr.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(brr.email, bcr.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(br.title, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(room.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(bc.departure, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(bc.destination, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(vehicle.licensePlate, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(vehicleType.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(COALESCE(approver.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<ApprovalStep> findHistory(
            @Param("type") String type,
            @Param("status") BookingStatus status,
            @Param("keyword") String keyword,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("ascending") boolean ascending,
            Pageable pageable);
}
