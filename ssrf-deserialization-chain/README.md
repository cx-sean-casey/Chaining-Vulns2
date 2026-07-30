# SSRF to Deserialization Daisy-Chain Demo (`ssrf-deserialization-chain`)

A lightweight Spring Boot demonstration application illustrating how a **Server-Side Request Forgery (SSRF)** vulnerability detected via **Static Application Security Testing (SAST)** can be chained with a deserialization sink in the application to demonstrate the exploit flow in a controlled environment.

This project is specifically designed for application security workshops and technical demonstrations of **Checkmarx One** triage, correlation, and remediation capabilities.

---

## 🛠️ Architecture & Attack Flow

```
                             [ ATTACKER ]
                                  │
                                  │ 1. POST /api/v1/profile/avatar/fetch
                                  │    imageUrl = http://127.0.0.1:8080/internal/import
                                  │    (Includes malicious serialized payload)
                                  ▼
             ┌─────────────────────────────────────────┐
             │         Public API Endpoint             │
             │       ProfileController.java            │
             │   [SAST Finding: Unfiltered SSRF]       │
             └────────────────────┬────────────────────┘
                                  │
                                  │ 2. Server makes internal loopback call
                                  │    POST http://127.0.0.1:8080/internal/import
                                  ▼
             ┌─────────────────────────────────────────┐
             │        Internal API Endpoint            │
             │    InternalImportController.java        │
             │ [Passes 127.0.0.1 Check + Deserializes] │
             └────────────────────┬────────────────────┘
                                  │
                                  │ 3. Executes the deserialization sink
                                  │    with a serialized payload input
                                  ▼
             ┌─────────────────────────────────────────┐
             │       SYSTEM COMMAND EXECUTION          │
             │   [Demonstration Sink / Payload Flow]   │
             └─────────────────────────────────────────┘
```

---

## 🔍 Vulnerability Breakdown

### 1. SAST Finding: Unvalidated Server-Side Request Forgery (SSRF)
* **File:** `src/main/java/com/demo/security/controller/ProfileController.java`
* **Vulnerability:** The endpoint `/api/v1/profile/avatar/fetch` takes a user-supplied `imageUrl` query parameter and opens an HTTP connection directly via `java.net.URL.openConnection()`.
* **Risk:** The application does not validate, sanitize, or filter target hostnames or IP addresses, allowing an attacker to coerce the application server into initiating requests to internal services or local loopback addresses (`127.0.0.1`).

### 2. Dependency State: Removed from This Branch
* **State:** The vulnerable legacy deserialization dependency has been removed from this branch's Maven configuration.
* **Impact:** The app still demonstrates the SSRF and unsafe deserialization flow through the application sink without the removed dependency.

### 3. Application Sink: Internal Unsafe Object Deserialization
* **File:** `src/main/java/com/demo/security/controller/InternalImportController.java`
* **Vulnerability:** The endpoint `/internal/import` reads raw binary data directly into an `ObjectInputStream.readObject()`.
* **Mitigation Control (Bypassed):** The endpoint verifies that `request.getRemoteAddr()` equals `127.0.0.1` (or `0:0:0:0:0:0:0:1`). While this successfully blocks external internet actors, it offers **zero protection against SSRF**, as requests routed through `ProfileController` originate locally from the server itself.

---

## 🚀 Step-by-Step Exploit Scenario

### Prerequisites
* JDK 11 or 17 installed
* Maven installed
* `curl` command-line utility

### 1. Build and Run the Application
```bash
mvn clean package
java -jar target/ssrf-deserialization-chain-1.0.0.jar
```
The server will start on port `8080`.

---

### 2. Direct Exploit Attempt (Fails / Access Denied)
An external attacker attempts to send a malicious payload directly to the internal import endpoint:

```bash
curl -X POST http://localhost:8080/internal/import      -H "Content-Type: application/octet-stream"      --data-binary "@payload.bin"
```

> **Result:** If sent from a non-loopback network interface, the internal controller returns `403 Access Denied: Internal network only.`.

---

### 3. Daisy-Chained Exploit via SSRF (Success)
The attacker uses the public profile avatar endpoint as a pivot to bypass IP controls and trigger deserialization internally:

```bash
curl -X POST "http://localhost:8080/api/v1/profile/avatar/fetch?imageUrl=http://127.0.0.1:8080/internal/import"      -H "Content-Type: application/octet-stream"      --data-binary "@payload.bin"
```

> **Result:**
> 1. `ProfileController` receives the request and issues an HTTP POST to `http://127.0.0.1:8080/internal/import`.
> 2. `InternalImportController` validates that `remoteAddr` is `127.0.0.1` (which passes).
> 3. `ObjectInputStream.readObject()` executes.
> 4. The deserialization sink evaluates the serialized payload and reaches the execution path.

---

## 🎯 Checkmarx One Value & Triage Insights

| Detection Engine | Finding | Standalone Severity | Correlated Severity |
| :--- | :--- | :--- | :--- |
| **SCA** | Removed from this branch | N/A | N/A |
| **SAST** | SSRF Flaw (`ProfileController.java`) | Medium | **CRITICAL** |
| **SAST** | Unsafe Deserialization (`InternalImportController.java`) | Low (Internal Only) | **CRITICAL** |

### Key Demonstration Talking Points

1. **Context-Aware Prioritization:**
   * Standalone SCA scanners would not find the removed dependency in this branch.
   * Standalone SAST scanners might still flag the internal deserialization sink as low risk due to IP checks.
   * **Checkmarx Correlation** can still connect the public SSRF entry point and the internal sink for triage purposes.

2. **Flexible Remediation Options:**
   * **Dependency Remediation (Fastest Path):** Remove the vulnerable legacy dependency from `pom.xml` and keep only the application sink under review.
   * **SAST Remediation (Root Cause Fix):** Implement an IP allowlist/blocklist in `ProfileController.java` to prevent requests to loopback (`127.0.0.1`, `localhost`) and metadata IPs (`169.254.169.254`).

---

## 📁 Project Structure

```
ssrf-deserialization-chain/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── demo/
        │           └── security/
        │               ├── SsrfDemoApplication.java
        │               └── controller/
        │                   ├── ProfileController.java
        │                   └── InternalImportController.java
        └── resources/
            └── application.properties
```