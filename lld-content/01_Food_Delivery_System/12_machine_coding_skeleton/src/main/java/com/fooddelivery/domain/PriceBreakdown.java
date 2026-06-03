package com.fooddelivery.domain;

public final class PriceBreakdown {
    private final Money subtotal;
    private final Money tax;
    private final Money deliveryFee;
    private final Money discount;
    private final Money surge;
    private final Money total;

    public PriceBreakdown(Money subtotal, Money tax, Money deliveryFee,
                          Money discount, Money surge, Money total) {
        this.subtotal = subtotal;
        this.tax = tax;
        this.deliveryFee = deliveryFee;
        this.discount = discount;
        this.surge = surge;
        this.total = total;
    }

    public Money subtotal()    { return subtotal; }
    public Money tax()         { return tax; }
    public Money deliveryFee() { return deliveryFee; }
    public Money discount()    { return discount; }
    public Money surge()       { return surge; }
    public Money total()       { return total; }

    @Override public String toString() {
        return "[sub=" + subtotal + ", tax=" + tax + ", del=" + deliveryFee
             + ", disc=" + discount + ", surge=" + surge + ", total=" + total + "]";
    }
}
