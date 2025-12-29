# ☁️ Multi-VM Cloud Deployment Guide in 10 Minutes

This guide explains how to deploy the system across **two separate virtual machines (VMs)** as required.

## 📋 The Architecture

1.  **VM 1 (Central Base)**: Runs Kafka, Zookeeper, Database, Central Station, Rain Detector.
2.  **VM 2 (Stations)**: Runs ONLY the Weather Stations (10 instances).

---

## 🚀 Step 1: Prepare VM 1 (Central Base)

1.  **Get value**: Find the **Public IP Address** of this VM (e.g., `123.45.67.89`). Let's call this `CENTRAL_IP`.
2.  **Copy files**: Upload the `cloud_deployment/central_server/` folder AND the source code to this VM.
3.  **Deploy**:
    Run the following command. **Crucial**: You must set the `CENTRAL_VM_IP` variable so Kafka knows its own address.

    ```bash
    # Go to the folder
    cd cloud_deployment/central_server

    # Run with the IP environment variable
    CENTRAL_VM_IP=123.45.67.89 docker-compose up --build -d
    ```

4.  **Verify**: Run `docker ps`. You should see 5 containers running (kafka, zookeeper, postgres, central-station, rain-detector).

---

## 🚀 Step 2: Prepare VM 2 (Weather Stations)

1.  **Copy files**: Upload the `cloud_deployment/stations_server/` folder and source code to this VM.
2.  **Deploy**:
    Run the following command, pointing it to VM 1's IP.

    ```bash
    # Go to the folder
    cd cloud_deployment/stations_server

    # Run, scaling to 10 stations
    # REPLACE 123.45.67.89 WITH THE IP OF VM 1
    CENTRAL_VM_IP=123.45.67.89 docker-compose up --build --scale weather-station=10 -d
    ```

3.  **Verify**: Run `docker ps`. You should see 10 weather-station containers.

---

## 🔍 How to Verify It's Working

Since the database is on VM 1, go to **VM 1** and run:

```bash
# Check logs of central station to see incoming data
docker logs central-station

# Check database count
docker exec -it postgres-db psql -U admin -d weather_db -c "SELECT COUNT(*) FROM weather_readings;"
```

If the count is increasing, **VM 2 is successfully sending data to VM 1 over the internet!** 🎉

## ⚠️ Important Firewall / Security Group Settings

For this to work, **VM 1 MUST allow incoming traffic** on port **9093** from VM 2.

-   **AWS Security Group**: Add Inbound Rule -> Custom TCP -> Port 9093 -> Source: 0.0.0.0/0 (or VM 2 IP).
-   **DigitalOcean/UFW**: `ufw allow 9093/tcp`
