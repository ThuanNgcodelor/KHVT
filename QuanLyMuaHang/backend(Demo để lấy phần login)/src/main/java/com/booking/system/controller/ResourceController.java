package com.booking.system.controller;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.Room;
import com.booking.system.entity.Vehicle;
import com.booking.system.repository.RoomRepository;
import com.booking.system.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final RoomRepository roomRepository;
    private final VehicleRepository vehicleRepository;

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<Room>>> getAllRooms() {
        return ResponseEntity.ok(ApiResponse.success(roomRepository.findAll(), "Lấy danh sách phòng thành công"));
    }

    @GetMapping("/cars")
    public ResponseEntity<ApiResponse<List<Vehicle>>> getAllCars() {
        return ResponseEntity.ok(ApiResponse.success(vehicleRepository.findAll(), "Lấy danh sách xe thành công"));
    }
}
