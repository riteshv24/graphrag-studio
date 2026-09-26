# Observability runbook
Prometheus collects request error rates and latency histograms from Payments Service and Identity Service. Grafana dashboards display these metrics.
Prometheus -> MONITORS -> Payments Service
Prometheus -> MONITORS -> Identity Service
Grafana -> USES -> Prometheus
Platform Team -> OWNS -> Prometheus
During a payment incident, inspect identity failures before increasing payment retry counts. Increasing retries can overload a failing dependency.
