# ☁️ Comprehensive Multi-VM Cloud Deployment Guide

This guide provides an in-depth, step-by-step procedure to deploy your Weather Monitoring System across **two separate Cloud Virtual Machines (VMs)**. This architecture demonstrates real-world distributed networking and decoupling.

---

## 🛠 Prerequisites

Before starting, ensure you have:

1.  **Two Cloud VMs** (AWS EC2, DigitalOcean Droplet, etc.):
    -   **VM 1 (Central)**: At least 4GB RAM (Kafka and Postgres are memory-heavy).
    -   **VM 2 (Stations)**: 1GB-2GB RAM is sufficient for 10 station containers.
2.  **Docker Installed** on both:
    -   Modern Docker includes the `docker compose` command (replacing the older `docker-compose`).
3.  **SSH Access**: You should be able to SSH into both machines from your local terminal.

---

## 🔐 Phase 1: Networking & Security (Firewalls)

This is where most cloud deployments fail. You must allow communication between the two VMs.

### VM 1 (Central Base) Configuration

Open your cloud provider's **Security Group** or **Firewall** settings for VM 1:

-   **Port 22 (SSH)**: Allow from your local IP.
-   **Port 9093 (Kafka External)**: **IMPORTANT**. Allow from VM 2's Public IP (or 0.0.0.0/0 for testing).
-   **Port 5432 (Postgres)**: (Optional) Only open if you want to run SQL queries from your local machine.

---

## 🚀 Phase 2: Deploying the Central Base (VM 1)

The Central machine hosts the "Brain" of the system: Kafka, Zookeeper, the Database, and the analysis services.

### 1. Transfer Files

On your **local machine**, navigate to your project root and send the required files to VM 1:

```bash
# Upload the central server configuration and your source code
scp -r ./cloud_deployment/central_server/ user@VM1_IP:/home/user/central_server
scp -r ./src/ user@VM1_IP:/home/user/src
scp ./Dockerfile user@VM1_IP:/home/user/
scp ./pom.xml user@VM1_IP:/home/user/
```

### 2. Launch Services

SSH into **VM 1** and run:

```bash
cd ~/central_server

# Verify the Public IP of this machine
export CENTRAL_IP=$(curl -s http://checkip.amazonaws.com)

# Start the services
# We pass the IP so Kafka can 'advertise' its address to the remote stations
CENTRAL_VM_IP=$CENTRAL_IP docker compose up --build -d
```

### 3. Verify

```bash
docker compose ps
# Ensure zookeeper, kafka, postgres-db, and central-station are 'Up'
```

---

## 🚀 Phase 3: Deploying Weather Stations (VM 2)

The second machine handles the 10 data producers.

### 1. Transfer Files

On your **local machine**, send the station files to VM 2:

```bash
scp -r ./cloud_deployment/stations_server/ user@VM2_IP:/home/user/stations_server
scp -r ./src/ user@VM2_IP:/home/user/src
scp ./Dockerfile user@VM2_IP:/home/user/
scp ./pom.xml user@VM2_IP:/home/user/
```

### 2. Launch Stations

SSH into **VM 2** and run:

```bash
cd ~/stations_server

# REPLACE 123.45.67.89 with the Public IP of VM 1 (Central)
CENTRAL_VM_IP=123.45.67.89 docker compose up --build --scale weather-station=10 -d
```

---

## 🧪 Phase 4: Verification

Go back to **VM 1** to see the data arriving from the other machine!

1.  **Check Kafka Traffic**:
    ```bash
    docker exec -it kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic weather_readings --max-messages 10
    ```
2.  **Check Database Persistence**:
    ```bash
    docker exec -it postgres-db psql -U admin -d weather_db -c "SELECT COUNT(*) FROM weather_readings;"
    ```

---

## 🛠 Troubleshooting

### "Port 9092 is already in use"

If you get a `bind: address already in use` error for 9092:

-   Another local Kafka or service is running. Run `sudo lsof -i :9092` to find it, or simply use `docker compose down` inside any other project folders that might be running.

### Kafka Connection Timed Out

If VM 2 cannot connect to VM 1:

1.  Check VM 1's firewall (Port 9093).
2.  Ensure you passed the correct `CENTRAL_VM_IP` when starting VM 1. Run `docker logs kafka` on VM 1 and look for "Advertised Listeners"—it should show VM 1's public IP.

### Why use Port 9093?

In our `docker-compose.yml`, we use:

-   **Port 9092**: For _Internal_ traffic (Central Station talking to Kafka on the same machine).
-   **Port 9093**: For _External_ traffic (Remote Weather Stations).
    This separation is a Kafka best practice for secure and reliable multi-machine communication.
