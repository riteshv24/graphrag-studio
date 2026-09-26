# A 12-week extension plan

This is **future work**, not a statement that the initial repository took twelve weeks to develop. The current implementation and delivery test results are described in PROJECT_STATUS.md and TESTING.md. The schedule is a planning estimate; narrow or expand it based on measured progress.

| Weeks | Focus | Deliverable and acceptance evidence |
|---|---|---|
| 1–2 | Independent relevance set | Collect permitted documents and manually label relevant passages, entities, and no-answer cases. Add paraphrase/conflict cases; separate development labels from held-out evaluation. |
| 3–4 | Document ingestion adapters | Add sandboxed PDF parsing with page/source provenance and file-type verification. Test malformed files, size limits, timeouts, and extraction failures; retain text ingestion as the baseline. |
| 5–6 | Embedding/index evolution | Add batched embeddings and a versioned vector index adapter. Measure recall/latency against the current linear scan and test dimension/model migration before any performance claim. |
| 7–8 | Graph quality and conflicts | Add entity-resolution review, relation confidence, and contradictory-source handling. Keep extracted claims distinct from verified relations. Evaluate extraction on labelled examples. |
| 9–10 | Retrieval experiments | Compare alternative fusion weights, rerankers, and graph expansion policies on held-out questions. Track citation support and abstention accuracy, not just retrieval overlap. |
| 11–12 | Real deployment boundary | Run Spring/PostgreSQL tests and add authentication, per-collection access control, ingestion quotas, and restore drills. Document load limits from measured experiments. |

## How to make the project your own

Open real issues, select one independently useful feature, write failing tests, implement the change, and commit it with a clear explanation. Record the actual date and result. A smaller change that you can explain and defend is more valuable than extra services that have no tested behavior.

Keep a decision log describing alternatives, constraints, and evidence. Save reproducible commands, configuration, and benchmark input alongside any measurement. Review the generated code before claiming ownership or expertise; disclose assistance where an employer, course, or application requires it.

A milestone is complete only when its acceptance evidence exists. Do not retroactively mark this plan as completed or manufacture a commit calendar.
