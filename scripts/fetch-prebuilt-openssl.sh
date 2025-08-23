#!/usr/bin/env bash
set -euo pipefail

# Simple helper to fetch prebuilt OpenSSL .so files into app/src/main/jniLibs/<ABI>/
# Usage: ./scripts/fetch-prebuilt-openssl.sh
# You must set PREBUILT_BASE_URL to the base URL that contains the prebuilt artifacts
# expected layout (example): $PREBUILT_BASE_URL/arm64-v8a/libssl.so

PREBUILT_BASE_URL="https://raw.githubusercontent.com/YourOrg/Prebuilt-OpenSSL-Android/main/Prebuilt"
OUT_DIR="app/src/main/jniLibs"
ABIS=("arm64-v8a" "armeabi-v7a")

mkdir -p "$OUT_DIR"
for ABI in "${ABIS[@]}"; do
  mkdir -p "$OUT_DIR/$ABI"
  echo "Downloading OpenSSL for $ABI..."
  SSL_URL="$PREBUILT_BASE_URL/${ABI}-shared/libssl.so"
  CRYPTO_URL="$PREBUILT_BASE_URL/${ABI}-shared/libcrypto.so"

  echo "  -> $SSL_URL"
  echo "  -> $CRYPTO_URL"

  # Use curl or wget depending on availability
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$SSL_URL" -o "$OUT_DIR/$ABI/libssl.so"
    curl -fL "$CRYPTO_URL" -o "$OUT_DIR/$ABI/libcrypto.so"
  else
    wget -O "$OUT_DIR/$ABI/libssl.so" "$SSL_URL"
    wget -O "$OUT_DIR/$ABI/libcrypto.so" "$CRYPTO_URL"
  fi
  echo "Saved to $OUT_DIR/$ABI/"
done

echo "Done. Verify files under $OUT_DIR and then Clean & Rebuild the project."