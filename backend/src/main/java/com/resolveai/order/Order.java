package com.resolveai.order;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status { PROCESSING, SHIPPED, DELIVERED, DELAYED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String items;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "tracking_id")
    private String trackingId;

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getCustomerId() { return customerId; }
    public String getItems() { return items; }
    public Status getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getTrackingId() { return trackingId; }
}