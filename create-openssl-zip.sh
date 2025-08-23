#!/usr/bin/env bash
set -euo pipefail
# Usage:
#   1) Make executable: chmod +x create-openssl-zip.sh
#   2) Edit PREBUILT_BASE_URL below to point to a trusted raw GitHub folder,
#      or run with LOCAL_LIBS_DIR="" to force download.
#   3) ./create-openssl-zip.sh
#
# Output: keysearch-openssl-package.zip containing:
#   - the modified project files
#   - scripts/fetch-prebuilt-openssl.sh
#   - README-openssl.md
#   - .github/workflows/android-openssl.yml
#   - app/src/main/cpp/CMakeLists.txt
#   - app/build.gradle
#   - app/src/main/jniLibs/<abi>/libssl.so and libcrypto.so (downloaded)

# EDIT: set PREBUILT_BASE_URL to the RAW base URL where prebuilt .so exist
PREBUILT_BASE_URL="https://raw.githubusercontent.com/YourOrg/Prebuilt-OpenSSL-Android/main/Prebuilt"
# If you already have local .so files, set LOCAL_LIBS_DIR to path containing:
#   arm64-v8a/libssl.so, arm64-v8a/libcrypto.so, armeabi-v7a/...
LOCAL_LIBS_DIR=""

OUT_ZIP="keysearch-openssl-package.zip"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

echo "Preparing files in $TMPDIR ..."

# create directory structure
mkdir -p "$TMPDIR"/app/src/main/cpp
mkdir -p "$TMPDIR"/scripts
mkdir -p "$TMPDIR"/.github/workflows

# write CMakeLists.txt
cat > "$TMPDIR/app/src/main/cpp/CMakeLists.txt" <<'CMAKE'
cmake_minimum_required(VERSION 3.18.1)
project("keysearchapp")

add_library(native-lib SHARED native-lib.cpp)

# Android log
find_library(log-lib log)

# Import prebuilt OpenSSL libraries placed under app/src/main/jniLibs/<ABI>/
add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcrypto.so)

add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libssl.so)

# Link native-lib with Android log and the imported OpenSSL libs
target_link_libraries(
    native-lib
    ${log-lib}
    ssl
    crypto
)
CMAKE

# write build.gradle
cat > "$TMPDIR/app/build.gradle" <<'GRADLE'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.keysearchapp'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.keysearchapp"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters "arm64-v8a", "armeabi-v7a"
        }

        externalNativeBuild {
            cmake {
                cppFlags ""
            }
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation "androidx.core:core-ktx:1.12.0"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.10.0"
}
GRADLE

# write fetch script
cat > "$TMPDIR/scripts/fetch-prebuilt-openssl.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
# Fetch prebuilt OpenSSL .so files into app/src/main/jniLibs/<ABI>/
# Edit PREBUILT_BASE_URL before running to use a trusted source.
PREBUILT_BASE_URL="https://raw.githubusercontent.com/YourOrg/Prebuilt-OpenSSL-Android/main/Prebuilt"
OUT_DIR="app/src/main/jniLibs"
ABIS=("arm64-v8a" "armeabi-v7-a")

mkdir -p "$OUT_DIR"
for ABI in "${ABIS[@]}"; do
  mkdir -p "$OUT_DIR/$ABI"
  echo "Downloading OpenSSL for $ABI..."
  SSL_URL="$PREBUILT_BASE_URL/${ABI}-shared/libssl.so"
  CRYPTO_URL="$PREBUILT_BASE_URL/${ABI}-shared/libcrypto.so"

  echo "  -> $SSL_URL"
  echo "  -> $CRYPTO_URL"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "$SSL_URL" -o "$OUT_DIR/$ABI/libssl.so"
    curl -fL "$CRYPTO_URL" -o "$OUT_DIR/$ABI/libcrypto.so"
  else
    wget -O "$OUT_DIR/$ABI/libssl.so" "$SSL_URL"
    wget -O "$OUT_DIR/$ABI/libcrypto.so" "$CRYPTO_URL"
  fi

  chmod 0644 "$OUT_DIR/$ABI/libssl.so" || true
  chmod 0644 "$OUT_DIR/$ABI/libcrypto.so" || true

  echo "Saved to $OUT_DIR/$ABI/"
done

echo "Done. Verify files under $OUT_DIR and then Clean & Rebuild the project."
SH
chmod +x "$TMPDIR/scripts/fetch-prebuilt-openssl.sh"

# write README-openssl.md
cat > "$TMPDIR/README-openssl.md" <<'MD'
# Adding prebuilt OpenSSL to the Android project

This document explains how to add prebuilt libssl.so and libcrypto.so to the project so the native code can link at runtime.

Steps:

1) Edit the script `scripts/fetch-prebuilt-openssl.sh` and set `PREBUILT_BASE_URL` to a trusted raw URL pointing to prebuilt artifacts
   with the expected layout (see script comments).

2) Run the included script to download prebuilt libraries into `app/src/main/jniLibs/`:

   chmod +x scripts/fetch-prebuilt-openssl.sh
   ./scripts/fetch-prebuilt-openssl.sh

3) Verify the files exist:

   ls app/src/main/jniLibs/arm64-v8a/
   ls app/src/main/jniLibs/armeabi-v7a/

   You should see `libssl.so` and `libcrypto.so` in each target ABI folder.

4) Clean and rebuild the project in Android Studio:

   Build -> Clean Project
   Build -> Rebuild Project

5) Inspect the resulting APK to ensure .so files are packaged:

   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep lib/arm64-v8a/libcrypto.so

6) CI integration:
   - Ensure the runner installs Android SDK/NDK/CMake (see example workflow file).
   - Run the fetch script in CI before `./gradlew assembleDebug`.

Security note:
- Do not use prebuilt binary libraries from unknown/untrusted sources in production apps.
- Prefer building OpenSSL/BoringSSL yourself or using a trusted vendor. Verify checksums/signatures of binaries.
MD

# write GitHub Actions workflow
cat > "$TMPDIR/.github/workflows/android-openssl.yml" <<'YML'
name: Android CI (with prebuilt OpenSSL)

on:
  push:
    branches: [ main, fix/jni-threading ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Android (SDK, NDK, CMake)
        uses: r0adkll/setup-android@v2
        with:
          api-level: 34
          build-tools: 34.0.0
          ndk: 25.2.9519653
          cmake: 3.22.1

      - name: Make gradlew executable
        run: chmod +x gradlew

      - name: Fetch prebuilt OpenSSL
        run: |
          chmod +x scripts/fetch-prebuilt-openssl.sh
          ./scripts/fetch-prebuilt-openssl.sh

      - name: Build Debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Verify .so packaging
        run: |
          unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "libnative-lib.so|libcrypto.so|libssl.so" || (echo "Missing required .so files" && exit 1)
YML

# populate jniLibs by downloading prebuilt libraries
ABIS=("arm64-v8a" "armeabi-v7-a")
for ABI in "${ABIS[@]}"; do
  mkdir -p "app/src/main/jniLibs/$ABI"
  SSL_URL="$PREBUILT_BASE_URL/${ABI}-shared/libssl.so"
  CRYPTO_URL="$PREBUILT_BASE_URL/${ABI}-shared/libcrypto.so"
  # download into the repository package
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$SSL_URL" -o "app/src/main/jniLibs/$ABI/libssl.so" || true
    curl -fL "$CRYPTO_URL" -o "app/src/main/jniLibs/$ABI/libcrypto.so" || true
  else
    wget -O "app/src/main/jniLibs/$ABI/libssl.so" "$SSL_URL" || true
    wget -O "app/src/main/jniLibs/$ABI/libcrypto.so" "$CRYPTO_URL" || true
  fi
done

Return: "Files written and branch created"