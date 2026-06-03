package com.fooddelivery.service;

import com.fooddelivery.domain.Money;
import com.fooddelivery.domain.OrderItem;
import com.fooddelivery.domain.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;

public final class PricingService {
    private final List<PricingRule> rules;

    public PricingService(List<PricingRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public PriceBreakdown compute(List<OrderItem> items, String currency) {
        Money subtotal = items.stream()
                .map(OrderItem::lineTotal)
                .reduce(Money.zero(currency), Money::add);

        Builder b = new Builder(currency, subtotal);
        for (PricingRule r : rules) r.apply(items, b);
        return b.build();
    }

    public static final class Builder {
        private final String currency;
        private final Money subtotal;
        private Money tax;
        private Money deliveryFee;
        private Money discount;
        private Money surge;

        Builder(String currency, Money subtotal) {
            this.currency = currency;
            this.subtotal = subtotal;
            this.tax         = Money.zero(currency);
            this.deliveryFee = Money.zero(currency);
            this.discount    = Money.zero(currency);
            this.surge       = Money.zero(currency);
        }

        public Money subtotal() { return subtotal; }
        public Builder tax(Money m)         { this.tax = m; return this; }
        public Builder deliveryFee(Money m) { this.deliveryFee = m; return this; }
        public Builder discount(Money m)    { this.discount = m; return this; }
        public Builder surge(Money m)       { this.surge = m; return this; }

        PriceBreakdown build() {
            Money total = subtotal.add(tax).add(deliveryFee).add(surge).subtract(discount);
            return new PriceBreakdown(subtotal, tax, deliveryFee, discount, surge, total);
        }
    }

    // ---------- Built-in rules ----------

    public static PricingRule taxRule(BigDecimal pct) {
        return (items, b) -> b.tax(b.subtotal().multiply(pct));
    }

    public static PricingRule flatDeliveryFee(Money fee) {
        return (items, b) -> b.deliveryFee(fee);
    }

    public static PricingRule surgeRule(double factor) {
        return (items, b) -> {
            if (factor > 1.0) {
                BigDecimal extra = BigDecimal.valueOf(factor - 1.0);
                b.surge(b.subtotal().multiply(extra));
            }
        };
    }

    public static PricingRule promoFlatOff(Money off) {
        return (items, b) -> b.discount(off);
    }
}
