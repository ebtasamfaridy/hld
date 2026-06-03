package com.fooddelivery;

import com.fooddelivery.dispatch.DispatchService;
import com.fooddelivery.dispatch.NearestFirstScoring;
import com.fooddelivery.domain.DeliveryAssignment;
import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.Location;
import com.fooddelivery.domain.MenuItem;
import com.fooddelivery.domain.Money;
import com.fooddelivery.domain.Order;
import com.fooddelivery.domain.Restaurant;
import com.fooddelivery.repository.AssignmentRepository;
import com.fooddelivery.repository.InMemoryDriverRepository;
import com.fooddelivery.repository.InMemoryOrderRepository;
import com.fooddelivery.repository.MenuRepository;
import com.fooddelivery.service.EventBus;
import com.fooddelivery.service.OrderCancelled;
import com.fooddelivery.service.OrderConfirmed;
import com.fooddelivery.service.OrderDelivered;
import com.fooddelivery.service.OrderPlaced;
import com.fooddelivery.service.OrderService;
import com.fooddelivery.service.PlaceOrderCommand;
import com.fooddelivery.service.PricingService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Composition root + demo. */
public class Main {

    public static void main(String[] args) {
        // ── repositories ───────────────────────────────────────────────
        var orderRepo  = new InMemoryOrderRepository();
        var driverRepo = new InMemoryDriverRepository();
        var menuRepo   = new MenuRepository();
        var assignRepo = new AssignmentRepository();

        // ── services ───────────────────────────────────────────────────
        var pricing = new PricingService(List.of(
                PricingService.taxRule(new BigDecimal("0.05")),
                PricingService.flatDeliveryFee(Money.inr(30))
        ));

        var bus = new EventBus();
        bus.register(OrderPlaced.class,     e -> System.out.println("[event] " + e));
        bus.register(OrderConfirmed.class,  e -> System.out.println("[event] " + e));
        bus.register(OrderCancelled.class,  e -> System.out.println("[event] " + e));
        bus.register(OrderDelivered.class,  e -> System.out.println("[event] " + e));

        var orderService = new OrderService(orderRepo, menuRepo, pricing, bus);
        var dispatch = new DispatchService(driverRepo, assignRepo, menuRepo,
                new NearestFirstScoring(), Duration.ofSeconds(15));

        // ── seed data ──────────────────────────────────────────────────
        UUID customerId = UUID.randomUUID();

        Restaurant bowl = menuRepo.save(new Restaurant("Bowl Cafe", "BLR",
                new Location(12.97, 77.59)));
        MenuItem manchurian = menuRepo.save(new MenuItem(bowl.id(), "Veg Manchurian", Money.inr(180)));
        MenuItem fried = menuRepo.save(new MenuItem(bowl.id(), "Fried Rice", Money.inr(220)));

        Driver alice = driverRepo.save(
                new Driver(UUID.randomUUID(), "Alice", "BLR", new Location(12.971, 77.591), 4.6));
        Driver bob   = driverRepo.save(
                new Driver(UUID.randomUUID(), "Bob",   "BLR", new Location(12.985, 77.610), 4.8));
        alice.goOnline(); driverRepo.save(alice);
        bob.goOnline();   driverRepo.save(bob);

        // ── 1. place order (idempotent) ────────────────────────────────
        var cmd = new PlaceOrderCommand(
                customerId, bowl.id(),
                List.of(
                        new PlaceOrderCommand.LineItem(manchurian.id(), 2),
                        new PlaceOrderCommand.LineItem(fried.id(), 1)),
                "demo-order-key-1",
                "INR");

        Order o = orderService.placeOrder(cmd);
        System.out.println("Placed: " + o);
        System.out.println("Price : " + o.priceBreakdown());

        // retry with same idempotency key — expect same Order
        Order again = orderService.placeOrder(cmd);
        System.out.println("Idempotent retry returns same id? " + o.id().equals(again.id()));

        // ── 2. restaurant accepts ──────────────────────────────────────
        orderService.confirm(o.id());

        // ── 3. dispatch driver ─────────────────────────────────────────
        DeliveryAssignment assignment = dispatch.dispatch(o)
                .orElseThrow(() -> new IllegalStateException("no driver available"));
        System.out.println("Offered to driver: " + assignment.driverId());

        // 4. driver accepts
        dispatch.accept(assignment.id());
        System.out.println("Driver accepted");

        // 5. kitchen prepares + ready
        orderService.markPreparing(o.id());
        orderService.markReady(o.id());

        // 6. driver picks up
        dispatch.pickedUp(assignment.id());
        orderService.markOutForDelivery(o.id(), assignment.id());

        // 7. driver delivers
        dispatch.delivered(assignment.id());
        orderService.markDelivered(o.id());

        // ── 8. cancel after delivered should fail ──────────────────────
        try {
            orderService.cancel(o.id());
        } catch (IllegalStateException e) {
            System.out.println("Expected cancel failure: " + e.getMessage());
        }

        System.out.println("Final order: " + orderRepo.findById(o.id()).orElseThrow());
    }
}
