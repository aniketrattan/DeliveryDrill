# Configuration

The current schema is version `1`.

```yaml
version: 1
name: payment-settlement
target:
  url: http://localhost:8080/webhooks
  method: POST
  timeout: 5s
headers:
  Content-Type: application/json
signature:
  type: hmac
  algorithm: sha256
  header: X-Signature
  secret:
    env: WEBHOOK_SECRET
events:
  completed:
    file: fixtures/completed.json
    event_id: evt-completed-1
    event_type: payment.completed
tests:
  - name: duplicate
    event: completed
    fault:
      duplicate: { count: 10, concurrency: 10 }
assertions:
  - name: state
    request: { method: GET, url: http://localhost:8080/payments/P123 }
    expect: { status: 200, json: { $.status: COMPLETED } }
```

Fixture paths are resolved relative to the scenario file and may not escape its directory. An event requires a fixture and stable `event_id`; `event_type` is optional and becomes `X-Event-Type`. Test names default to `test-N`.

