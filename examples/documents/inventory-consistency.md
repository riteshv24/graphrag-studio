# Inventory consistency
Inventory Service reserves stock before checkout requests payment authorization. A reservation expires after ten minutes unless checkout confirms the order.
Inventory Service -> STORES_IN -> PostgreSQL
Inventory Service -> SENDS_TO -> Kafka
Commerce Team -> OWNS -> Inventory Service
Released reservations restore stock availability. Repeated release events are deduplicated by reservation identifier.
