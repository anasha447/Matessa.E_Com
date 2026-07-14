package com.matessa.commercial.service;

import com.matessa.commercial.messaging.OrderCancelledEvent;
import com.matessa.commercial.messaging.OrderPlacedEvent;

/** Applies the inventory side-effects of order events (decrement / restore stock, coupon). */
public interface OrderEventService {

    void handleOrderPlaced(OrderPlacedEvent event);

    void handleOrderCancelled(OrderCancelledEvent event);
}
