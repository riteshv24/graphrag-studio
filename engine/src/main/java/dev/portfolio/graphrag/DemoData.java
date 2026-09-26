package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** A fictional architecture corpus with explicit relationship statements and manually labelled queries. */
public final class DemoData {
    private DemoData() {
    }
    public static List<Map<String, Object>> documents() {
        return List.of( doc("Payments architecture", """
            # Payments architecture
            Payments Service authorizes card payments and records the payment ledger. It requires Identity Service to validate the caller before a payment can proceed.
            Payments Service -> DEPENDS_ON -> Identity Service
            Payments Service -> STORES_IN -> PostgreSQL
            Payments Service -> SENDS_TO -> Kafka
            Checkout Service -> DEPENDS_ON -> Payments Service
            A failed authorization returns a declined payment. Retries use an idempotency key to avoid duplicate charges.
            """), doc("Identity and access", """
            # Identity and access
            Identity Service validates signed access tokens and consults Redis for active sessions. An unavailable session cache causes authenticated requests to fail closed.
            Identity Service -> USES -> Redis
            Identity Service -> RUNS_ON -> Kubernetes
            Platform Team -> OWNS -> Identity Service
            The access token lifetime is fifteen minutes. Refresh operations require a valid active session.
            """), doc("Checkout request flow", """
            # Checkout request flow
            Checkout Service calls Payments Service after it reserves inventory. If payment authorization fails, Checkout Service releases the reservation.
            Checkout Service -> DEPENDS_ON -> Payments Service
            Checkout Service -> DEPENDS_ON -> Inventory Service
            Commerce Team -> OWNS -> Checkout Service
            Checkout Service returns an order confirmation only after payment authorization succeeds.
            """), doc("Data platform contracts", """
            # Data platform contracts
            PostgreSQL stores the payment ledger with a unique idempotency key for every authorization request. Kafka carries payment events to downstream consumers.
            Data Team -> MANAGES -> PostgreSQL
            Data Team -> MANAGES -> Kafka
            Analytics Service -> USES -> Kafka
            Analytics Service -> STORES_IN -> Object Storage
            Payment events are delivered at least once. Consumers deduplicate using the event identifier.
            """), doc("Ownership register", """
            # Ownership register
            Platform Team owns Identity Service and the Kubernetes runtime. Commerce Team owns Checkout Service and Inventory Service. Payments Team owns Payments Service.
            Platform Team -> OWNS -> Kubernetes
            Platform Team -> OWNS -> Identity Service
            Commerce Team -> OWNS -> Inventory Service
            Payments Team -> OWNS -> Payments Service
            Ownership determines which team receives the initial incident notification.
            """), doc("Observability runbook", """
            # Observability runbook
            Prometheus collects request error rates and latency histograms from Payments Service and Identity Service. Grafana dashboards display these metrics.
            Prometheus -> MONITORS -> Payments Service
            Prometheus -> MONITORS -> Identity Service
            Grafana -> USES -> Prometheus
            Platform Team -> OWNS -> Prometheus
            During a payment incident, inspect identity failures before increasing payment retry counts. Increasing retries can overload a failing dependency.
            """), doc("Inventory consistency", """
            # Inventory consistency
            Inventory Service reserves stock before checkout requests payment authorization. A reservation expires after ten minutes unless checkout confirms the order.
            Inventory Service -> STORES_IN -> PostgreSQL
            Inventory Service -> SENDS_TO -> Kafka
            Commerce Team -> OWNS -> Inventory Service
            Released reservations restore stock availability. Repeated release events are deduplicated by reservation identifier.
            """), doc("Failure scenario notes", """
            # Failure scenario notes
            A Redis session outage can make Identity Service reject authenticated requests. Payments Service depends on Identity Service, so payment authorization can fail even while PostgreSQL remains healthy.
            Identity Service -> USES -> Redis
            Payments Service -> DEPENDS_ON -> Identity Service
            Checkout Service -> DEPENDS_ON -> Payments Service
            The incident response begins with the session cache and identity checks. These notes describe a fictional dependency failure, not a measured production incident.
            """));
    }
    private static Map<String, Object> doc(String title, String text) {
        return Json.map("title", title, "text", text, "source", "fictional architecture corpus");
    }
    public static List<Map<String, Object>> relevance(List<Map<String, Object>> documents) {
        Map<String, String> ids=new HashMap<>();
        documents.forEach(d->ids.put(Json.string(d, "title", ""), Json.string(d, "id", "")));
        List<Map<String, Object>> result=new ArrayList<>();
        add(result, ids, "What depends on Identity Service for payment authorization?", List.of("Payments architecture", "Failure scenario notes"));
        add(result, ids, "Which team owns Checkout Service?", List.of("Checkout request flow", "Ownership register"));
        add(result, ids, "How long does an inventory reservation last?", List.of("Inventory consistency"));
        add(result, ids, "How does PostgreSQL prevent duplicate payment authorization?", List.of("Data platform contracts", "Payments architecture"));
        return result;
    }
    private static void add(List<Map<String, Object>> result, Map<String, String> ids, String question, List<String> titles) {
        if(titles.stream().allMatch(ids::containsKey))result.add(Json.map("question", question, "expectedDocumentIds", titles.stream().map(ids::get).toList()));
    }
}
