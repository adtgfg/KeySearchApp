# Adding prebuilt OpenSSL to the Android project

This document explains how to add prebuilt libssl.so and libcrypto.so to the project so the native code can link at runtime.

Steps:

1) Run the included script to download prebuilt libraries into app/src/main/jniLibs/

   ./scripts/fetch-prebuilt-openssl.sh

   By default the script points to a placeholder PREBUILT_BASE_URL. Replace that variable in the script with the correct GitHub raw URL for the Prebuilt-OpenSSL-Android repo you trust.

2) Verify the files exist:

   ls app/src/main/jniLibs/arm64-v8a/
   ls app/src/main/jniLibs/armeabi-v7a/

   You should see libssl.so and libcrypto.so in each target ABI folder.

3) Clean and rebuild the project in Android Studio:

   Build -> Clean Project
   Build -> Rebuild Project

4) Inspect the resulting APK to ensure .so files are packaged:

   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep lib/arm64-v8a/libcrypto.so

5) If the CI needs to build or the APK is built on GitHub Actions, ensure the runner installs the Android SDK components (cmake and ndk) and the workflow includes the fetch script step before building.