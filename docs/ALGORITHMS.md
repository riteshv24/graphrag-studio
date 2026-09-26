# Indexing, graphs, retrieval, and measurable limits

## Document generations and chunks

`DocumentService` checks title/text limits and content hashes before queuing indexing. A job prepares chunks and embeddings outside the store write transaction, then atomically commits the new document generation. An older indexing result cannot overwrite a newer generation. Deletion invalidates outstanding work. When reindexing an existing document, the previously committed version remains available until a replacement succeeds.

`Chunker` uses bounded overlapping character windows with original `[start, end)` offsets. It avoids splitting UTF-16 surrogate pairs. Every indexed passage can be traced back to the document text; this is more useful than showing a citation with no recoverable evidence.

Markdown and plain text are accepted. The system does not parse PDFs or fetch arbitrary URLs. A `source` field is a label, not an instruction to retrieve a remote page.

## Two explicitly different embedding modes

`Embeddings` supplies a 256-dimensional normalized signed-hash baseline from tokens and character trigrams. This offline vector is a lexical feature representation—not a learned semantic embedding. Hash collisions and lexical mismatch are expected limitations.

An optional Ollama embedding adapter sends actual requests to the configured model. Each stored vector includes an embedding-space signature with provider/model/dimension. Queries reject a mismatched space and require reindexing; vectors from different models are not quietly compared.

Cosine similarity is computed over equal-length vectors. The bounded workspace uses a linear scan, not an approximate-nearest-neighbor service. No performance claim is inferred from the existence of a vector field.

## BM25 and reciprocal-rank fusion

`Bm25` builds term frequencies, document frequencies, and mean chunk length. It scores title plus chunk text, using k1=1.2 and b=0.75:

```text
IDF(t) = log(1 + (N - df(t) + 0.5) / (df(t) + 0.5))
BM25(t,d) = IDF(t) * tf(t,d)*(k1+1)
            / (tf(t,d) + k1*(1-b+b*len(d)/avglen))
```

The query uses distinct non-stopword terms. Each ranked lexical/vector candidate list is capped at 40 and tie-broken by chunk ID. Hybrid mode sums reciprocal-rank terms `1 / (60 + rank)` instead of adding incomparable raw BM25 and cosine scores. Graph-expanded passages contribute `0.6 / (60 + hops)`.

The interface exposes raw BM25, cosine, lexical/vector ranks, graph hops, and source paths alongside the fused ranking. Graph expansion is bounded to two hops for retrieval. A caller can choose lexical-only, vector-only, or hybrid mode.

## What the graph actually means

`EntityExtractor` recognizes explicit typed lines such as:

```text
Platform Team -> OWNS -> Identity Service
Payments Service -> DEPENDS_ON -> Identity Service
```

Aliases normalize common service labels. A proper-name heuristic supplies additional entity mentions. Relationship keywords are not promoted to entity names. Nodes and edges retain chunk/document provenance.

`CO_OCCURS` is a separately labelled weak relationship. It means two mentions share a chunk; it does **not** establish a dependency or ownership fact. Weak edges are excluded from retrieval expansion. The separate shortest-path exploration API can traverse the graph for inspection and is not a proof that a real-world causal path exists.

Graph assertions are claims in uploaded documents. The engine does not independently verify them. JSON, GraphML, and Cypher are real export formats. Neo4j is an optional export destination, not the application's live storage backend.

## Abstention and answer evidence

The offline baseline abstains when there is no lexical evidence. With configured semantic embeddings, the current no-lexical-match path also requires a cosine of at least 0.55. This threshold is a heuristic, not a universally calibrated relevance probability.

`AnswerComposer` defaults to extracting up to three relevant sentences with source IDs. The optional model path checks that returned citation identifiers refer to supplied sources. Missing or unknown citation identifiers cause abstention. Valid identifiers do **not** prove that each generated claim is supported; entailment verification is not implemented.

Query history stores an evidence snapshot with document version, offsets, passages, and ranking explanation. Later corpus edits therefore do not rewrite a historical answer's displayed source context. Human feedback uses optimistic revisions.

## Retrieval ablations, precisely defined

`BenchmarkService` snapshots the corpus when a benchmark is queued, then evaluates the same manually labelled questions with lexical, vector, and hybrid retrieval. It retrieves at most K **chunks**, deduplicates their document IDs while retaining order, and scores against expected document IDs:

```text
Recall@K = relevant unique retrieved documents / expected relevant documents
RR       = 1 / first relevant rank in the deduplicated document list
MRR      = mean RR across labelled questions
```

Thus K is the chunk retrieval budget, while relevance and reciprocal rank are document-level. Multiple chunks from one document can consume K without increasing document recall. Latency is measured per query; there is no confidence interval or statistically significant ranking claim. The included small fictional fixture is for reproducible behavior, not a benchmark proving that hybrid always wins.

## Capacity and tradeoffs

Corpus retrieval is linear in retained chunks/vector dimensions; BM25 statistics and the graph are rebuilt for the selected immutable corpus. This makes ranking transparent and avoids external service requirements, at the cost of scale. Current limits include 80 documents per collection, 180 chunks per document, 2,500 query-corpus chunks, and top K at most 12. These are protective limits, not load-tested capacity guarantees.
