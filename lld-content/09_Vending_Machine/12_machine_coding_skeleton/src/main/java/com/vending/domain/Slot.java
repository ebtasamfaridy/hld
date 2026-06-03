package com.vending.domain;

public final class Slot {
    private final SlotCode code;
    private final Product product;
    private Money price;
    private int count;

    public Slot(SlotCode code, Product product, Money price, int count) {
        this.code = code; this.product = product; this.price = price; this.count = count;
    }
    public SlotCode code()     { return code; }
    public Product product()   { return product; }
    public Money price()       { return price; }
    public int count()         { return count; }

    public void setPrice(Money p) { this.price = p; }
    public void increment(int delta) { this.count += delta; }
    public boolean tryDecrement() {
        if (count <= 0) return false;
        count--;
        return true;
    }
}
