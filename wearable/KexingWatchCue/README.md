# 课醒 Lite Wearable 手表端

当前开发版本：`0.2.6`。目标设备为 HUAWEI WATCH GT 5 Pro，包名为 `com.classcue.kexing.watch`，配对的 Android 正式包名为 `com.classcue.kexing`。

## 已实现

- 接收手机发送的课堂问题、极速答案与强力答案；
- 保存最近 30 组问答，每页一题；
- 表冠上下滚动长答案，左右滑动或底部箭头翻题；
- 按手机消息中的 `vibrate` 字段决定是否短震；
- 断开页面时注销接收器；Wear Engine 兼容层避免旧运行时无参数退订异常。

当前没有手表答题或回传手机功能。

## 正式配对身份

源码中包含当前 Android 正式发布证书的公开 SHA-256 指纹。若使用不同的 Android 签名密钥，必须同步替换：

1. `PHONE_CERT_SHA256.txt`
2. `entry/src/main/js/default/constants/peer.js`
3. `entry/src/main/config.json` 的 `supportLists`

运行以下命令检查源码恢复状态：

```bash
bash tools/verify_project.sh --source-only
```

Windows PowerShell 可使用等价校验：

```powershell
.\tools\verify_project.ps1 -SourceOnly
```

正式构建前必须让默认校验通过：

```bash
bash tools/verify_project.sh
```

设备 UDID 不进入公开仓库；如需真机调试，请通过环境变量或源码外文件提供。

## 构建与签名

`tools/build_hap.sh` 会先执行正式身份校验，再调用 `hvigorw`。需要已安装的华为 Command Line Tools，并通过 `DEVECO_COMMAND_LINE_HOME` 或 `HVIGORW_PATH` 指定工具位置。

签名 HAP 还需要与 `com.classcue.kexing.watch` 匹配的密钥、证书和 Profile。真机调试时，请把设备 UDID 放在源码外的 `DEVICE_UDID.txt`，或通过 `KEXING_DEVICE_UDID` 提供；公开仓库只保留 `DEVICE_UDID.txt.example` 占位值。私钥、密码与 Profile 不包含在源码包中。

官方 Lite Wearable JS SDK 5.0.2.306 位于 `entry/src/main/js/default/wearengine/`，并由 `.sha256` 文件固定完整性。Android 端使用 Wear Engine 5.0.3.304；两端版本号来自现有可运行成品与交接源码的恢复组合，发布时应在真实设备上回归验证。
