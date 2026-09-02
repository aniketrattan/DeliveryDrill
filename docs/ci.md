# CI

The CLI's exit status is CI-friendly (`0` pass, `1` test failure, `2` configuration, `3` internal error, `4` target unavailable). A minimal GitHub Actions job is:

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
- run: ./gradlew test
- run: ./gradlew run --args="run tests/webhooks.yml --report junit --output build/deliverydrill.xml --seed 42"
- uses: actions/upload-artifact@v4
  if: always()
  with:
    name: deliverydrill-report
    path: build/deliverydrill.xml
```

Only target systems you own or are authorized to test. Keep high-volume scenarios local or behind an explicit CI environment boundary.

This repository's workflow also runs `python examples/ci_smoke.py` after `installDist`. It starts the two local demo modes, verifies the resilient consumer passes the state invariant, and verifies the vulnerable consumer fails with a reported `COMPLETED` to `PROCESSING` regression.
