# Loro 🦜

**Loro (Parrot)** is a vibrant, accessible, and uniquely tailored family messaging application designed to seamlessly connect kids and adults. Built exclusively for Android using modern development practices, Loro features a dual-interface system ("Kids Mode" and "Adults Mode") that adapts to the cognitive and functional needs of its users.

## ✨ Features

### 🧒 Kids Mode
- **Simplified Visual Interface:** A highly vibrant, grid-based UI designed around a custom "Parrot" Material Design 3 theme.
- **One-Tap Media Recording:** Press and hold interactions for easy Audio and Video messaging, complete with an intuitive "Swipe up to lock" feature.
- **Bedtime Mode Restrictions:** Automatic UI adjustments that lock active communication (recording/sending) during scheduled sleep hours (e.g., 9 PM to 7 AM), protecting children's sleep schedules. 
- **Accessibility First:** Comprehensive `ContentDescriptions` and TalkBack labeling ensure visually impaired young users can confidently navigate and exchange messages.

### 🧑‍💼 Adults Mode
- **Unrestricted Access:** Adult profiles bypass Bedtime Mode to freely read logs, manage kids' profiles, and send messages anytime.
- **Activity Monitoring:** Built-in Firebase integration that logs when a child's device enters or exits Bedtime Mode, stored securely in a `system_logs` collection.

### ⚙️ Core Technical Features
- **Offline-First Resilience:** Integrated Room database ensures messages are accessible without an internet connection, while `WorkManager` intelligently queues media uploads for when connectivity is restored.
- **Hardware Optimized:** Uses Android's `MediaRecorder` for highly compressed audio messaging (AAC/MPEG_4) and `CameraX` for reliable cross-device video capture.
- **QA & Testing Overlay:** A hidden debug overlay allows QA teams to simulate offline modes and bypass time-based Bedtime restrictions for immediate testing.

## 🛠️ Tech Stack & Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose & Material Design 3
- **Architecture:** MVVM (Model-View-ViewModel) / Clean Architecture principles
- **Local Persistence:** Room Database
- **Background Tasks:** WorkManager & Coroutines / StateFlow
- **Media:** CameraX, MediaRecorder
- **Backend / Cloud:** Firebase Firestore (for system logs and configuration)

## 🎨 Design System: The "Loro" Palette

Loro rejects generic app layouts, utilizing a carefully planned, high-contrast palette known as the **Loro Brand Identity**:
- **Parrot Light Blue** (`#0083ff`) & **Parrot Dark Blue** (`#0054ff`): Primary surfaces and app bars.
- **Parrot Yellow** (`#ebff1f`): Energetic accent color for the simplified Kids Mode grid.
- **Parrot Red** (`#ff1900`): Active state for the audio/video record buttons.
- **Parrot Orange** (`#ff7144`): Warning states and the "Swipe Up to Lock" visual indicators.

## 🚀 Getting Started

### Prerequisites
- [Android Studio (Latest stable version)](https://developer.android.com/studio)
- Android SDK 34
- JDK 17+

### Firebase Setup
Loro uses Firebase Firestore for remote logging and potential future message syncing.
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Register your application using the Android package name.
3. Download the `google-services.json` file.
4. Place the `google-services.json` file in the `app/` directory of this project.

### Building and Running
1. Clone the repository and open the project in Android Studio.
2. Allow Gradle to sync the dependencies.
3. Select an emulator or physical device.
4. Click **Run** (`Shift + F10`).

## 🧪 Testing & QA

To facilitate day-time testing of nighttime features, Loro includes a developer/QA overlay in **Kids Mode**:
- **Bypass Bedtime Restriction:** Toggle the bypass switch in the QA menu to ignore the local system clock. This forces the UI into the active daytime state, unlocking media recording functions regardless of the actual hour.

## 🔒 Privacy & Permissions
This app requires the following permissions to function correctly:
- `CAMERA`: For recording video messages.
- `RECORD_AUDIO`: For capturing voice messages.
- `INTERNET`: For syncing messages and logs with Firestore.
- `ACCESS_NETWORK_STATE`: For offline-first upload chunking and WorkManager tasks.

## 📄 License
This project is proprietary and built for family safety and connection. All rights reserved.
