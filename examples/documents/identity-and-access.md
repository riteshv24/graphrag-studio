# Identity and access
Identity Service validates signed access tokens and consults Redis for active sessions. An unavailable session cache causes authenticated requests to fail closed.
Identity Service -> USES -> Redis
Identity Service -> RUNS_ON -> Kubernetes
Platform Team -> OWNS -> Identity Service
The access token lifetime is fifteen minutes. Refresh operations require a valid active session.
