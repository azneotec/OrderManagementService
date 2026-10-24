package com.azneotech.ordermanagementservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One entry in an order's delivery timeline: the status it moved to, when, and an optional note. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_tracking_event")
public class OrderTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;
    private String note;
    @Column(nullable = false)
    private Instant createdAt;

    public OrderTrackingEvent(OrderStatus status, String note) {
        this.status = status;
        this.note = note;
        this.createdAt = Instant.now();
    }

}
