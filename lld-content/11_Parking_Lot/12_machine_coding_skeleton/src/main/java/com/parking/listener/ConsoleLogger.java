package com.parking.listener;

import com.parking.domain.Money;
import com.parking.domain.Spot;
import com.parking.domain.Ticket;
import com.parking.domain.Vehicle;

public final class ConsoleLogger implements LotListener {
    @Override public void onAdmitted(Vehicle v, Spot s, Ticket t) {
        System.out.printf("  [admit] %-7s %-12s → spot %s (%s)%n",
                v.type(), v.plate(), s.id(), s.type());
    }
    @Override public void onRejected(Vehicle v, String reason) {
        System.out.printf("  [reject] %-7s %-12s — %s%n", v.type(), v.plate(), reason);
    }
    @Override public void onClosed(Ticket t, Money fee) {
        System.out.printf("  [exit]  %-12s ticket %s   fee %s%n", t.plate(), t.id(), fee);
    }
}
