# DevOps Gateway - Ping Health-Check Contracts

This document defines the health-check connection contracts and validation rules supported by the DevOps Gateway for all service node protocols: **HTTP**, **WEBSOCKET**, **TCP**, **UDP**, **GRPC**, and **NONE**.

---

## 1. Supported Ping Protocols

The Gateway allows operators to configure a specific health-check protocol per Service Node. The active protocol is displayed in the node details screen and can be cycled in runtime.

### Summary of Protocol Contracts

| Protocol | Health-Check Validation | Latency (RTT) | Telemetry Collection |
|---|---|---|---|
| **`HTTP`** | HTTP GET returns status code `2xx` | Time until response headers read | Parsed from response JSON body |
| **`WEBSOCKET`**| Successful WS handshake & connection | Time until socket handshake opens | Parsed from first incoming Text message |
| **`TCP`** | Successful TCP three-way socket connection | Time to establish TCP handshake | Not supported |
| **`UDP`** | Datagram packet successfully dispatched | Time to send datagram packet | Not supported |
| **`GRPC`** | gRPC endpoint port is open/reachable | Time to establish TCP port check | Not supported |
| **`NONE`** | No active health-check performed | Not monitored (0 ms) | Disabled / Push-Only |

---

## 2. Active Monitoring (Pull-Based Specifications)

### 2.1. HTTP Contract

- **Method:** `GET`
- **Path:** Customizable (default: `/`).
- **Headers:** Optional custom authorization header (e.g. `X-Cluster-Token: token_secret`).
- **ONLINE criteria:** HTTP response status code is `200` to `299`.
- **Telemetry Payload Schema (JSON):**
  - `status` (String, Optional): `"UP"`
  - `language` (String, Optional): `"NodeJS"`, `"Python"`, etc.
  - `cpu` (Number, Optional): CPU load percentage (e.g., `5.2`).
  - `ram` (Number, Optional): RAM usage in MB (e.g., `42.1`).

### 2.2. WEBSOCKET (WS) Contract

- **Connection:** Standard WebSocket handshake over `ws://` or `wss://`.
- **Flow:**
  1. Gateway establishes connection and measures handshake latency.
  2. Gateway sends a text frame containing `"ping"`.
  3. Service node optionally responds with a text frame containing the **Telemetry JSON Payload** (same format as HTTP schema above).
  4. Gateway parses telemetry and closes the connection cleanly.
- **ONLINE criteria:** WebSocket handshake completes successfully.

### 2.3. TCP Contract

- **Connection:** Raw socket connection to the node's host IP and port.
- **ONLINE criteria:** Connection succeeds within the configured socket timeout.

### 2.4. UDP Contract

- **Connection:** Socketless Datagram transmission.
- **Flow:** Gateway dispatches a 1-byte datagram packet to the node host IP and port.
- **ONLINE criteria:** Packet is sent successfully without throwing local network or DNS exceptions.

### 2.5. GRPC Contract

- **Connection:** TCP socket connectivity check to verify gRPC server port availability.
- **ONLINE criteria:** TCP handshake succeeds on the gRPC port.

### 2.6. NONE Contract

- **Behavior:** The gateway skips scheduling any health checks.
- **ONLINE criteria:** Stays registered in the status assigned during registration (or updated via passive push-based telemetry).

---

## 3. Telemetry Schema (For HTTP and WebSocket Pull)

When a node uses `HTTP` or `WEBSOCKET` and is `ONLINE`, the Gateway parses the response body or text frame as JSON. All fields are optional:

```json
{
  "status": "UP",
  "language": "Go",
  "cpu": 2.1,
  "ram": 18.6,
  "message": "Dynamic telemetry update"
}
```
If `language` is omitted, the DevOps Panel falls back to displaying the transport protocol (`HTTP` or `WebSocket`).

---

## 4. Passive Monitoring (Push-Based Telemetry)

Instead of the Gateway actively pinging the service node (Pull-based), a service node can choose to actively push its metrics to the Gateway. This is especially useful for nodes using a `PingProtocol` of `NONE`.

### 4.1. HTTP Push-Based Endpoint

- **Method:** `GET` or `POST`
- **URL Path:** `/clusters/<clusterName>/telemetry`
- **Port:** Base gateway port + 1 (e.g. `3001` if base port is `3000`).
- **Query Parameters:**
  - `host` (String, Required): The registered host of the node (e.g. `localhost` or `http://localhost`).
  - `port` (Number, Required): The registered port of the node (e.g. `3004`).
  - `cpu` (Number, Optional): CPU load percentage (e.g. `2.5`).
  - `ram` (Number, Optional): RAM usage in MB (e.g. `45.3`).
  - `language` or `lang` (String, Optional): Node runtime environment (e.g. `Go`, `NodeJS`).
  - `latency` (Number, Optional): Custom-measured response latency in ms.
  - `status` (String, Optional): `ONLINE`, `UNSTABLE`, or `OFFLINE`.
  - `event` (String, Optional): Custom microservice event name to dispatch through the GateBridge event bus.
  - `protocol` (String, Optional): Source/application protocol for the submitted event, such as `http`, `ws`, `grpc`, `tcp`, or `udp`. Defaults to the node's configured ping protocol.
  - `format` (String, Optional): Payload format for event consumers, such as `json`, `text`, `protobuf`, or `cloudevent`. Defaults to `text`.
  - Any additional `key=value` pairs are included as event attributes when `event` is present. Reserved fields (`host`, `port`, `event`, `protocol`, `format`, `token`) are not forwarded as event attributes.
  
- **Example Request URL:**
  ```
  http://localhost:3001/clusters/production-cluster/telemetry?host=localhost&port=3004&cpu=2.5&ram=45.3&language=NodeJS&status=ONLINE
  ```
- **Example Request URL With Custom Event:**
  ```
  http://localhost:3001/clusters/production-cluster/telemetry?host=localhost&port=3004&event=cache.warmed&protocol=grpc&format=json&detail=products
  ```
- **Example Response:**
  ```
  SUCCESS: Telemetry updated for localhost:3004
  ```

### 4.2. Telnet Push-Based Command

- **Port:** Base gateway port (e.g. `3000`).
- **Syntax:**
  ```
  TELEMETRY <host> <port> [key=value] [key=value] ...
  ```
- **Example Command:**
  ```
  TELEMETRY localhost 3004 cpu=2.5 ram=45.3 language=NodeJS status=ONLINE
  ```
- **Example Command With Custom Event:**
  ```
  TELEMETRY localhost 3004 event=cache.warmed protocol=grpc format=json detail=products
  ```

---

## 5. Exposing Telemetry via JSON REST API

To retrieve the real-time telemetry metrics of all registered nodes in a cluster via an HTTP JSON endpoint, you can query:

- **Method:** `GET`
- **URL Path:** `/clusters/<clusterName>/get_nodes_json`
- **Port:** Base gateway port + 1 (e.g. `3001` if base port is `3000`).
- **Headers:** `X-Cluster-Token: <token_secret>`
- **Example Request URL:**
  ```
  http://localhost:3001/clusters/production-cluster/get_nodes_json
  ```
- **Example Response (`application/json`):**
  ```json
  [
    {
      "host": "http://localhost",
      "port": 3004,
      "status": "ONLINE",
      "isExternal": false,
      "pingProtocol": "HTTP",
      "pingPath": "/",
      "pingHeaderName": null,
      "pingHeaderValue": null,
      "latencyMs": 12,
      "cpuUsage": 5.3,
      "ramUsage": 42.1,
      "runtime": "NodeJS"
    }
  ]
  ```
