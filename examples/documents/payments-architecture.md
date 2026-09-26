# Payments architecture
Payments Service authorizes card payments and records the payment ledger. It requires Identity Service to validate the caller before a payment can proceed.
Payments Service -> DEPENDS_ON -> Identity Service
Payments Service -> STORES_IN -> PostgreSQL
Payments Service -> SENDS_TO -> Kafka
Checkout Service -> DEPENDS_ON -> Payments Service
A failed authorization returns a declined payment. Retries use an idempotency key to avoid duplicate charges.
