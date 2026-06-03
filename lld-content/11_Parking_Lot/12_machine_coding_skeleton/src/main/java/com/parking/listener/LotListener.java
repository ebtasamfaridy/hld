package com.parking.listener;

import com.parking.domain.Money;
import com.parking.domain.Spot;
import com.parking.domain.Ticket;
import com.parking.domain.Vehicle;

public interface LotListener {
    void onAdmitted(Vehicle v, Spot s, Ticket t);
    void onRejected(Vehicle v, String reason);
    void onClosed(Ticket t, Money fee);
}
