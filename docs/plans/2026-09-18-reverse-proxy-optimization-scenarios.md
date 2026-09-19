# Reverse Proxy Multi-Scenario Optimization & Benchmarking Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement persistent HTTP connection pooling for `ReverseProxyService`, add backpressure concurrency cap controls, apply OS kernel socket tuning, and execute a matrix of 5 benchmark scenarios (Baseline, Pooling Only, Cap Only, OS Tuning Only, Full Combo) under 1GB RAM constraints to measure peak Goodput RPS and latency improvements.

**Architecture:** Refactor `JdkHttpProxyClient` to maintain a pooled persistent HTTP/1.1 connection manager (`PooledHttpProxyClient`), integrate active request concurrency backpressure in `ProxyBenchmarkApplication`, and execute automated 13-tier stress tests comparing throughput, p99 latency, and OS port saturation.

**Tech Stack:** Java 21 VirtualThreads, Undertow 2.3 HTTP Server, `java.net.http.HttpClient` with persistent pool settings, Linux sysctl network tuning, `BenchmarkRunner` CLI engine.

## Global Constraints
- Package: `hexacloud.infra.server` & `hexacloud.application`
- Memory budget: Maximum 1GB RAM (`-Xms256m -Xmx512m`)
- Fast-Path Bypass: DISABLED
- Local branch: `feature/1gb-ram-optimization` (do NOT push to remote)

---

### Task 1: Implement Persistent HTTP Connection Pooling in `JdkHttpProxyClient`

**Files:**
- Modify: `java/src/hexacloud/core/utils/network/JdkHttpProxyClient.java:12-30`
- Create/Test: `java/test/hexacloud/core/utils/network/JdkHttpProxyClientTest.java`

**Interfaces:**
- Consumes: `HttpProxyClient` interface
- Produces: Persistent pooled `HttpClient` with HTTP/1.1 Keep-Alive header enforcement and socket reuse

- [ ] **Step 1: Write unit test for persistent connection reuse in `JdkHttpProxyClientTest.java`**

```java
package hexacloud.core.utils.network;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JdkHttpProxyClientTest {

    private HttpServer server;
    private AtomicInteger connectionCount = new AtomicInteger(0);

    @BeforeEach
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(3099), 0);
        server.createContext("/test", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                connectionCount.incrementAndGet();
                byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void testPersistentConnectionExecution() throws Exception {
        JdkHttpProxyClient client = new JdkHttpProxyClient();
        ProxyResponse resp1 = client.execute("http://127.0.0.1:3099/test", "GET", new HashMap<>(), null, 2000);
        assertEquals(200, resp1.statusCode());

        ProxyResponse resp2 = client.execute("http://127.0.0.1:3099/test", "GET", new HashMap<>(), null, 2000);
        assertEquals(200, resp2.statusCode());
        assertTrue(connectionCount.get() >= 2);
    }
}
```

- [ ] **Step 2: Run test to verify initial behavior**

Run: `mvn test -Dtest=JdkHttpProxyClientTest`
Expected: PASS

- [ ] **Step 3: Update `JdkHttpProxyClient.java` with explicit HTTP Keep-Alive & Connection Pool configurations**

```java
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                .build();
```

- [ ] **Step 4: Run test to verify it passes cleanly**

Run: `mvn test -Dtest=JdkHttpProxyClientTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add java/src/hexacloud/core/utils/network/JdkHttpProxyClient.java java/test/hexacloud/core/utils/network/JdkHttpProxyClientTest.java
git commit -m "feat(proxy): optimize JdkHttpProxyClient connection pool and Keep-Alive settings"
```

---

### Task 2: Execute Scenario B (Persistent Connection Pooling Only)

**Files:**
- Target: `http://127.0.0.1:4001/proxy/hello`
- Script: `scripts/benchmark.sh`

- [ ] **Step 1: Start UpstreamMockServer & ProxyBenchmarkApplication in background**
- [ ] **Step 2: Run 13-tier stress benchmark**

Run: `mvn exec:java -Dexec.mainClass="hexacloud.infra.benchmark.BenchmarkRunner" -Dexec.args="--mode=stress --protocol=http --target=http://127.0.0.1:4001/proxy/hello --warmup=2s --duration=3s --runs=1"`

- [ ] **Step 3: Record Goodput RPS, p50, p99 latency, and RAM usage for Scenario B**

---

### Task 3: Execute Scenario C (Active Requests Cap / Backpressure Limiter Only)

**Files:**
- Modify: `java/src/hexacloud/application/ProxyBenchmarkApplication.java` (enable `rateLimit` / active requests cap)

- [ ] **Step 1: Configure active request cap of 1500 in `ProxyBenchmarkApplication.java`**
- [ ] **Step 2: Start server and execute 13-tier stress benchmark**

Run: `mvn exec:java -Dexec.mainClass="hexacloud.infra.benchmark.BenchmarkRunner" -Dexec.args="--mode=stress --protocol=http --target=http://127.0.0.1:4001/proxy/hello --warmup=2s --duration=3s --runs=1 --cap=1500"`

- [ ] **Step 3: Record Goodput RPS, 503 CAP protection rate, p99 latency, and RAM usage for Scenario C**

---

### Task 4: Execute Scenario D (Linux OS Kernel Network Socket Tuning Only)

**Files:**
- OS Kernel sysctl settings

- [ ] **Step 1: Apply Linux network kernel socket parameters**

Run:
`sudo sysctl -w net.ipv4.tcp_tw_reuse=1`
`sudo sysctl -w net.core.somaxconn=65535`
`sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"`

- [ ] **Step 2: Run 13-tier stress benchmark without cap or pooling changes**
- [ ] **Step 3: Record Goodput RPS, p50, p99 latency, and RAM usage for Scenario D**

---

### Task 5: Execute Scenario E (Full Stack Combo Optimization - All Combined)

**Files:**
- Combined: Connection Pooling + Active Request Cap + Linux OS Kernel Tuning

- [ ] **Step 1: Enable connection pooling + OS kernel tuning + active request cap**
- [ ] **Step 2: Run full 13-tier stress benchmark up to 25,000 concurrent clients**
- [ ] **Step 3: Consolidate comparative results matrix across Scenarios A, B, C, D, and E**
