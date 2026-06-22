# Plant Watering Server

Spring Boot REST API — central hub of the plant watering system. Receives sensor data from an ESP32 via MQTT, stores it in InfluxDB, manages plant configuration and watering schedules in PostgreSQL, and exposes a REST API for the mobile app.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.0 |
| Build | Maven |
| Relational DB | PostgreSQL 16 (JPA + Flyway) |
| Time-series DB | InfluxDB 2.x (influxdb-client-java 8.0.0) |
| Messaging | MQTT via Eclipse Paho (Mosquitto broker) |
| Auth | Spring Security + JWT (JJWT 0.13.0) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5, Testcontainers |

## Architecture

```
[ESP32] ──MQTT──> [Mosquitto] ──> [Server] ──> [InfluxDB]
[ESP32] <──MQTT── [Mosquitto] <── [Server]
[React Native App] <──REST──> [Server] <──JPA──> [PostgreSQL]
```

The ESP32 publishes sensor readings (soil moisture, flow, battery) via MQTT. The server subscribes to those topics, persists the data in InfluxDB, and serves it to the mobile app via REST. Watering commands flow in the opposite direction: app → server → MQTT → ESP32.

## Prerequisites

- Java 25
- Docker (for local PostgreSQL + InfluxDB)
- A running Mosquitto broker (see [`infra/apps/mosquitto/`](../k3s-homelab/infra/apps/mosquitto/))

## Running Locally

**1. Start dependencies:**

```bash
docker compose up -d
```

Starts PostgreSQL on port `5432` and InfluxDB on port `8086`.

**2. Create `src/main/resources/application-local.properties`** (gitignored):

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/plantdb
DB_USERNAME=plant
DB_PASSWORD=secret

# InfluxDB
INFLUX_URL=http://localhost:8086/
INFLUX_TOKEN=local-dev-token
INFLUX_ORG=plantorg
INFLUX_BUCKET=plant-sensors

# JWT
JWT_SECRET=local-dev-secret-key-min-32-chars-long-replace-in-prod

# App
APP_PASSWORD=localdev

# MQTT
MQTT_BROKER=localhost
MQTT_USERNAME=plant
MQTT_PASSWORD=your-mosquitto-password
```

**3. Run the server:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Server starts on `http://localhost:8080`.

## Key Commands

| Command | Description |
|---|---|
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Run locally |
| `./mvnw test` | Run tests |
| `./mvnw spring-boot:build-image -DskipTests` | Build Docker image |
| `docker compose up -d` | Start local DB dependencies |
| `docker compose down` | Stop local DB dependencies |

## API

Interactive docs available at `http://localhost:8080/swagger-ui.html` when the server is running.

### Planned Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Authenticate, returns JWT |
| `GET` | `/plants` | List all plants |
| `POST` | `/plants` | Create a plant |
| `GET` | `/plants/{id}` | Get plant details |
| `GET` | `/plants/{id}/moisture` | Soil moisture history (InfluxDB) |
| `POST` | `/plants/{id}/water` | Trigger watering |
| `GET` | `/plants/{id}/schedules` | List watering schedules |
| `POST` | `/plants/{id}/schedules` | Create watering schedule |

All endpoints except `/auth/login` require `Authorization: Bearer <token>`.

## MQTT Topics

| Direction | Topic | Payload |
|---|---|---|
| ESP32 → Server | `plant/sensors/moisture` | `{"sensor_0":42,"sensor_1":67,"sensor_2":55}` |
| ESP32 → Server | `plant/sensors/flow` | `{"liters":0.35}` |
| ESP32 → Server | `plant/sensors/battery` | `{"soc":78.1,"voltage":3.91}` |
| ESP32 → Server | `plant/status` | `{"pump":"on"}` |
| Server → ESP32 | `plant/pump/command` | `{"action":"start"}` / `{"action":"stop"}` |

## Testing

Tests use Testcontainers — Docker must be running.

```bash
./mvnw test
```

PostgreSQL and InfluxDB are spun up automatically per test run. No external dependencies needed.

## Deployment

Built as a Docker image and deployed to a k3s cluster. See the [homelab repo](../k3s-homelab/) for Kubernetes manifests.

```bash
./mvnw spring-boot:build-image -DskipTests
```
