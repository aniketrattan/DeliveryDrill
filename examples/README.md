# Demo consumer

The standard-library demo makes the framework's purpose visible without Docker or a second build.

```bash
set WEBHOOK_SECRET=dev-secret       # PowerShell: $env:WEBHOOK_SECRET = "dev-secret"
python examples/demo_server.py --mode resilient
deliverydrill run examples/payment/deliverydrill.yml --seed 42 --verbose
```

Run `--mode vulnerable` to see the duplicate and ordering tests fail. The vulnerable mode is intentionally unsafe and must never be used outside a local demo.

For an isolated idempotency-race demonstration (so state from earlier tests cannot affect the count), run `examples/payment/concurrent-only.yml`. The vulnerable consumer reports more than one transaction; the resilient consumer reports exactly one.

The state invariant example (`examples/payment/state-invariant.yml`) observes the target after each event and fails when a vulnerable consumer moves from `COMPLETED` back to `PROCESSING`. The resilient consumer keeps the state at `COMPLETED` and passes.
