package com.publicissapient.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "items"})
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Only the FK column is mapped eagerly (LAZY) here; the full User graph
    // is resolved through @BatchMapping on the OrderController to avoid N+1.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Returns the owning user's id without forcing initialization of the full
     * User proxy/entity. Safe to call even when {@code user} is an
     * uninitialized Hibernate LAZY proxy, since the identifier is always
     * known up front and does not require a DB round trip. Used by the
     * {@code @BatchMapping} resolver in OrderController to group orders by
     * user id for the bulk User lookup.
     */
    @Transient
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
}
