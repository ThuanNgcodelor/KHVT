package com.booking.system.entity;

import com.booking.system.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column
    private String location;

    @Column(nullable = false)
    private Integer capacity;

    @Column
    private String equipment;
    
    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.ACTIVE;
}
