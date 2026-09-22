#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"

signing_dir="${KEXING_SIGNING_DIR:?请设置 KEXING_SIGNING_DIR 指向隔离的调试签名目录}"
keystore_file="${KEXING_KEYSTORE_FILE:-$signing_dir/kexing-watch-debug-ec.p12}"
certificate_file="${KEXING_CERTIFICATE_FILE:-$signing_dir/kexing-watch-debug.cer}"
profile_file="${KEXING_PROFILE_FILE:-$signing_dir/kexing-watch-debug.p7b}"
unsigned_hap="${KEXING_UNSIGNED_HAP:-$project_dir/entry/build/default/outputs/default/entry-default-unsigned.hap}"
signed_hap="${KEXING_SIGNED_HAP:-$project_dir/entry/build/default/outputs/default/entry-default-signed.hap}"
key_alias="${KEXING_KEY_ALIAS:-kexing_watch_debug}"

if [[ -n "${HAP_SIGN_TOOL_JAR:-}" ]]; then
  sign_tool="$HAP_SIGN_TOOL_JAR"
elif [[ -n "${DEVECO_COMMAND_LINE_HOME:-}" ]]; then
  sign_tool="$DEVECO_COMMAND_LINE_HOME/sdk/default/openharmony/toolchains/lib/hap-sign-tool.jar"
else
  echo "请设置 DEVECO_COMMAND_LINE_HOME 或 HAP_SIGN_TOOL_JAR。" >&2
  exit 2
fi

for required_file in "$sign_tool" "$keystore_file" "$certificate_file" "$profile_file" "$unsigned_hap"; do
  if [[ ! -f "$required_file" ]]; then
    echo "缺少文件：$required_file" >&2
    exit 2
  fi
done

if [[ -n "${KEXING_KEYSTORE_PASSWORD:-}" ]]; then
  keystore_password="$KEXING_KEYSTORE_PASSWORD"
elif [[ -n "${KEXING_KEYSTORE_PASSWORD_FILE:-}" && -f "$KEXING_KEYSTORE_PASSWORD_FILE" ]]; then
  keystore_password="$(tr -d '\r\n' < "$KEXING_KEYSTORE_PASSWORD_FILE")"
else
  echo "请通过 KEXING_KEYSTORE_PASSWORD 或 KEXING_KEYSTORE_PASSWORD_FILE 提供密码。" >&2
  exit 2
fi

if [[ -z "$keystore_password" ]]; then
  echo "签名密码不能为空。" >&2
  exit 2
fi

mkdir -p "$(dirname "$signed_hap")"
verification_dir="$(mktemp -d)"
trap 'rm -rf "$verification_dir"' EXIT

java -jar "$sign_tool" verify-profile \
  -inFile "$profile_file" \
  -outFile "$verification_dir/profile.json"

java -jar "$sign_tool" sign-app \
  -mode localSign \
  -keyAlias "$key_alias" \
  -keyPwd "$keystore_password" \
  -appCertFile "$certificate_file" \
  -profileFile "$profile_file" \
  -inFile "$unsigned_hap" \
  -signAlg SHA256withECDSA \
  -keystoreFile "$keystore_file" \
  -keystorePwd "$keystore_password" \
  -outFile "$signed_hap" \
  -compatibleVersion 10 \
  -signCode 1

java -jar "$sign_tool" verify-app \
  -inFile "$signed_hap" \
  -outCertChain "$verification_dir/app-cert-chain.cer" \
  -outProfile "$verification_dir/profile.p7b"

if ! cmp -s "$profile_file" "$verification_dir/profile.p7b"; then
  echo "签名包内 Profile 与输入 Profile 不一致。" >&2
  exit 3
fi

if command -v unzip >/dev/null 2>&1; then
  unzip -t "$signed_hap" >/dev/null
fi

echo "已生成并验证：$signed_hap"
sha256sum "$signed_hap"
