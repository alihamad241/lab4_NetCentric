# 🌦️ Weather Station: System Architecture & Technical Deep Dive

This document serves as the "Master Guide" for the platform. It explains how a distributed IoT system works at scale, why specific technical choices were made, and how to manage the environment.

---

## 🗺️ 1. The Big Picture (Executive Summary)

Our system is a **distributed data pipeline**. It follows a **Producer-Consumer** pattern with an intermediate **Message Broker** (Kafka) in the middle to handle high volume and reliability.

### The 10,000-Foot Data Flow:

1.  **Generation**: 10 `Weather Station` pods generate JSON data every second.
2.  **Buffering**: Data is pushed into **Kafka**, which holds it securely.
3.  **Persistence**: The `Central Station` pulls data from Kafka in batches and saves it to **PostgreSQL**.
4.  **Real-time Logic**: The `Rain Detector` pulls the same data to look for high-humidity alerts instantly.

---

## 🧱 2. Core Infrastructure Components

### � Kafka (The Distributed Log)

We use Apache Kafka because it doesn't just "pass messages"—it is a **distributed commit log**.

-   **Persistent Storage**: Even if no one is listening, Kafka stores messages on disk.
-   **Scalability**: Kafka can handle millions of messages per second by splitting "Topics" into "Partitions" across multiple machines.
-   **Offsets**: Consumers track their own "Offset" (pointer). This means if the Central Station crashes, it knows exactly which message was the last one processed and resumes from there.

### 🟡 Zookeeper (The Coordinator)

Kafka cannot function without Zookeeper. It acts as the "Manager":

-   **Heartbeats**: It keeps track of which Kafka brokers are alive.
-   **Configuration**: It stores metadata about topics and partitions.
-   **Leader Election**: If we had multiple Kafka nodes, Zookeeper would decide which one is the "leader" for each piece of data.

### 🔵 The Bitnami Factor (Why Bitnami?)

You’ll notice we use `bitnamilegacy/kafka` and `zookeeper`. **Bitnami** is a industry-standard provider of "packaged" applications.

-   **Security**: Bitnami images are hardened and frequently scanned for vulnerabilities.
-   **Optimization**: They are pre-configured with sane defaults for production.
-   **Consistency**: They follow a standard directory structure and configuration format, making it easier for DevOps engineers to manage.

---

## 🗄️ 3. Data Persistence & Initialization

### PostgreSQL & The "Init" Pattern

We use PostgreSQL 15 for long-term storage. To avoid manual setup, we use the **Postgres Init Pattern**:

-   **Automatic Setup**: Any `.sql` file placed in `/docker-entrypoint-initdb.d/` is executed automatically when the container starts for the first time.
-   **ConfigMaps**: In Kubernetes, we store the `init.sql` inside a ConfigMap and mount it into that folder.

---

## � 4. Kubernetes (The Orchestrator)

### What is Kubernetes doing here?

If Kafka is the "Nervous System," Kubernetes is the **"Brain"**:

-   **Self-Healing**: If you delete a station pod, Kubernetes sees the "Desired State" is 10 and immediately brings the "Actual State" back to 10 by starting a new one.
-   **Service Discovery**: Use `kafka-service:9092` instead of IP addresses. Kubernetes handles the internal routing automatically.

### Essential `kubectl` Command Kit:

-   `kubectl get po`: Check the health of all 15 pods.
-   `kubectl logs -f [POD]`: Follow the "live" heartbeat of any service.
-   `kubectl rollout restart deployment [NAME]`: Force a clean restart of any component.

---

## ☁️ 5. Deployment Options

| Feature        | Docker Compose        | Kubernetes (Minikube) | Cloud Multi-VM    |
| :------------- | :-------------------- | :-------------------- | :---------------- |
| **Complexity** | Very Low              | Medium/High           | Medium            |
| **Use Case**   | Local Dev / Small VPS | Large Clusters        | Distributed Teams |
| **Scaling**    | Manual (`--scale`)    | Automatic (HPA)       | Manual/Scripts    |
| **Cost**       | Minimal               | High (RAM/CPU)        | Moderate          |

---

## ❓ 6. Theoretical Foundations (Academic FAQ)

This section covers the core concepts of distributed systems that are central to this project.

### Q1: Synchronous vs. Asynchronous Communication

**What is the theoretical benefit of utilizing a Message Broker (Kafka) over direct API calls (e.g., REST/HTTP)?**
**A:** Direct API calls are **synchronous**, meaning the producer must wait for the consumer to respond. This creates a "tight coupling" where any delay or failure in the consumer impacts the producer. Kafka enables **asynchronous communication**, decoupling the components. This increases **temporal availability**: the Weather Stations can continue generating data even if the Database or Central Station is temporarily offline.

### Q2: Horizontal vs. Vertical Scalability

**How does this architecture facilitate horizontal scaling?**
**A:** This system is designed for **horizontal scaling** (adding more machines) rather than vertical scaling (adding CPU/RAM to one machine).

-   **Weather Stations**: We can scale from 10 to 1,000 instances simply by adding more containers (stateless).
-   **Kafka**: By using **Partitions**, Kafka can distribute the message load across multiple brokers in a cluster.
-   **Consumers**: We can use **Consumer Groups** to have multiple instances of the Central Station reading from different partitions of the same topic in parallel.

### Q3: Data Consistency Model

**Does this system guarantee Stong Consistency or Eventual Consistency?**
**A:** This system follows an **Eventual Consistency** model. When a Weather Station pushes data to Kafka, it is not immediately reflected in the PostgreSQL database. There is a "lag" while the data sits in the Kafka log and then waits for the Central Station to perform its batch insert. Eventually, the database state will catch up and be consistent with the data produced.

### Q4: Distributed Fault Tolerance

**How does the system achieve high availability and fault tolerance?**
**A:** Fault tolerance is handled at two levels:

1.  **Orchestration (Kubernetes)**: K8s provides **process-level fault tolerance** by automatically restarting crashed pods.
2.  **Data Persistence (Kafka)**: Kafka provides **data-level fault tolerance**. Messages are persisted to disk and (in a multi-node cluster) replicated across different brokers. If a consumer fails, no data is lost because the log retains the history.

### Q5: Message Delivery Semantics

**What are the three types of delivery semantics, and which one is implemented here?**
**A:**

1.  **At-most-once**: Messages may be lost but are never redelivered.
2.  **At-least-once**: Messages are never lost but may be redelivered (duplicates).
3.  **Exactly-once**: Messages are delivered exactly once (hardest to achieve).
    Our system currently leans toward **At-least-once**. Because the Central Station only commits its Kafka offset _after_ a successful batch insert into the database, if it crashes mid-batch, it will restart and re-consume those messages from Kafka, ensuring no data is lost (though duplicates might occur if not handled by the DB).

### Q6: Stateless vs. Stateful Services

**Which components are stateless, and why is this distinction important?**
**A:**

-   **Stateless**: Weather Stations, Central Station, and Rain Detector. They don't store local data that needs to survive a restart. This makes them easy to scale and replace.
-   **Stateful**: Kafka, Zookeeper, and PostgreSQL. They store the "state" of the system on disk. Scaling these is more complex because they require persistent storage (PVCs) and stable network identities.

### Q7: Metadata Simulation (The "Battery Status" Role)

**What does the "Battery Status" theoretically represent in this IoT architecture?**
**A:**
In a real-world distributed system, "Battery Status" serves three critical roles:

1.  **Observability & Monitoring**: It simulates **Device Health Metadata**. In production IoT, you need to know when a sensor is about to die so you can dispatch maintenance before the data stream stops.
2.  **Data Integrity Verification**: In this lab, it is used to prove **Statistical Consistency** through the pipeline. By ensuring the producer (Station) sends a specific distribution (30% Low, 40% Medium, 30% High) and the consumer (DB) reflects that same distribution, we verify that the data wasn't corrupted or unfairly sampled during transport.
3.  **Adaptive Logic**: Theoretically, it enables **Power-Aware Computing**. A real sensor might drop its transmission frequency when status is "Low" to extend its life. In our code, it's a fixed simulation, but it represents the type of "Context-Aware" data used to drive system decisions.

---

```

```
