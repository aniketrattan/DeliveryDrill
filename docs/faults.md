# Faults

Faults can be placed in `fault` or composed in a `faults` list. Supported fields:

| Fault | Example | Behaviour |
| --- | --- | --- |
| `duplicate` | `{count: 20, concurrency: 20}` | Repeat the same event; each batch waits on a barrier before sending. |
| `delay` | `{duration: 500ms}` or `{min: 100ms, max: 1s}` | Sleep before delivery; ranges use the scenario seed. |
| `retry` | `{attempts: 5, backoff: {initial: 100ms, multiplier: 2}}` | Send provider-style attempts with backoff. |
| `reorder` | `true` | Reverse a declared `sequence`. |
| `tamper_signature` | `true` | Alter one character of the generated HMAC. A 4xx response is expected. |
| `missing_signature` | `true` | Omit the signature header. A 4xx response is expected. |
| `malformed_json` | `true` | Send `not-json`. A 4xx response is expected. |
| `burst` | `{events: 1000, concurrency: 50}` | Bounded burst for concurrency diagnostics. |

All duplicate/burst counts must be positive and concurrency cannot exceed the count. A seed makes random delay choices reproducible.

