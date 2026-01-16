📦 Manufacturing Events Processing Backend

Processing backend that ingests time-series manufacturing events, validates and deduplicates them, intelligently updates records, and exposes analytical REST APIs.

Built using Java 21 + Spring Boot 3 + PostgreSQL.

🏗️ 1. Architecture
Client → REST API → Controller → Service Layer → Repository → PostgreSQL

Component Roles
| Component            | Responsibility                                                              |
| -------------------- | --------------------------------------------------------------------------- |
|   Controller         | Accepts requests, converts JSON → domain models, returns API responses      |
|   Service Layer      | Core business logic: validation, dedupe, update decision, stats computation |
|   Repository (JPA)   | DB persistence, bulk save, filtered queries                                 |
|   Database           | Source of truth storing canonical events                                    |
|   Tests              | Verify logic independently from DB                                          |

This separation allows:
a. Independent unit testing
b. Plugging different storage backends in future
c. Horizontal scaling (multiple app instances) without conflicts

🔁 2. Dedupe / Update Logic
The backend receives multiple updates for the same eventId.
To ensure a single canonical representation, the following rules apply:

a. Event identification:
    eventId is the natural primary key
    Identifies the same event across retries / delayed ingestion
b. Logic per event:
    If no record exists → INSERT
    Else compare incoming.receivedTime to existing.receivedTime:
        If incoming timestamp > existing → UPDATE
        Else → IGNORE

Why receivedTime?
eventTime is when the event occurred (can be delayed)
receivedTime reflects data pipeline freshness
Choosing latest received ensures the system holds the most authoritative payload

This avoids:
Duplicate records
Overwriting newer data with stale messages
Double-counting during analytics

🔒 3. Thread-Safety
This solution is thread-safe without in-memory locks due to the following guarantees:
a. Database Constraints:
    Primary key on event_id prevents duplicate inserts
    UPDATE path ensures race conditions resolve correctly
b. Transaction Semantics (@Transactional):
    Insert/update decision and DB write happen atomically
    No half-written records
c. Batched Writes:
    saveAll minimizes transaction count
    Reduces lock duration
d. Horizontal Scalability:
    Multiple instances can process batches simultaneously because:
        The DB is single source of truth
        Latest-wins logic applies consistently
No Java-level locking needed, allowing higher throughput.

🗄️ 4. Data Model
Database Table: events
| Column          | Type       | Description             |
| --------------- | ---------- | ----------------------- |
| `event_id`      | VARCHAR PK | Unique identifier       |
| `machine_id`    | VARCHAR    | Machine source          |
| `factory_id`    | VARCHAR    | Factory reference       |
| `line_id`       | VARCHAR    | Line reference          |
| `event_time`    | TIMESTAMP  | When issue occurred     |
| `received_time` | TIMESTAMP  | When system received it |
| `duration_ms`   | BIGINT     | Event duration          |
| `defect_count`  | INT        | Defects (−1 = unknown)  |
| `created_at`    | TIMESTAMP  | Insert time             |
| `updated_at`    | TIMESTAMP  | Last update time        |

Notes:
Minimal schema to keep ingestion fast
No joins needed — analytics derived with queries + Java aggregation

⚡ 5. Performance Strategy
Goal: Process 1000 events within 1 second.
Key choices enabling throughput:
a. Batch-Oriented Design:
    API accepts an array of events in one call
    Eliminates HTTP overhead from single-event writes
b. Efficient Persistence:
    Uses Spring Data saveAll → groups DB writes
    Lets PostgreSQL optimize commits internally
c. Lightweight Validation:
    Simple numeric + timestamp checks
    No heavy parsing or API dependencies
d. Index Optimization:
    Primary key lookups make dedupe checks O(log N)
    No full table scan required
e. Connection Reuse:
    HikariCP connection pool avoids repeated DB handshakes
Combined, these achieve near-constant-time ingestion even with large batches.

🧪 6. Edge Cases & Assumptions
Accepted Assumptions
eventId uniquely maps to a real-world event
Field constraints are minimal to maximize throughput

Edge Case Behaviors:
| Case                                     | Decision                           |
| ---------------------------------------- | ---------------------------------- |
| `defectCount == -1`                      | Stored, but ignored in analytics   |
| Negative duration / missing fields       | Rejected with error                |
| Future timestamps > 15 mins ahead        | Rejected (pipeline anomaly)        |
| Duplicate events with older receivedTime | Dropped (data is stale)            |
| Event spanning multiple factories/lines  | Supported — keyed by event ID only |

Tradeoffs:
    Dropping old messages may lose history — chosen to preserve correctness
    Stats computed in service, not DB, avoids expensive SQL

▶️ 7. Setup & Run Instructions
Prerequisites
Java 21
Maven
PostgreSQL running locally

Create DB:
Command:CREATE DATABASE manufacturing_events;

Configure DB:
src/main/resources/application.properties
set username / password 

Run Application:
Command:mvn spring-boot:run
Server starts on http://localhost:8090

Run Tests:
Command:mvn test

Sample load test JSON → sample-data/batch-1000.json  
Run benchmark using:
curl -w "\nTotal time: %{time_total}s\n" ^
  -X POST "http://localhost:8090/events/batch" ^
  -H "Content-Type: application/json" ^
  -d "@batch-1000.json"


🔮 8. Future Improvements
With more time, I would explore:
a. Scalability
    Kafka ingestion (real streaming vs batch HTTP)
    Using PostgreSQL COPY or bulk insert optimizations
    Sharding by factory/line
b. Analytics Enhancements
    Caching hot ranges (e.g., last 1h per machine)
    Precomputed time window rollups
c. Observability
    Grafana dashboards + metrics
    Structured error reporting

🧑‍💻 Author
Sandipak Dhuri