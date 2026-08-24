<div align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_new.png" width="112" alt="ClassCue 图标">
  <h1>课醒 ClassCue</h1>
  <p>面向 Android 的课堂实时转写、点名提醒与 AI 回答助手</p>

  <p>
    <img alt="Version 0.2.2p" src="https://img.shields.io/badge/version-0.2.2p-6750A4">
    <img alt="Android 13–16" src="https://img.shields.io/badge/Android-13--16-3DDC84?logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Java_17-7F52FF?logo=kotlin&logoColor=white">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-blue"></a>
  </p>
  <p>
    <strong>简体中文</strong> · <a href="README_EN.md">English</a>
  </p>
</div>

课醒会在你主动开始一节课后，把同一份麦克风音频同时用于本地 WAV 录音和离线语音识别。它持续分析课堂转写，在疑似点名、明确提问或自定义强提醒词出现时，通过高优先级通知提醒；你也可以把相关文字交给自己配置的 AI 模型生成参考答案。

> [!IMPORTANT]
> 当前仓库是 `0.2.2p` 公开版，`p` 表示 public。它不包含华为手表、Wear Engine、手表端工程、平台申请资料或私人签名材料。带华为手表连接能力的 `0.2.1` 是独立的私人版本，不在本仓库发布。

## 为什么做 ClassCue

普通录音软件只留下音频，通用转写软件也通常不会理解“老师正在点谁、问题从哪里开始”。ClassCue 把课堂场景拆成一条本地优先的处理链：

1. 录下原始课堂音频，同时做离线实时转写。
2. 结合点名、提问表达和前后文窗口判断是否需要提醒。
3. 通知先展示老师原话，让用户自行确认题目。
4. 用户启用 AI 后，再把文字上下文并行发送给其自行配置的模型服务。
5. 录音、转写、提醒和答案都保留在本机历史中，便于回看。

## 功能概览

| 能力 | 当前实现 |
|---|---|
| 离线转写 | 单路 `AudioRecord` 采集；内置 Vosk 小模型，可选下载两档 sherpa-onnx Zipformer 模型 |
| 课堂提醒 | 识别姓名、学号、称呼、点名表达、明确提问、全班问题和自定义强提醒词 |
| 上下文捕获 | 支持“先问后点人”和“先点人后问”，前后窗口可在 30–120 秒间调整 |
| 通知 | 手机高优先级横幅、可调震动强度、Android 实时状态提升请求和 MagicOS 兼容检查 |
| AI 回答 | 多个兼容 OpenAI Chat Completions 的 HTTPS 服务并行请求，区分极速/强力角色与独立超时 |
| 手动重答 | 可按中文字或英文词块圈选老师原话，支持不连续选择、滑动批选和指定模型重发 |
| 本地记录 | 保存 WAV、课堂转写、提醒、AI 回答及录音偏移；录音默认只保留最近 3 天 |
| 隐私保护 | API Key 由 Android Keystore 使用 AES-GCM 保护；系统备份排除密钥配置和录音 |

ClassCue 不使用语音播报，也不会把原始录音上传给 AI 服务。

## 界面与使用流程

1. 首次启动后允许麦克风和通知权限。
2. 在“设置”中填写可能被叫到的姓名、学号、称呼和强提醒词。
3. 选择小、中或大档离线识别模型；中、大档需要用户手动下载。
4. 如需 AI 回答，添加至少一个 HTTPS BaseURL、模型名和 API Key，并选择“极速”或“强力”角色。
5. 回到主界面开始听课。通知会先显示老师原话，AI 结果到达后更新同一条提醒。

“我可能被叫到的称呼”可在同一输入框中填写多项，并使用中文逗号、英文逗号、分号或换行分隔，例如：`小明，李同学，班长，课代表`。

## 离线模型

| 档位 | 用途 | 获取方式 | 下载体积 |
|---|---|---|---:|
| 小档 | 低存储占用、快速启动 | APK 内置 Vosk 中文小模型 | 已随应用提供 |
| 中档 | 更稳的流式课堂转写 | 应用内手动下载 Zipformer INT8 | 约 127 MiB |
| 大档 | 流式结果后的第二遍精修 | 应用内手动下载 Zipformer CTC INT8 | 约 287 MiB |

模型下载由独立前台数据同步服务处理，支持进度、速度、暂停、断点续传、失败重试和删除。大档依赖中档流式结果，因此选择大档时会先补齐中档模型。

## 快速开始

### 环境要求

| 组件 | 要求 |
|---|---|
| 操作系统 | Android 13–16 |
| CPU 架构 | `arm64-v8a` |
| JDK | 17 |
| Android SDK | 36 |
| Android Build Tools | 36.0.0 |
| Android Studio | 2025.2.2 或兼容版本 |

### 获取并构建

```bash
git clone https://github.com/lu0973-cb02d/ClassCue.git
cd ClassCue
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell 或命令提示符使用：

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

中国大陆网络环境如无法稳定访问 Google Maven，可临时设置兼容镜像基址后重试：

```powershell
$env:KEXING_MAVEN_PROXY = "https://maven.aliyun.com/repository"
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

当前版本为 `0.2.2p (10)`。Debug 应用 ID 是 `com.classcue.kexing.debug`，可与正式包名构建分开安装。仓库暂不提供由项目所有者签名的正式 APK。

## AI 服务配置

建议保留一个低延迟模型和一个更可靠的强模型：

| 角色 | 建议超时 | 行为 |
|---|---:|---|
| 极速 | 5–15 秒 | 第一份成功答案立即更新通知 |
| 强力 | 25–45 秒 | 返回后再次更新同一通知，并优先展示 |

BaseURL 必须使用 HTTPS。客户端会把以下常见写法归一到 Chat Completions 地址：

```text
https://example.com      → https://example.com/v1/chat/completions
https://example.com/v1   → https://example.com/v1/chat/completions
```

如果地址已经以 `/chat/completions` 结尾，则保持不变。每个模型独立保存 BaseURL、模型名、角色、API Key、启用状态和超时；单个服务失败不会阻塞其他模型。

## 隐私与数据边界

| 数据 | 默认去向 |
|---|---|
| 原始麦克风音频 | 本机 WAV 文件，不发送给 AI 服务 |
| 离线转写 | 本机识别并写入本地历史 |
| 提醒与答案 | 本机 SQLite 历史 |
| AI API Key | Android Keystore 保护的本机加密存储 |
| AI 请求 | 仅向用户配置的 HTTPS 服务发送文字上下文 |
| 中/大档模型 | 从 `k2-fsa/sherpa-onnx` 官方 GitHub Release 资源下载 |

请在录音前确认已取得必要同意，并遵守学校规定和所在地法律。不要在考试或明确禁止电子设备的场景使用本项目。公开提交 Issue 时，请勿附带真实录音、课堂转写、API Key 或其他个人信息；安全问题请参阅 [SECURITY.md](SECURITY.md)。

## 项目结构

```text
ClassCue/
├─ app/src/main/java/com/classcue/kexing/
│  ├─ data/       # 本地历史、录音仓库、设置与密钥存储
│  ├─ engine/     # 录音、ASR、提醒分类、模型下载与 AI 请求
│  ├─ model/      # 应用设置和业务数据模型
│  └─ ui/         # Jetpack Compose 界面
├─ app/src/main/assets/
│  └─ vosk-model-small-cn-0.22/  # 内置离线小模型
├─ app/src/test/  # JVM 单元测试
├─ design/        # 图标设计资源
└─ LICENSES/      # 第三方许可证副本
```

## 构建状态

当前公开快照已验证：

- 19 项 JVM 单元测试通过，0 失败。
- Debug APK 完整编译和封装通过。
- Android Lint 为 0 个错误、21 个警告。
- 源码和合并 Manifest 不含华为 APP ID、Wear Engine SDK、运动健康包查询或手表端声明。

完整边界和已知警告见 [BUILD_STATUS.md](BUILD_STATUS.md)。发布者如需自己的签名 APK，请阅读 [FORMAL_SIGNING_REQUIRED.md](FORMAL_SIGNING_REQUIRED.md)。

## 已知限制

- 教室距离、回声、多人同时说话、口音和生僻姓名都会影响识别效果；项目不承诺 100% 准确。
- 前台服务和唤醒锁可以提高后台稳定性，但不能绕过用户强制停止、麦克风隐私开关或厂商极端省电策略。
- MagicOS 是否把 Android 实时通知显示为灵动胶囊由系统决定；项目不调用未公开的厂商私有接口。
- 中、大档模型包目前通过 HTTPS 下载，但尚未在客户端内固定 SHA-256 校验值。仅应使用仓库预设的官方上游地址。
- 当前只提供中文界面和 `arm64-v8a` 构建目标。

## 贡献

欢迎通过 Issue 报告可复现的问题或提出功能建议，也欢迎提交 Pull Request。提交前请至少运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

请保持公开版不依赖华为 Wear Engine 或私人签名资料；不要提交密钥、证书、真实课堂录音、个人转写或模型服务凭据。

## 许可证与第三方组件

ClassCue 自有源代码和文档以 [MIT License](LICENSE) 开源。

Vosk、sherpa-onnx、AndroidX、Jetpack Compose、JNA、Apache Commons 以及预训练模型等第三方内容继续适用各自的许可证和模型说明，不因本项目采用 MIT 而改变。详情见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)。
