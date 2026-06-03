package com.hotelbooking.service;

import com.hotelbooking.domain.Money;
import com.hotelbooking.repository.RoomInventoryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class PricingService {
    private final RoomInventoryRepository inv;
    private final BigDecimal taxPct;

    public PricingService(RoomInventoryRepository inv, BigDecimal taxPct) {
        this.inv = inv;
        this.taxPct = taxPct;
    }

    /** Sum nightly base prices × room count, plus tax. */
    public Money quote(UUID hotelId, UUID roomTypeId, LocalDate checkIn,
                       LocalDate checkOut, int roomCount) {
        Money subtotal = null;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            var row = inv.find(hotelId, roomTypeId, d).orElseThrow(
                () -> new IllegalStateException("No inventory for " + d));
            Money night = row.basePrice().multiply(BigDecimal.valueOf(roomCount));
            subtotal = subtotal == null ? night : subtotal.add(night);
        }
        Money tax = subtotal.multiply(taxPct);
        return subtotal.add(tax);
    }
}
