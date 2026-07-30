# Security policy

Please report suspected vulnerabilities privately through GitHub's security
advisory feature for this repository. Do not open a public issue containing
credentials, exploit details, or sensitive production data.

The default API key and one-node infrastructure in `compose.yml` are for local
development only. Production deployments must provide externally managed
secrets, encrypted connections, network policies, and multi-node Kafka and
PostgreSQL services.
