#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

source_only=false
if [[ "${1:-}" == "--source-only" ]]; then
  source_only=true
elif [[ $# -gt 0 ]]; then
  echo "用法：$0 [--source-only]" >&2
  exit 2
fi

fail() {
  echo "校验失败：$1" >&2
  exit 1
}

if [[ -n "${KEXING_DEVICE_UDID:-}" ]]; then
  udid="$KEXING_DEVICE_UDID"
elif [[ -f DEVICE_UDID.txt ]]; then
  udid="$(tr -d '\r\n' < DEVICE_UDID.txt)"
elif [[ "$source_only" == true ]]; then
  udid="$(tr -d '\r\n' < DEVICE_UDID.txt.example)"
else
  fail "缺少 DEVICE_UDID.txt；真机调试/签名前请通过源码外文件或 KEXING_DEVICE_UDID 提供"
fi
[[ "$udid" =~ ^[0-9A-F]{64}$ ]] || fail "设备 UDID 必须是 64 位大写十六进制"

phone_fingerprint="$(tr -d '\r\n' < PHONE_CERT_SHA256.txt)"
[[ "$phone_fingerprint" =~ ^[0-9a-f]{64}$ ]] || fail "PHONE_CERT_SHA256.txt 必须是 64 位小写十六进制 SHA-256"
if [[ "$phone_fingerprint" == "0000000000000000000000000000000000000000000000000000000000000000" ]]; then
  if [[ "$source_only" == false ]]; then
    fail "尚未填入 Android 正式发布证书 SHA-256；禁止生成可误认为可配对的 HAP"
  fi
  echo "提示：源码校验模式允许正式证书指纹占位；发布构建仍会被阻止。" >&2
fi

grep -Fq 'com.classcue.kexing:' entry/src/main/config.json || fail "supportLists 未使用 Android 正式包名"
grep -Fq "$phone_fingerprint" entry/src/main/config.json || fail "config.json 未包含手机签名指纹"
grep -Fq "$phone_fingerprint" entry/src/main/js/default/constants/peer.js || fail "peer.js 未包含手机签名指纹"

sdk_file="entry/src/main/js/default/wearengine/wearengine.js"
expected_sdk_hash="$(tr -d '\r\n' < "${sdk_file}.sha256")"
actual_sdk_hash="$(sha256sum "$sdk_file" | awk '{print $1}')"
[[ "$actual_sdk_hash" == "$expected_sdk_hash" ]] || fail "wearengine.js SHA-256 不匹配"

for js_file in \
  entry/src/main/js/default/app.js \
  entry/src/main/js/default/constants/peer.js \
  entry/src/main/js/default/wearengine/compat.js \
  entry/src/main/js/default/pages/index/history.js \
  entry/src/main/js/default/pages/index/index.js; do
  node --check "$js_file"
done

if command -v jq >/dev/null 2>&1; then
  jq empty entry/src/main/config.json entry/src/main/resources/base/element/string.json
else
  echo "提示：未安装 jq，跳过标准 JSON 解析检查。" >&2
fi

echo "手表工程源码校验通过"
echo "目标设备 UDID：$udid"
echo "手机正式包名：com.classcue.kexing"
echo "手机 APK 证书 SHA-256：$phone_fingerprint"
