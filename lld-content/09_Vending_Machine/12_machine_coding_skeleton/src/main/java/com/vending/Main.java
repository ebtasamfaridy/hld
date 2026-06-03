package com.vending;

import com.vending.domain.*;
import com.vending.hardware.StubHardware;
import com.vending.inventory.CashInventory;
import com.vending.inventory.Inventory;
import com.vending.listener.ConsoleAuditListener;
import com.vending.payment.GreedyChangeMaker;

import java.time.Clock;

public final class Main {
    public static void main(String[] args) {
        var inventory = new Inventory();
        var cash      = new CashInventory();
        var hw        = new StubHardware();
        var audit     = new ConsoleAuditListener();

        var coke   = new Product("p1", "Coke");
        var chips  = new Product("p2", "Chips");
        var water  = new Product("p3", "Water");

        inventory.addSlot(new Slot(SlotCode.of("A1"), coke,  Money.inr(3500), 5));   // ₹35
        inventory.addSlot(new Slot(SlotCode.of("A2"), chips, Money.inr(2000), 10));  // ₹20
        inventory.addSlot(new Slot(SlotCode.of("A3"), water, Money.inr(1500), 0));   // out of stock

        cash.add(Denomination.coin(500),  5);   // 5 × ₹5
        cash.add(Denomination.coin(1000), 4);   // 4 × ₹10
        cash.add(Denomination.note(2000), 2);   // 2 × ₹20

        var vm = new VendingMachine(inventory, cash, new GreedyChangeMaker(), hw, audit, Clock.systemUTC());

        // ---- Successful purchase: ₹100 → ₹35 product, ₹65 change ----
        section("Purchase Coke with ₹100");
        vm.selectProduct(SlotCode.of("A1"));
        vm.insertCoin(Denomination.note(10000));  // ₹100

        // ---- Out-of-stock attempt ----
        section("Try out-of-stock A3");
        vm.selectProduct(SlotCode.of("A3"));     // stays IDLE; OUT_OF_STOCK audit

        // ---- Cancel mid-payment ----
        section("Cancel after partial pay");
        vm.selectProduct(SlotCode.of("A2"));
        vm.insertCoin(Denomination.coin(1000));  // ₹10
        vm.cancel();

        // ---- Operator workflow ----
        section("Operator refills");
        vm.enterMaintenance();
        vm.refillSlot(SlotCode.of("A3"), 5, Money.inr(1500), water);
        vm.refillCash(Denomination.coin(500), 10);
        Money collected = vm.collectCash();
        System.out.println("  collected: " + collected);
        vm.exitMaintenance();
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
