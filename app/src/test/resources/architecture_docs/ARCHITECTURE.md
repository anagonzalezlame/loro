# Software Architecture and Product Documentation
**Project Name:** Loro  
**Project Group:** Rhea  
**Classification:** Academic Capstone / Enterprise QA Deliverable

---

## 1. Executive Summary & Product Vision

### 1.1 Core Value Proposition
**Loro** is a secure, dual-mode family communication application designed for children's safety and parental control. Operating through two distinct user state landscapes—**Kids Mode** and **Adults Mode**—the platform establishes a reliable, isolated environment for asynchronous voice and video messaging. Children interact with a simplified, playful, high-contrast user interface, communicating only with parent-approved contacts. Adults oversee operations via a PIN-secured management console to manage contacts, audit message flows, and adjust structural behavioral rules.

### 1.2 Resource Optimization Strategy (Firebase Spark Plan)
To ensure long-term cost sustainability and bypass cloud maintenance overheads, Loro operates entirely within the constraints of the **Firebase Spark Plan (Free Tier)**. The resource limits and their corresponding mitigation strategies are documented below:

| Resource Path | Spark Plan Limit | Loro Mitigation Strategy |
| :--- | :--- | :--- |
| **Cloud Storage** | 10 GB Storage / 20k Writes daily | Aggressive local downsampling of media recordings (AAC audio caps under 100KB, video downscaled to 480p). Active background garbage collection deletes cloud media resources once they are confirmed as older than 7 days, replacing them with a local storage retention profile if pinned by parents. |
| **Firestore** | 50k Reads / 20k Writes daily | Client-side **Room Database** acts as the primary source of truth. Read calls utilize offline persistence caches and read operations are bound strictly to modified time deltas inside state flows instead of pulling full document indexes. |
| **Cloud Storage Bandwidth** | 1 GB download / day | Media streaming bypasses repetitive downloads via Room caching. Once a media file is fetched from Firebase Storage, it is saved directly to the local application cache directory. |

---

## 2. System Architecture & Tech Stack

```
                     +---------------------------------------+
                     |            Loro Jetpack UI            |
                     |  (KidsModeScreen / AdultsModeScreen)  |
                     +-------------------+-------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |               ViewModels              |
                     |    (KidsMode VM / AdultsMode VM)     |
                     +-------------------+-------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |           Repository Group            |
                     | (KidsRepository / AdultsRepository)  |
                     +-------+-----------------------+-------+
                             |                       |
                             v                       v
              +----------------------------+   +-----------+
              |       WorkManager          |   | Air-Gapped|
              | (Offline Upload & Syncer)   |   |   Local   |
              +--------------+-------------+   |   Room    |
                             |                 |    DB     |
                             v                 +-----------+
              +----------------------------+
              |      Remote Firebase       |
              |   Cloud Storage & Firestore|
              +----------------------------+
```

### 2.1 Kotlin, Jetpack Compose, & MVVM Structure
The Loro architecture is modeled after Google's modern Android development guidelines, combining clean architecture principles with a strict **MVVM (Model-View-ViewModel)** structural separation:
* **UI Layer (Jetpack Compose):** Fully declarative, reactive layout built without traditional XML bindings. High rendering optimization is achieved via unidirectional state flow (UDF) streams, and screen recompositions are trimmed through explicit state wrapping keys.
* **State Management:** Employs Kotlin `StateFlow` and `SharedFlow` objects. UI states transition safely between parameterized sealed interfaces (`Loading`, `Success`, `Error`, `Idle`).
* **Dependency Injection:** Utilizes constructor-based injection for ViewModels and Repositories. Clean boundaries allow mock repositories to be swapped in seamlessly during local testing cycles.

### 2.2 Offline Queueing & Persistence Engine (WorkManager + Room)
Unstable network conditions are resolved by routing all message transmissions through an transactional offline buffer:
1. **Room Storage Persistence:** Outgoing messages are written directly to the local Room database (`MessageEntity`) with a pending state flag `UPLOADING_OFFLINE`.
2. **Android WorkManager:** Enqueues a non-blocking background job (`UploadWorker`) configured with network constraints.
3. **Automatic Cache Cleanup:** Upon a successful synchronization operation, the temporary raw file on local disk is securely removed to prevent memory bloat and privacy leaks.

---

## 3. Security & Permission Model

### 3.1 Role Segregation and Firestore Security Rules
Access control boundaries are strictly enforced both client-side and at the server level. Firestore collections utilize structural schemas separated into `children`, `contacts`, and `messages` paths.

The following security configuration illustrates operational role isolation:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Core check helper verifying active authenticator
    function isAuthenticated() {
      return request.auth != null;
    }
    
    // Checks if the active consumer is registered as a verified adult role
    function isAdultUser(userId) {
      return isAuthenticated() && 
             get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == "adult";
    }

    // Children collection permissions
    match /children/{childId} {
      allow read: if isAuthenticated();
      allow write: if isAdultUser(request.auth.uid);
    }

    // Messages collection permissions
    match /messages/{messageId} {
      allow create: if isAuthenticated();
      allow read: if isAuthenticated() && 
                  (request.auth.uid == resource.data.senderId || 
                   request.auth.uid == resource.data.recipientId);
      allow update, delete: if isAdultUser(request.auth.uid);
    }
  }
}
```

### 3.2 Parental Control & PIN Access Architecture
Transitions from Kids Mode to Adults Mode require successful verification against a parent-defined 4-to-6-digit PIN. To mitigate guessing vectors, the authentication handler implements local security constraints:
* **Incremental Attack Protection:** Failed PIN entry attempts are counted in an atomic counter.
* **Lockout Policy:** On the 3rd consecutive failed entry, the input interface enters a lockout state for 60 seconds (`lockoutEndTime` is persisted). All validation requests are rejected early during this window.

### 3.3 Bedtime & Downtime Rule Engine
The application enforces a local safety mechanism called "Bedtime Mode." When active:
* The kids dashboard switches to a calm bedtime UI state, disabling message recording and contact selections to discourage late-night usage.
* Bedtime boundaries default to **21:00 (9:00 PM) to 7:00 AM**.
* System clock validation checks are performed locally on application launch and during background resume sequences. Bedtime status can also be controlled remotely via custom parental switches.

---

## 4. Quality Assurance & Testing Strategy

### 4.1 Manual & Exploratory Testing Workflows
While automated testing ensures business logic correctness, manual exploratory testing is heavily emphasized for Loro's novel interaction model (e.g., "swipe to lock" audio/video recordings):
* **Exploratory Sessions:** Testers systematically explore the UI/UX flows, intentionally interrupting recordings by locking the screen, switching network signals from cellular to offline, and checking the system volume and camera views under varying lighting.
* **UI/UX Walkthroughs:** Emphasize the fluid transition between the child's bright, tactile contact cards and the adults room's high-contrast control dials, focusing on visual hierarchy, readability, and immediate feedback for child actions.

### 4.2 Behavior-Driven Development (BDD) Acceptance Criteria
User stories are documented and validated against clean BDD Gherkin specifications:

* **Scenario: Child tries to communicate during bedtime hours**
  * **Given** the current device runtime hour is `22:00`
  * **When** Kids Mode is initialized
  * **Then** the screen state transitions to Bedtime, message actions are locked, and a soothing ambient sleeping graphic is displayed.

* **Scenario: Parent accesses Adults Mode with correct security verification**
  * **Given** the app is in Kids Mode
  * **When** the parent enters the correct configured PIN
  * **Then** the app immediately transitions to the Adults Management Dashboard.

### 4.3 Hidden QA Debug Bypass & Error Reporting
* **QA Debug Bedtime Bypass:** For testing efficiency, a hidden toggle allows developers & testers to bypass bedtime constraints instantly, reverting the app into an active interactive state without having to modify system clocks.
* **Observability (Firebase Crashlytics):** Captures non-fatal exceptions in all production try-catch blocks (e.g., CameraX camera capture issues, database indexing conflicts, or background sync failures). This ensures telemetry streams are fed to developers in real-time.

---

## 5. Future Roadmap & Scalability

The architecture is explicitly pre-configured to adapt easily to future expansion vectors without violating Spark Plan constraints:

```
                            +-----------------------------+
                            |     Future Loro Core        |
                            +--------------+--------------+
                                           |
                    +----------------------+----------------------+
                    |                                             |
                    v                                             v
     +--------------+--------------+               +--------------+--------------+
     | Real-time WebRTC Call Engine|               | Federated AI safety filters |
     |  (Direct P2P, Zero Storage) |               |      (Local device Gemini)  |
     +-----------------------------+               +-----------------------------+
```

1. **Direct WebRTC P2P Calling:** To support future real-time conversation desires, a peer-to-peer signaling mechanism can be layered over Firestore. Since calling traffic routes directly between two active devices, download/write limits of the Spark Plan are completely unaffected.
2. **Local Machine-Learning Safety Screeners (Gemini Nano / Local TensorFlow):** Automated safety checks on child video messages can run entirely on-device utilizing modern Android NNAPIs. This shields children from inappropriate media ingestion without incurring server-side processing costs.
