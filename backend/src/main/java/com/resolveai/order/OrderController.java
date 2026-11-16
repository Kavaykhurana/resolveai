package com.resolveai.order;

import com.resolveai.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public record OrderDto(String orderNumber, String items, String status, BigDecimal totalAmount,
                           Instant orderedAt, Instant deliveredAt, String trackingId) {
        static OrderDto from(Order o) {
            return new OrderDto(o.getOrderNumber(), o.getItems(), o.getStatus().name(),
                    o.getTotalAmount(), o.getOrderedAt(), o.getDeliveredAt(), o.getTrackingId());
        }
    }

    @GetMapping
    public List<OrderDto> myOrders(@AuthenticationPrincipal User user) {
        return orderRepository.findByCustomerIdOrderByOrderedAtDesc(user.getId())
                .stream().map(OrderDto::from).toList();
    }
}