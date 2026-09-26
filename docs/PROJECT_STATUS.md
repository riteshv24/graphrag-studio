# Release status: GraphRAG Studio 0.1.0

## Implemented and exercised

The Java engine and JDK HTTP application compile and run. The product's offline workflows, persistence contracts, background jobs, domain tests, real HTTP smoke, browser-module tests, and offline DOM rendering have been exercised. See TESTING.md and `validation/` for the recorded output.

## Implemented but awaiting target-environment verification

The Spring MVC adapter, Maven build, H2 tests, JDBC PostgreSQL path, Docker images/Compose deployment, and external provider integrations are source implementations, not claimed verified deployments. CI must run after publication. Live integrations require explicitly configured services and credentials.

## Deliberate implementation choices

- Java owns the actual domain algorithms. Python is only an optional CLI/testing client, not a claimed AI-worker fleet.
- Browser interfaces use native ES modules, not React. There is no frontend package installation required to use the applications.
- Local atomic-file persistence is the default. PostgreSQL is the optional JDBC deployment. No Hibernate/JPA, Flyway, Redis, or Kafka claim is made.
- Offline AI-related baselines are labelled as such. Real generation/semantic embeddings are separate opt-in Ollama paths.
- GraphRAG's Neo4j support is a Cypher export, not a live graph database integration.

## Bounds and exclusions

The offline hash vectors are lexical features, not learned semantic embeddings. Entity extraction is deterministic and incomplete, not a general language-understanding model. Graph edges are document claims, not verified real-world facts. Retrieval is an in-memory linear scan, not an ANN service. Neo4j is an export target, not the live backing database. Native ingestion supports text/Markdown only; PDF parsing is not implemented.

The repository is an initial, inspectable implementation with tests and documentation. No three-month development duration, production adoption, historical commit schedule, employer use, or measured commercial impact is asserted. Add a real changelog as you review, test, and extend the code.
