package com.fooddelivery.dispatch;

import com.fooddelivery.domain.DeliveryAssignment;
import com.fooddelivery.domain.Driver;
import com.fooddelivery.domain.DriverStatus;
import com.fooddelivery.domain.Location;
import com.fooddelivery.domain.Order;
import com.fooddelivery.repository.AssignmentRepository;
import com.fooddelivery.repository.DriverRepository;
import com.fooddelivery.repository.MenuRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

public final class DispatchService {
    private final DriverRepository drivers;
    private final AssignmentRepository assignments;
    private final MenuRepository menu;
    private final ScoringStrategy scorer;
    private final Duration offerWindow;

    public DispatchService(DriverRepository drivers,
                           AssignmentRepository assignments,
                           MenuRepository menu,
                           ScoringStrategy scorer,
                           Duration offerWindow) {
        this.drivers = drivers;
        this.assignments = assignments;
        this.menu = menu;
        this.scorer = scorer;
        this.offerWindow = offerWindow;
    }

    /**
     * Find the best idle driver in the city of the order's restaurant and offer them the delivery.
     * Atomically transitions driver IDLE → OFFER_PENDING.
     */
    public synchronized Optional<DeliveryAssignment> dispatch(Order order) {
        Location pickup = menu.findRestaurant(order.restaurantId())
                .orElseThrow().location();
        String city = menu.findRestaurant(order.restaurantId()).orElseThrow().city();

        Optional<Driver> best = drivers.findByCityAndStatus(city, DriverStatus.IDLE).stream()
                .max(Comparator.comparingDouble(d -> scorer.score(d, pickup)));

        if (best.isEmpty()) return Optional.empty();
        Driver chosen = best.get();
        chosen.reserveForOffer();
        drivers.save(chosen);

        DeliveryAssignment a = new DeliveryAssignment(order.id(), chosen.id(),
                Instant.now().plus(offerWindow));
        return Optional.of(assignments.save(a));
    }

    public DeliveryAssignment accept(java.util.UUID assignmentId) {
        DeliveryAssignment a = assignments.findById(assignmentId).orElseThrow();
        a.accept();
        Driver d = drivers.findById(a.driverId()).orElseThrow();
        d.markBusy();
        drivers.save(d);
        return assignments.save(a);
    }

    public DeliveryAssignment pickedUp(java.util.UUID assignmentId) {
        DeliveryAssignment a = assignments.findById(assignmentId).orElseThrow();
        a.pickedUp();
        return assignments.save(a);
    }

    public DeliveryAssignment delivered(java.util.UUID assignmentId) {
        DeliveryAssignment a = assignments.findById(assignmentId).orElseThrow();
        a.delivered();
        Driver d = drivers.findById(a.driverId()).orElseThrow();
        d.markIdle();
        drivers.save(d);
        return assignments.save(a);
    }
}
