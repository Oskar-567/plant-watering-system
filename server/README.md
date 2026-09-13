# Plant Watering Server

Spring Boot REST API — central hub of the plant watering system. Receives sensor data from an ESP32 via MQTT, stores it together with plant configuration and watering history in PostgreSQL, and exposes a REST API for the mobile app.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.0 |
| Build | Maven |
| Relational DB | PostgreSQL (JPA + Flyway) |
| Messaging | MQTT via Eclipse Paho (Mosquitto broker) |
| Auth | Spring Security + JWT (JJWT 0.13.0) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, MockMvc, Mockito |

## Architecture

```
[ESP32] ──MQTT──> [Mosquitto] ──> [Server] ──> [PostgreSQL]
[ESP32] <──MQTT── [Mosquitto] <── [Server]
[React Native App] <──REST──> [Server] <──JPA──> [PostgreSQL]
```

The ESP32 publishes sensor readings (soil moisture, flow, battery) via MQTT. The server subscribes to those topics, persists the data in PostgreSQL, and serves it to the mobile app via REST. Watering commands flow in the opposite direction: app → server → MQTT → ESP32.

## Prerequisites

- Java 25
- Docker Desktop (for local PostgreSQL)
- A running Mosquitto broker (see [`infra/apps/mosquitto/`](../k3s-homelab/infra/apps/mosquitto/))

## Running Locally

**1. Create `src/main/resources/application-local.properties`** (gitignored):

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://<host>:5432/plantdb
DB_USERNAME=plant
DB_PASSWORD=secret

# JWT
JWT_SECRET=your-secret-min-32-chars

# App
APP_PASSWORD=your-password

# MQTT
MQTT_BROKER=<host>
MQTT_USERNAME=plant
MQTT_PASSWORD=your-password
```

**2. Run the server:**

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

Server starts on `http://localhost:8080`.

> **PowerShell note:** Wrap `-D` flags in quotes, e.g. `"-Dspring-boot.run.profiles=local"`

## Required Setup: Register an Instance

The server silently drops every MQTT message (moisture, flow, battery, status) until a matching `Instance` row exists in Postgres — no error, just `WARN ... No instance found for mqtt prefix: <prefix>` in the logs. No sensor readings are stored, the app has nothing to show, and `TankEmptyDetectionService`/watering-history logging never trigger, even though the ESP32↔MQTT connection itself works fine.

**Before anything else, create an `Instance` for each ESP32** via `POST /instances` (see the login example below). The critical field is `mqttPrefix`:

- It must exactly match the **first path segment** of the topics the ESP32 publishes/subscribes to (`MqttMessageHandler` splits the topic on the first `/` and looks up only that segment).
- The firmware publishes to topics like `plant/sensors/battery`, `plant/status`, etc. — so `mqttPrefix` must be `"plant"`, **not** `"plant/balcony"` or any other multi-segment value. A multi-segment prefix will never match and messages get dropped exactly like a missing instance.

If sensor data isn't showing up anywhere, check the instance's `mqttPrefix` first before suspecting the database or MQTT connectivity.

## Key Commands

| Command | Description |
|---|---|
| `./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"` | Run locally with local profile |
| `./mvnw test` | Run all tests |
| `./mvnw package "-DskipTests"` | Build JAR (used by CI/CD) |

## API

Interactive docs available at `http://localhost:8080/swagger-ui.html` when the server is running.

### Implemented Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/login` | — | Send password, receive JWT |
| `GET` | `/instances` | Bearer | List all instances |
| `GET` | `/instances/{id}` | Bearer | Get a single instance |
| `POST` | `/instances` | Bearer | Create a new instance |
| `POST` | `/instances/{id}/pump/start` | Bearer | Start pump — publishes MQTT command, opens WateringEvent |
| `POST` | `/instances/{id}/pump/stop` | Bearer | Stop pump — publishes MQTT command |
| `GET` | `/instances/{id}/watering-history` | Bearer | List all watering events descending by start time |
| `GET` | `/instances/{id}/moisture?range=24h` | Bearer | Soil moisture history (range: 1–999 + m/h/d, default 24h) |
| `GET` | `/instances/{id}/battery?range=24h` | Bearer | Battery (soc + voltage) history (range: 1–999 + m/h/d, default 24h) |

All endpoints except `/auth/login` require `Authorization: Bearer <token>`.

### Login Example (IntelliJ HTTP Client)

```http
### Login — token is saved automatically
POST http://localhost:8080/auth/login
Content-Type: application/json

{"password": "{{APP_PASSWORD}}"}

> {%
  client.global.set("token", response.body.token);
%}

###

### Create instance
POST http://localhost:8080/instances
Authorization: Bearer {{token}}
Content-Type: application/json

{"name": "Balcony", "mqttPrefix": "plant", "hasPump": true, "hasBattery": true, "sensorCount": 1}
```

> `mqttPrefix` is the first segment of the MQTT topics only — see "Required Setup" above. `"plant/balcony"` would silently never match anything.

## MQTT Topics

| Direction | Topic | Payload |
|---|---|---|
| ESP32 → Server | `plant/sensors/moisture` | `{"sensor_0":42,"sensor_1":67,"sensor_2":55}` |
| ESP32 → Server | `plant/sensors/flow` | `{"liters":0.35}` |
| ESP32 → Server | `plant/sensors/battery` | `{"soc":78.1,"voltage":3.91}` |
| ESP32 → Server | `plant/status` | `{"pump":"on"}` |
| Server → ESP32 | `plant/pump/command` | `{"action":"start"}` / `{"action":"stop"}` |

## Testing

```bash
./mvnw test
```

Tests run without any external dependencies:
- **Unit tests** (`JwtTokenProviderTest`, `PumpServiceTest`, `TankEmptyDetectionServiceTest`) — no Spring context, Mockito only
- **Web-layer tests** (`@WebMvcTest`) — controller + security only, services mocked via `@MockitoBean`

> Note: Spring Boot 4 requires `spring-boot-starter-webmvc-test` for `@WebMvcTest`. Use `@MockitoBean` instead of the deprecated `@MockBean`.

## Deployment

Deployed automatically via GitOps — push to `server/` on `main` triggers the pipeline:

1. GitHub Actions builds the JAR (`mvn package -DskipTests`) and packages it into an arm64 Docker image via `Dockerfile`
2. Image is pushed to GHCR (`ghcr.io/oskar-567/plant-watering-system-server`)
3. Pipeline updates the image SHA in `k3s-homelab/infra/apps/plant-watering-system-server/deployment.yaml`
4. Flux CD picks up the change and rolls out the new version on k3s

Live at: `http://192.168.73.150:30080`

See [k3s-homelab](../../k3s-homelab/) for Kubernetes manifests.
