# Fictional Northstar corpus

`collection.json` configures aliases, chunking, and the offline hash embedding provider. `documents.json` maps human-readable titles to the eight Markdown files under `documents/`. They are the actual seed texts, not summaries of a larger unavailable corpus.

Create a collection, then POST each document's title/text/source to `/api/collections/{id}/documents` and poll the returned job. `query.json` shows a real query request. The application also seeds these inputs automatically on first startup.

Retrieve `/api/collections/{id}/benchmark-fixture` to obtain relevance labels with IDs belonging to the running seeded collection. Do not reuse document IDs from another workspace. For a fully automated independent example, run `python scripts/demo.py`.

All companies, systems, policies, and relationships in these examples are fictional. A typed triple expresses a document claim, not an externally verified fact.
