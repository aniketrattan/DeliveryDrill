# DeliveryDrill

DeliveryDrill is a Java 21 CLI for finding correctness bugs in webhook consumers under unreliable delivery: duplicates, synchronized concurrent retries, delays, reordered events, malformed bodies, and invalid HMAC signatures.

## Quick start

Prerequisites: Java 21 and a network connection for the first Gradle dependency download.

```bash
set WEBHOOK_SECRET=dev-secret
python examples/demo_server.py --mode resilient
gradlew run --args="run examples/payment/deliverydrill.yml --seed 42"
```

PowerShell users can set the secret with `$env:WEBHOOK_SECRET = 'dev-secret'`. The vulnerable demo is useful for seeing real failures:

```bash
python examples/demo_server.py --mode vulnerable
gradlew run --args="run examples/payment/deliverydrill.yml --seed 42 --verbose"
```

The CLI returns stable exit codes: `0` all tests pass, `1` a test fails, `2` invalid configuration, `3` an internal error, `4` the target is unavailable. `validate` never contacts the target.

```bash
gradlew run --args="validate examples/payment/deliverydrill.yml"
gradlew run --args="list-faults"
gradlew run --args="run examples/payment/deliverydrill.yml --report json --output build/deliverydrill.json --seed 42"
gradlew run --args="run examples/payment/deliverydrill.yml --report junit --output build/deliverydrill.xml --seed 42"
```

## Configuration

Scenarios are YAML files with a target, local JSON fixtures, named events, tests, faults, and optional HTTP assertions. See [docs/configuration.md](docs/configuration.md), [docs/faults.md](docs/faults.md), and [docs/assertions.md](docs/assertions.md). Secrets can be supplied with `signature.secret.env`; they are never included in reports.

## What is implemented

- Java 21 `HttpClient` delivery with explicit request timeouts and no redirects by default.
- HMAC-SHA256 (and SHA1) signatures in hex or Base64.
- Sequential and barrier-synchronized concurrent duplicate delivery.
- Fixed/seeded random delays, retries with exponential backoff, reverse ordering, malformed bodies, signature tampering/omission, and bounded bursts.
- HTTP status, header, body-contains, and small JSONPath-compatible assertions (`$.status`, array indexes, `.length`).
- Declarative monotonic state invariants that observe the target after each logical event and report regressions.
- Console, JSON, and JUnit XML reporters suitable for CI.
- Deterministic seeds printed in every report.

## Development

```bash
gradlew test
gradlew build
```

The framework deliberately does not ship a hosted inbox, dashboard, database connector, or general load-testing engine. Those are outside the correctness-testing boundary. The next extension points are retry/timeout overlap diagnostics, payload mutation, state-machine permutation generation, and failing-sequence shrinking.

## License

MIT. See [LICENSE](LICENSE).
