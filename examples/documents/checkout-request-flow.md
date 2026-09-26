# Checkout request flow
Checkout Service calls Payments Service after it reserves inventory. If payment authorization fails, Checkout Service releases the reservation.
Checkout Service -> DEPENDS_ON -> Payments Service
Checkout Service -> DEPENDS_ON -> Inventory Service
Commerce Team -> OWNS -> Checkout Service
Checkout Service returns an order confirmation only after payment authorization succeeds.
