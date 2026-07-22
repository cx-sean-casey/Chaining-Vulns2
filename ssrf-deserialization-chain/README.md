# SSRF to Deserialization Daisy-Chain Demo (`ssrf-deserialization-chain`)

A lightweight Spring Boot demonstration application illustrating how a **Server-Side Request Forgery (SSRF)** vulnerability detected via **Static Application Security Testing (SAST)** can be chained with a **Known Gadget Dependency (CVE-2015-7501)** detected via **Software Composition Analysis (SCA)** to achieve **Remote Code Execution (RCE)**.

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
                                  │ 3. Uses Commons Collections 3.2.1
                                  │    Gadget Chain (InvokerTransformer)
                                  ▼
             ┌─────────────────────────────────────────┐
             │       SYSTEM COMMAND EXECUTION          │
             │   [SCA Finding: CVE-2015-7501 / RCE]    │
             └─────────────────────────────────────────┘
```

---

## 🔍 Vulnerability Breakdown

### 1. SAST Finding: Unvalidated Server-Side Request Forgery (SSRF)
* **File:** `src/main/java/com/demo/security/controller/ProfileController.java`
* **Vulnerability:** The endpoint `/api/v1/profile/avatar/fetch` takes a user-supplied `imageUrl` query parameter and opens an HTTP connection directly via `java.net.URL.openConnection()`.
* **Risk:** The application does not validate, sanitize, or filter target hostnames or IP addresses, allowing an attacker to coerce the application server into initiating requests to internal services or local loopback addresses (`127.0.0.1`).

### 2. SCA Finding: Known Deserialization Gadget Chain (CVE-2015-7501)
* **Dependency:** `commons-collections:commons-collections:3.2.1`
* **Vulnerability:** Apache Commons Collections versions `3.2.1` and earlier contain transformer classes (`InvokerTransformer`, `LazyMap`) that can be chained together during Java object deserialization to invoke arbitrary methods and execute operating system commands.

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
> 4. `commons-collections:3.2.1` evaluates the serialized gadget chain and executes arbitrary OS commands.

---

## 🎯 Checkmarx One Value & Triage Insights

| Detection Engine | Finding | Standalone Severity | Correlated Severity |
| :--- | :--- | :--- | :--- |
| **SCA** | `commons-collections:3.2.1` (CVE-2015-7501) | Medium / High | **CRITICAL** |
| **SAST** | SSRF Flaw (`ProfileController.java`) | Medium | **CRITICAL** |
| **SAST** | Unsafe Deserialization (`InternalImportController.java`) | Low (Internal Only) | **CRITICAL** |

### Key Demonstration Talking Points

1. **Context-Aware Prioritization:**
   * Standalone SCA scanners often flag `commons-collections:3.2.1` without knowing if a deserialization sink (`readObject()`) exists in the codebase.
   * Standalone SAST scanners might mark internal deserialization sinks as low risk due to IP checks.
   * **Checkmarx Correlation** connects the public SSRF entry point, the internal sink, and the vulnerable dependency, proving **exploitability** and elevating triage priority.

2. **Flexible Remediation Options:**
   * **SCA Remediation (Fastest Path):** Upgrade `commons-collections` to version `3.2.2` or `4.4` in `pom.xml`. This disables unsafe transformer deserialization by default, neutralizing the RCE even if the SSRF persists.
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