# GraphRAG Studio walkthrough

## Trace a connection back to its source

Start `./run.sh`, open port 8313, and wait until the eight fictional Northstar documents finish indexing.

1. Select an entity in the graph. Inspect connected relationships and their source documents. Toggle weak co-occurrence separately from explicit typed relationships.
2. Ask: **Who owns Identity Service and which services depend on it?** Use hybrid mode and inspect the answer alongside source IDs and passages. A citation should lead to real stored text.
3. Open the evidence view and compare BM25, cosine, rank, and graph-hop contributions. Explain that the default vector is lexical hashing, not a learned semantic model.
4. Repeat with lexical and vector modes. Open the benchmark lab and run its manually labelled fixture. Discuss the measured retrieval results without claiming one mode is universally better.
5. Import a short Markdown document containing a typed ownership or dependency triple. After its indexing job succeeds, observe the new provenance-bearing edge.
6. Reindex or delete that document. The graph should reflect committed document versions, while historical query evidence remains a snapshot of the answer's original sources.

Run `python scripts/demo.py` to create a separate two-document collection, execute an actual query, and measure its three-mode retrieval ablation.

## Follow the code

Read `Chunker` and `DocumentService` for generation-safe indexing, `Embeddings` and `Bm25` for candidates, `EntityExtractor` and `KnowledgeGraph` for relationships, `Retriever` for fusion, and `AnswerComposer` for extractive/model output. `BenchmarkService` defines precisely how relevance is scored.

## Questions to answer in a technical discussion

**Why reciprocal-rank fusion instead of adding raw scores?** BM25 and cosine have different scales. Fusion combines rank positions while leaving the original score evidence inspectable.

**Why preserve document offsets?** A citation without recoverable text is difficult to audit. Original offsets and versions make it possible to confirm the exact input passage.

**Why not treat co-occurrence as a dependency?** Mentioning two services together is not enough evidence that one calls the other. Weak edges are labelled separately and excluded from retrieval expansion.

**What happens after changing the embedding model?** The vector signature mismatch rejects the query and requires reindexing. Mixing vector spaces would produce meaningless comparisons.

**Does a valid citation prove the answer?** No. The model path checks identifier validity, not entailment. The default extractive path is easier to inspect but can still select an irrelevant sentence.

## Suggested first personal contribution

Build a curated relevance dataset with paraphrases, ambiguous entity names, conflicting documents, and deliberately unanswerable questions. Record relevance judgments before comparing modes. Add confidence intervals only after collecting enough independent cases; publish measured results and the exact corpus/model/configuration.
