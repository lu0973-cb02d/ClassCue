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

## 为 GitHub Releases 配置固定测试签名

GitHub Actions 每次运行都会在全新的虚拟机里构建。如果没有配置固定密钥，Android Gradle Plugin 会临时生成一个新的调试证书，导致每个 Release 的 APK 证书都不同：用户无法覆盖升级，只能卸载旧版，而卸载会清除本机的课堂录音和转写。

工作流支持从仓库 Secrets 读取一把固定的测试签名密钥，用于 `assembleDebug` 产出的公开测试 APK。建议单独生成，不要复用正式发布证书：

1. 在自己的电脑上生成密钥（`keytool` 随 JDK 提供，会交互式询问密码，不要把密码写在命令行里）：

   ```powershell
   keytool -genkeypair -v -keystore classcue-apk-ci.jks -storetype PKCS12 -alias classcue-ci -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ClassCue CI"
   ```

2. 用 GitHub CLI 写入四个 Actions Secrets（PKCS12 的密钥密码与存储密码相同）。`gh secret set` 会从标准输入或交互提示读取值，不会回显：

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("classcue-apk-ci.jks")) | gh secret set CLASSCUE_APK_KEYSTORE_BASE64 --repo <owner>/ClassCue
   gh secret set CLASSCUE_APK_STORE_PASSWORD --repo <owner>/ClassCue
   gh secret set CLASSCUE_APK_KEY_PASSWORD --repo <owner>/ClassCue
   gh secret set CLASSCUE_APK_KEY_ALIAS --repo <owner>/ClassCue --body classcue-ci
   ```

3. 把 `classcue-apk-ci.jks` 和密码离线备份在仓库之外。密钥丢失后，用户需要再卸载一次才能安装新证书的版本。

配置后，Release 会附带 `SIGNING_CERT_SHA256.txt`，并在更新说明末尾写明证书指纹；未配置时工作流会给出警告，并在更新说明里提醒用户升级前先导出数据。从临时证书版本切换到固定证书版本仍需卸载一次，之后的版本即可直接覆盖升级。

本地构建也可通过 `CLASSCUE_APK_STORE_FILE`、`CLASSCUE_APK_STORE_PASSWORD`、`CLASSCUE_APK_KEY_ALIAS`、`CLASSCUE_APK_KEY_PASSWORD` 四个环境变量使用同一把密钥签名 Debug APK；未设置时仍使用本机默认调试证书。
