package com.parking.gateway;

import com.parking.domain.SpotId;
import com.parking.domain.TicketId;
import com.parking.domain.VehicleType;

public sealed interface EntryResult permits EntryResult.Admitted, EntryResult.LotFull {
    record Admitted(TicketId ticketId, SpotId spot)              implements EntryResult {}
    record LotFull(VehicleType vehicleType)                      implements EntryResult {}
}
