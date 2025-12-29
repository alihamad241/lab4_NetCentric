# Weather Station System

A distributed weather monitoring system built with Java, Apache Kafka, and PostgreSQL, deployed on Kubernetes.

## Overview

This system simulates 10 weather stations that generate weather data and send it to a central station for analysis through Kafka. A rain detector processes humidity alerts in real-time.

### Architecture

```
┌─────────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌────────────┐
│ Weather Stations│────▶│    Kafka    │────▶│ Central Station │────▶│ PostgreSQL │
│   (10 pods)     │     │             │     │                 │     │            │
└─────────────────┘     │             │     └─────────────────┘     └────────────┘
                        │             │
                        │             │────▶┌─────────────────┐
                        │             │     │  Rain Detector  │
                        └─────────────┘     │ (Kafka Streams) │
                              ▲             └─────────────────┘
                              │
                        ┌─────────────┐
                        │  Zookeeper  │
                        └─────────────┘
```

### Features

-   **Weather Stations**: Generate readings every 1 second with simulated battery status (30% low, 40% medium, 30% high) and 10% message drop rate
-   **Kafka**: Message broker for reliable data streaming
-   **Central Station**: Consumes messages and persists to PostgreSQL in batches
-   **Rain Detector**: Kafka Streams processor that triggers alerts when humidity > 70%

---

## Prerequisites

-   **Java 8+** (for local builds)
-   **Maven 3.6+**
-   **Docker** (Docker Desktop or Docker Engine)
-   **kubectl** (Kubernetes CLI)
-   **Minikube** (local Kubernetes cluster)

### Installing Minikube (Linux x86_64)

```bash
# Download minikube
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64

# Install locally
install minikube-linux-amd64 minikube

# Start cluster with Docker driver
./minikube start --driver=docker
```

---

## Quick Start

### 1. Clone and Build

```bash
# Navigate to project directory
cd lab4_NetCentric

# Build the project
mvn clean package
```

### 2. Build Docker Image

```bash
docker build -t hanine-weather-app:latest .
```

### 3. Load Image into Minikube

```bash
./minikube image load hanine-weather-app:latest
```

### 4. Deploy to Kubernetes

```bash
kubectl apply -f k8s/combined-manifests.yaml
```

### 5. Database Initialization

The database is initialized **automatically** on startup via the `postgres-init` ConfigMap. This creates the `weather_readings` table and necessary indexes.

If you need to manually re-run the initialization or apply new SQL:

```bash
# Get postgres pod name
export PG_POD=$(kubectl get po -l app=postgres -o jsonpath='{.items[0].metadata.name}')

# Execute the local init script
kubectl exec -i $PG_POD -- psql -U admin -d weather_db < k8s/init.sql.file
```

### 6. Verify Deployment

```bash
# Watch pods starting
kubectl get po -w

# Wait until all pods show "Running" status
```

Expected output (15 pods):

-   `central-station-*` (1 pod)
-   `kafka-0` (1 pod)
-   `postgres-db-*` (1 pod)
-   `rain-detector-*` (1 pod)
-   `weather-stations-*` (10 pods)
-   `zookeeper-0` (1 pod)

---

## Verification & Analysis

### Check Database Records

```bash
# Get total record count
kubectl exec -it $(kubectl get po -l app=postgres -o jsonpath='{.items[0].metadata.name}') \
  -- psql -U admin -d weather_db -c "SELECT COUNT(*) FROM weather_readings;"
```

### Check Battery Status Distribution

```bash
kubectl exec -it $(kubectl get po -l app=postgres -o jsonpath='{.items[0].metadata.name}') \
  -- psql -U admin -d weather_db -c \
  "SELECT station_id, battery_status, COUNT(*) as count,
   ROUND((COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY station_id)), 2) as percentage
   FROM weather_readings WHERE station_id = 1
   GROUP BY station_id, battery_status
   ORDER BY station_id, battery_status;"
```

Expected distribution: ~30% low, ~40% medium, ~30% high

### Run Full Analysis

```bash
# Copy analysis script
kubectl cp k8s/analysis.sql $(kubectl get po -l app=postgres -o jsonpath='{.items[0].metadata.name}'):/tmp/

# Execute analysis
kubectl exec -it $(kubectl get po -l app=postgres -o jsonpath='{.items[0].metadata.name}') \
  -- psql -U admin -d weather_db -f /tmp/analysis.sql
```

### Check Kafka Messages

```bash
kubectl exec -it kafka-0 -- kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic weather_readings \
  --max-messages 5
```

---

## Monitoring

### View Component Logs

```bash
# Weather stations
kubectl logs -l app=weather-stations --tail=10

# Central station
kubectl logs -l app=central-station

# Rain detector
kubectl logs -l app=rain-detector
```

### Check Kafka Consumer Group

```bash
kubectl exec -it kafka-0 -- kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group central-station-group \
  --describe
```

---

## Project Structure

```
lab4_NetCentric/
├── src/main/java/com/netcentric/lab4/
│   ├── station/WeatherStation.java      # Weather data generator
│   ├── central/CentralStation.java      # Kafka consumer & DB writer
│   └── processor/RainingTrigger.java    # Rain alert processor
├── k8s/
│   ├── combined-manifests.yaml          # All K8s resources
│   └── analysis.sql                     # Analysis queries
├── Dockerfile                            # Docker build config
└── pom.xml                              # Maven dependencies
```

---

## Cleanup

```bash
# Delete all resources
kubectl delete -f k8s/combined-manifests.yaml

# Stop Minikube
./minikube stop

# Delete cluster (optional)
./minikube delete
```

---

## Troubleshooting

### Pods stuck in "Pending" or "ImagePullBackOff"

```bash
# Check pod events
kubectl describe po <pod-name>

# Reload image
./minikube image load hanine-weather-app:latest
kubectl delete po <pod-name>
```

### No data in database

The Central Station uses batch inserts (100 records). Wait 1-2 minutes for data to accumulate, then check again.

### Kafka not starting

Ensure Zookeeper is running first:

```bash
kubectl get po zookeeper-0
kubectl logs zookeeper-0
```

---

## Configuration

Key environment variables in `k8s/combined-manifests.yaml`:

| Component       | Variable                         | Default              |
| --------------- | -------------------------------- | -------------------- |
| Kafka           | `KAFKA_CFG_ADVERTISED_LISTENERS` | `kafka-service:9092` |
| Central Station | `KAFKA_BOOTSTRAP_SERVERS`        | `kafka-service:9092` |
| PostgreSQL      | `POSTGRES_DB`                    | `weather_db`         |
| PostgreSQL      | `POSTGRES_USER`                  | `admin`              |
| PostgreSQL      | `POSTGRES_PASSWORD`              | `password`           |

---
