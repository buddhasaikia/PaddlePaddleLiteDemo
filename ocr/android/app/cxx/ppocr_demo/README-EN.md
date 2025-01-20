# OOCR Text Recognition Demo User Guide

This guide demonstrates how to implement real-time OCR text recognition on an Android app. The demo is user-friendly and highly extensible, allowing users to integrate their own trained models. It provides details on running the OCR demo and maintaining functionality when updating models or input/output processing.

---

## How to Run the OCR Text Recognition Demo

### Prerequisites

1. Install Android Studio on your computer. Refer to the [Android Studio official site](https://developer.android.com/studio) for installation details.
2. Prepare an Android device and enable USB Debugging:  
   **Settings -> Developer Options -> Enable Developer Options and USB Debugging.**

**Note:** If Android Studio's NDK is not configured, follow the [NDK and CMake installation guide](https://developer.android.com/studio/projects/install-ndk). Use the latest NDK version or match the NDK version used in the Paddle Lite prediction library.

---

### Deployment Steps

1. Locate the demo at `Paddle-Lite-Demo/ocr/Android/app/c++/ppocr_demo`.
2. Navigate to the `Paddle-Lite-Demo/libs` directory and run the `download.sh` script to download the Paddle Lite prediction library:
   ```shell
   cd Paddle-Lite-Demo/libs
   sh download.sh
   ```
3. Navigate to `Paddle-Lite-Demo/ocr/assets` and run `download.sh` to download the optimized model, test images, and label files:
   ```shell
   cd ../ocr/assets
   sh download.sh
   ```
4. Go to `Paddle-Lite-Demo/ocr/Android/app/c++/ppocr_demo` and run `prepare.sh` to copy the model and resource files to the project:
   ```shell
   cd ../android/app/c++/ppocr_demo
   sh prepare.sh
   ```
5. Open the `ppocr_demo` project in Android Studio.
6. Connect your phone to your computer via USB, enable USB debugging, and allow installations from USB. Ensure Android Studio detects your device.
7. Click the **Run** button to compile and install the app on your phone. Internet access is required for downloading Paddle Lite libraries and models.

**Expected Result:**  
The app installs and displays as follows:

| App Icon        | App Demo               |
|------------------|------------------------|
| ![App Icon](https://paddlelite-demo.bj.bcebos.com/demo/ocr/docs_img/android/ppocr_app_pic.jpg) | ![App Demo](https://paddlelite-demo.bj.bcebos.com/demo/ocr/docs_img/android/ppocr_app_run.jpg) |

---

### Notes on NDK Configuration Errors

If you encounter errors related to NDK configuration during project import, compilation, or runtime:

- Update the `Android NDK location` in `File > Project Structure > SDK Location`.
- If using NDK from Android Studio's SDK Tools, select the default path.
- Alternatively, edit the `local.properties` file and set the correct NDK path.

If the issue persists, refer to [Android Gradle Plugin Updates](https://developer.android.com/studio/releases/gradle-plugin#updating-plugin).

---

## How to Update the Prediction Library

Refer to the [Paddle Lite Project](https://github.com/PaddlePaddle/Paddle-Lite) and its [source compilation documentation](https://www.paddlepaddle.org.cn/lite/develop/source_compile/compile_env.html) to build the Android prediction library.

**Replace the C++ Libraries:**
- Update header files: Replace the `include` folder in `ppocr_demo/app/PaddleLite/cxx/include`.
- Update the `armeabi-v7a` and `arm64-v8a` dynamic libraries with the corresponding compiled files.

**Note:** If the library version is updated, ensure you also update the optimized model.

---

## Demo Code Overview

The OCR demo structure includes Java and C++ components:

### Java Side
- **Common Package**: Located in `app/src/java/com/baidu/paddle/lite/demo/common`, handles common utilities like model copying and data type conversion.
- **`ppocr_demo` Package**: Located in `app/src/java/com/baidu/paddle/lite/demo/ppocr_demo`, it manages app events and Java-C++ interactions.

### C++ Side
- **Native.cc**: Bridges data between Java and C++.
- **Pipeline.cc**: Manages pre-processing, prediction, and post-processing pipelines.
- **Other Files**:
    - `cls_process.cc`: Handles direction classification.
    - `rec_process.cc`: Handles text recognition.
    - `det_process.cc`: Handles text detection.
    - `det_post_process.cc`: Post-processing for detection models.

---

### Example: Updating the Model

To update the detection model:
1. Place the optimized model in the `./assets` directory.
2. Update the model path in `MainActivity.java` if the model name differs:
   ```java
   Utils.copyAssets(this, "new_model_path");
   String detRealModelDir = new File(
       this.getExternalFilesDir(null),
       "new_model_path").getAbsolutePath();
   ```

---

### Updating Input/Output Preprocessing

- Modify `Preprocess` functions in `cls_process.cc`, `det_process.cc`, or `rec_process.cc` for input changes.
- Modify `Postprocess` functions for output changes.

---

This guide ensures a smooth setup, update, and maintenance process for the OCR text recognition demo. For further assistance, refer to the official Paddle Lite and PaddleOCR documentation.