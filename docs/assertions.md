# Assertions

Assertions run after a test's deliveries. They issue an independent HTTP request and can check:

- `expect.status`: exact status code;
- `expect.headers`: case-insensitive header values;
- `expect.body_contains`: a literal substring;
- `expect.json`: JSONPath-compatible scalar checks.

Supported paths start at `$` and include object properties (`$.status`), array indexes (`$.transactions[0].id`), and array/object size (`$.transactions.length`). A failed assertion includes expected and actual values in console and machine-readable reports.

## State invariants

An `invariants` entry observes a JSON field after each logical event in a test and enforces a monotonic order:

```yaml
invariants:
  - name: payment state cannot regress
    source: { method: GET, url: http://localhost:8080/payments/P123 }
    field: $.status
    order: [CREATED, PROCESSING, COMPLETED]
```

If the observed value moves backward, the test fails with the exact transition (for example, `COMPLETED` to `PROCESSING`). This is intentionally bounded: it checks a declared sequence and does not yet generate or shrink permutations automatically.
