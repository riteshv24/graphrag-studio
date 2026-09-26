# Failure scenario notes
A Redis session outage can make Identity Service reject authenticated requests. Payments Service depends on Identity Service, so payment authorization can fail even while PostgreSQL remains healthy.
Identity Service -> USES -> Redis
Payments Service -> DEPENDS_ON -> Identity Service
Checkout Service -> DEPENDS_ON -> Payments Service
The incident response begins with the session cache and identity checks. These notes describe a fictional dependency failure, not a measured production incident.
