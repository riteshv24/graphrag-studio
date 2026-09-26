#!/usr/bin/env python3
"""Exercise a real local graphrag-studio workflow; stores new evidence in the selected workspace."""
from __future__ import annotations
import argparse
import json
import sys
import uuid
from pathlib import Path
from api_client import ApiClient

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8313")
    args = parser.parse_args()
    api = ApiClient(args.url)
    run_id = uuid.uuid4().hex[:8]

    collection = api.request("POST", "/api/collections", {"name": "CLI architecture " + run_id,
        "embeddingProvider": "hash", "chunkSize": 600, "overlap": 80})
    base = "/api/collections/" + collection["id"]
    sources = [("Identity ownership", "Platform Team -> OWNS -> Identity Service\nPlatform Team owns Identity Service and handles its incident response."),
               ("Payments dependency", "Payments Service -> DEPENDS_ON -> Identity Service\nPayments Service calls Identity Service to authorize payment requests.")]
    ids = []
    for title, text in sources:
        job = api.request("POST", base + "/documents", {"title": title, "text": text, "source": "CLI fixture"})
        api.wait(job["id"])
        ids.append(job["documentId"])
    question = "Who owns Identity Service and which service depends on it?"
    result = api.request("POST", base + "/query", {"question": question, "mode": "hybrid", "hops": 2})
    print(result["answer"])
    print("\nEvidence:")
    for source in result["sources"]:
        print(f"[{source['citation']}] {source['title']} — {source['explanation']}")
    job = api.request("POST", base + "/benchmarks", {"topK": 3,
        "cases": [{"question": question, "expectedDocumentIds": ids}]})
    api.wait(job["id"])
    print("\nComputed retrieval ablation:")
    print(json.dumps(api.request("GET", base + "/benchmarks")[-1]["summary"], indent=2))

if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, TimeoutError, OSError, ValueError) as error:
        print(f"Demo failed: {error}", file=sys.stderr)
        raise SystemExit(1)
