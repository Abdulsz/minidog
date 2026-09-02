# MiniDog load test

The load generator uses only Java 21 and sends fixed-size metric batches with
configurable concurrency. It reports accepted events per second, HTTP 429 load
shedding, request failures, and p50/p95/p99 request latency.

Start MiniDog, then compile and run the generator from the repository root:

```powershell
javac load-test/LoadGenerator.java
java -cp load-test LoadGenerator http://localhost:8080/ingest 32 15 50
```

Arguments are endpoint, concurrency, duration in seconds, and batch size.
The generated `.class` file is ignored by the repository's existing Java
ignore rule.
