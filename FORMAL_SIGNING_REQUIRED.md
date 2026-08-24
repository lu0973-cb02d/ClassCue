# Android 发布签名说明

本工程的应用 ID 为 `com.classcue.kexing`。公开仓库不包含项目所有者的正式证书、私钥、密码或任何密码恢复文件。

## 使用自己的发布证书

复制 `keystore.properties.example` 为 `keystore.properties`，填写你自己的证书路径、别名和密码。该文件以及常见证书格式已被 `.gitignore` 排除，请勿提交。

也可以在本地或 CI 中注入：

- `CLASSCUE_RELEASE_STORE_FILE`
- `CLASSCUE_RELEASE_STORE_PASSWORD`
- `CLASSCUE_RELEASE_KEY_ALIAS`
- `CLASSCUE_RELEASE_KEY_PASSWORD`

配置完成后运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

未配置发布证书时，`assembleRelease` 只能生成未签名 APK，不能直接安装或发布。任何人用自己的证书构建出的 APK，都不能覆盖安装由另一证书签名的同包名版本。

请把证书和密码存放在仓库之外，并对 CI 密钥使用最小权限和受保护变量。
