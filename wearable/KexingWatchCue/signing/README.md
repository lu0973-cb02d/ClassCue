# HAP 签名材料（不进入源码）

源码包不包含私钥、密码、证书或 Profile。可安装到目标 HUAWEI WATCH GT 5 Pro 的 Debug HAP 至少需要：

- 与 `com.classcue.kexing.watch` 对应的 `.p12` 密钥和 `.cer` 证书；
- 包含 `DEVICE_UDID.txt` 中目标设备 UDID 的 `.p7b` Debug Profile；
- 与密钥匹配的别名和密码。

签名材料应保存在本目录的 `private/` 或源码目录之外。项目 `.gitignore` 已排除常见密钥和 Profile 扩展名。

隔离好材料后可使用官方签名工具：

```bash
export DEVECO_COMMAND_LINE_HOME=/path/to/command-line-tools
export KEXING_SIGNING_DIR=/secure/path/to/signing-files
export KEXING_KEYSTORE_PASSWORD_FILE=/secure/path/to/password.txt
bash tools/sign_hap.sh
```

脚本中的默认文件名和别名可用对应环境变量覆盖。签名完成后，应提取 HAP 应用证书 SHA-256，并配置到 Android 设置页或 `AppSettings.DEFAULT_WATCH_FINGERPRINT`。
