package com.example.quanlymuahang.domain.purchaseorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "po_daily_sequences")
public class PoDailySequence {
    @Id @Column(name = "sequence_date") private LocalDate sequenceDate;
    @Column(name = "next_number", nullable = false) private int nextNumber;
    protected PoDailySequence() {}
}
