# Data platform contracts
PostgreSQL stores the payment ledger with a unique idempotency key for every authorization request. Kafka carries payment events to downstream consumers.
Data Team -> MANAGES -> PostgreSQL
Data Team -> MANAGES -> Kafka
Analytics Service -> USES -> Kafka
Analytics Service -> STORES_IN -> Object Storage
Payment events are delivered at least once. Consumers deduplicate using the event identifier.
