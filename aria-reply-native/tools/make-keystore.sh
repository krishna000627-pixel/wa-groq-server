#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
OUT="${HOME}/aria-keystore/aria-release.jks"
mkdir -p "$(dirname "$OUT")"
if [ -f "$OUT" ]; then echo "Existing keystore: $OUT"; exit 0; fi
read -r -s -p "Keystore password: " PASS; echo
read -r -s -p "Key password (blank = same): " KEY_PASS; echo
KEY_PASS="${KEY_PASS:-$PASS}"
keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias aria-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS \
  -storepass "$PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=Aria Reply, OU=Mobile, O=Aria, L=India, ST=India, C=IN"
chmod 600 "$OUT"
echo
printf '%s\n' "ARIA_KEYSTORE_FILE=$OUT" "ARIA_KEYSTORE_PASSWORD=<your password>" "ARIA_KEY_ALIAS=aria-release" "ARIA_KEY_PASSWORD=<your key password>"
printf '%s\n' "Base64 for GitHub secret:" 
base64 -w 0 "$OUT"; echo
