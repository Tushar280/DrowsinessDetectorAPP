<div align="center">
  <h1>🚗 Drowsiness Detector</h1>
  <p>A real-time Android application leveraging machine learning to prevent driver fatigue and ensure road safety.</p>

  [![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![ML Kit](https://img.shields.io/badge/ML-Google%20ML%20Kit-4285F4?style=flat&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
  [![TFLite](https://img.shields.io/badge/Model-TensorFlow%20Lite-FF6F00?style=flat&logo=tensorflow&logoColor=white)](https://www.tensorflow.org/lite)
</div>

## 📌 Overview

**Drowsiness Detector** is a mobile application designed to monitor driver alertness in real-time. By utilizing the device's front camera, it employs **Google ML Kit** for facial landmark detection and a **TensorFlow Lite (TFLite)** model to classify eye states and head tilts. When signs of drowsiness are detected, the app triggers timely alerts to prevent potential accidents.

## 📥 Download App

You can directly install the application on your Android device by downloading the APK file below:

<!-- Download link directs users to the latest GitHub Release -->
**[⬇️ Download Drowsiness Detector APK](https://github.com/Tushar280/DrowsinessDetectorAPP/releases/latest)**

---

## 🛠️ Hardware Requirements

To run this application effectively, you will need:
- An **Android Smartphone** (Android 7.0 / API 24 or higher recommended).
- A functional **front-facing camera**.
- A **phone mount** for the car dashboard (for real-world usage).
- A **Computer (Windows/macOS/Linux)** (if you intend to build the project from source).

---

## 💻 How to Run Locally

If you wish to view the code, modify it, or build the application from source on your own computer, follow these steps:

### Prerequisites
- [Android Studio](https://developer.android.com/studio) installed on your system.
- An Android device with **USB Debugging** enabled, or an Android Emulator configured in Android Studio.

### Setup Instructions

1. **Clone the repository**
   Open your terminal or command prompt and run:
   ```bash
   git clone https://github.com/Tushar280/DrowsinessDetectorAPP.git
   ```
2. **Open the project**
   - Launch **Android Studio**.
   - Click on **File > Open** and select the `DrowsinessDetectorAPP` folder you just cloned.
3. **Sync Gradle**
   - Wait for Android Studio to automatically sync the Gradle files and download necessary dependencies.
4. **Run the application**
   - Connect your Android device via USB or start an emulator.
   - Click the green **Run (▶)** button in the top toolbar to build and install the app on your device.

---

## 🚀 Key Features

* **Real-time Face Tracking:** Continuous monitoring of facial features using ML Kit.
* **Eye State Classification:** Accurate detection of open/closed eyes via a custom TFLite model.
* **Alert System:** Immediate auditory and visual warnings upon detecting fatigue.
* **Background Monitoring:** Continues to track and alert even when navigating other maps or apps.

<div align="center">
  <br>
  <sub>Built with ❤️ for road safety.</sub>
</div>
