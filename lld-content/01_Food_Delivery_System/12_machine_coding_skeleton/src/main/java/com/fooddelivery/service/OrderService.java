package com.fooddelivery.service;

import com.fooddelivery.domain.MenuItem;
import com.fooddelivery.domain.Order;
import com.fooddelivery.domain.OrderItem;
import com.fooddelivery.domain.PriceBreakdown;
import com.fooddelivery.domain.Restaurant;
import com.fooddelivery.repository.MenuRepository;
import com.fooddelivery.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderService {
    private final OrderRepository orders;
    private final MenuRepository menu;
    private final PricingService pricing;
    private final EventBus events;

    public OrderService(OrderRepository orders, MenuRepository menu,
                        PricingService pricing, EventBus events) {
        this.orders = orders;
        this.menu = menu;
        this.pricing = pricing;
        this.events = events;
    }

    public Order placeOrder(PlaceOrderCommand cmd) {
        var existing = orders.findByIdempotencyKey(cmd.idempotencyKey);
        if (existing.isPresent()) return existing.get();

        Restaurant r = menu.findRestaurant(cmd.restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("restaurant not found"));
        if (!r.isActive()) throw new IllegalStateException("restaurant closed");

        List<OrderItem> items = new ArrayList<>();
        for (var li : cmd.items) {
            MenuItem m = menu.findItem(li.menuItemId)
                    .orElseThrow(() -> new IllegalArgumentException("item not found: " + li.menuItemId));
            if (!m.restaurantId().equals(cmd.restaurantId))
                throw new IllegalArgumentException("item " + m.id() + " not in restaurant " + cmd.restaurantId);
            if (!m.isAvailable())
                throw new IllegalStateException("item out of stock: " + m.name());
            items.add(new OrderItem(m.id(), m.name(), li.quantity, m.price()));
        }

        PriceBreakdown price = pricing.compute(items, cmd.currency);

        Order.Builder b = Order.builder()
                .customer(cmd.customerId)
                .restaurant(cmd.restaurantId)
                .priceBreakdown(price)
                .idempotencyKey(cmd.idempotencyKey);
        items.forEach(b::addItem);

        Order saved = orders.save(b.build());

        // Repository may have returned an existing one (idempotency race).
        if (saved.idempotencyKey() != null && !saved.idempotencyKey().equals(cmd.idempotencyKey)) {
            // shouldn't happen
            throw new IllegalStateException("idempotency mismatch");
        }
        events.publish(new OrderPlaced(saved.id()));
        return saved;
    }

    public Order confirm(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        o.confirm();
        Order saved = orders.save(o);
        events.publish(new OrderConfirmed(saved.id()));
        return saved;
    }

    public Order markPreparing(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        o.markPreparing();
        return orders.save(o);
    }

    public Order markReady(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        o.markReady();
        return orders.save(o);
    }

    public Order markOutForDelivery(UUID orderId, UUID assignmentId) {
        Order o = orders.findById(orderId).orElseThrow();
        o.markOutForDelivery(assignmentId);
        return orders.save(o);
    }

    public Order markDelivered(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        o.markDelivered();
        Order saved = orders.save(o);
        events.publish(new OrderDelivered(saved.id()));
        return saved;
    }

    public Order cancel(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        if (!o.canCancel()) throw new IllegalStateException("cannot cancel in state " + o.status());
        o.cancel();
        Order saved = orders.save(o);
        events.publish(new OrderCancelled(saved.id()));
        return saved;
    }
}
