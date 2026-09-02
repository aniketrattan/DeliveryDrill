# Architecture

`ScenarioLoader` parses YAML and validates references and safety constraints before any network call. `DeliveryEngine` expands each test into delivery attempts, applies fault settings, signs the exact bytes sent, observes declared state invariants after each logical event, and collects structured outcomes. `AssertionEngine` performs post-delivery HTTP checks and JSONPath extraction. Reporter implementations serialize the same `SuiteResult` to concise console text, JSON, or JUnit XML.

The delivery engine keeps event identity (`event_id`) separate from attempt number, which makes duplicate and retry diagnostics meaningful. Concurrent batches use a `CyclicBarrier`; a test configured for concurrency therefore cannot silently degrade to sequential sends.
