<div align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_new.png" width="112" alt="课醒图标">
  <h1>课醒 ClassCue</h1>
  <p><strong>按课表记好每一节课，被点名时及时叫醒你。</strong></p>
  <p>本地优先的 Android 课堂助手：课表管理 · 离线录音转写 · 点名与提问提醒 · 课后回放搜索 · 可选 AI 参考答案</p>

  <p>
    <a href="https://github.com/lu0973-cb02d/ClassCue/releases/latest"><img alt="最新版本" src="https://img.shields.io/github/v/release/lu0973-cb02d/ClassCue?label=%E4%B8%8B%E8%BD%BD&color=6750A4"></a>
    <img alt="Android 13–16" src="https://img.shields.io/badge/Android-13--16-3DDC84?logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-blue"></a>
  </p>
  <p>
    <strong>简体中文</strong> · <a href="README_EN.md">English</a>
  </p>
</div>

课醒把一节课从开始到结束串起来：上课前按课表确认是哪门课；上课时在手机上离线录音、实时转写，听到老师点你的名字或提出问题时立刻提醒；下课后按课程回放录音、搜索原话、导出笔记。录音和转写只保存在你的手机上；只有你配置并开启 AI 后，相关的那段文字才会发给你自己选择的模型服务。

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/screenshots/timetable.png" alt="课表页" width="200"><br><sub>课表与本周课程</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/confirm.png" alt="确认本节课程" width="200"><br><sub>开始前确认课程</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/listen.png" alt="听课页" width="200"><br><sub>听课与实时转写</sub></td>
    <td align="center" width="25%"><img src="docs/screenshots/lesson.png" alt="课次详情" width="200"><br><sub>课后回放与提醒</sub></td>
  </tr>
</table>

## 一节课里，课醒做什么

### 课前

- **课表**：按星期、上下课时间、起止周和单双周录入课程，可填写地点；设好学期第一周后自动算出本周是第几周。
- **上课提醒（可选）**：上课前若干分钟推送通知，点开后直接确认课程。提醒只会提示，**从不自动开始录音**。

### 课中

- **确认课程**：点“开始听课”时，按课表默认选中正在上或即将开始的课，也可以改选或手动输入。录音、转写和提醒都归到这门课下。
- **离线录音与转写**：同一路麦克风音频同时写入本地 WAV 和离线语音识别器。提供小、中、大三档模型，支持远场增强、外置麦克风和省电识别。
- **点名与提问提醒**：综合学号、姓名、称呼、点名说法和提问说法打分判断；名字被识别成同音或近音字（平翘舌、前后鼻音不分）也能认出。“先提问后点名”和“先点名后提问”都能接上前后文（前后各 30–120 秒可调）。命中后立即发出高优先级通知，先显示老师原话，再附上 AI 参考答案。灵敏度可在“少误报”和“少漏报”之间调整。
- **AI 参考答案（可选）**：把提醒前后的文字并行发给你配置的一个或多个模型，答案边生成边更新到同一条通知里。默认只为点到你的问题自动请求，并限制每 10 分钟的次数；也可以手动圈选老师原话后重新发送。
- **华为手表（需自行签名）**：`wearable/KexingWatchCue` 提供手表端源码，可把提醒和答案转发到手表。

### 课后

- **课次详情**：按时间线查看转写、提醒和 AI 回答；自带播放器，点任意一句就从录音中对应位置附近开始播放。
- **全文搜索**：跨课程搜索老师原话、提醒和答案。
- **导出**：把单节课、一门课或全部课程导出为 Markdown。
- **省空间与防丢失**：课后可在后台把录音压缩为 AAC（每小时约 14 MB，WAV 约 110 MB），默认保留原件；应用被系统关闭或手机重启时，未写完的录音会自动修复，不会丢。

## 下载安装

从 [Releases](https://github.com/lu0973-cb02d/ClassCue/releases/latest) 下载 `ClassCue-<版本>-arm64-v8a.apk`，并可用同目录的 `SHA256SUMS.txt` 核对文件。

| 项目 | 要求 / 说明 |
|---|---|
| 系统 | Android 13–16，`arm64-v8a` |
| 安装包名 | `com.classcue.kexing.github`（从 0.2.8 起） |
| 首次启动 | 需要允许麦克风和通知权限 |
| 从 0.2.6 及更早版本升级 | 旧版包名是 `com.classcue.kexing.debug`，新版会作为另一个应用安装；请先在旧版“记录”页导出需要保留的内容，确认新版正常后再卸载旧版 |

每次更新的内容见 [`release-notes/`](release-notes)。

## 上手

1. **课表**页：设置学期第一周的周一，录入课程。也可以跳过，直接在听课页填写课程名。
2. **设置**页：填写可能被叫到的姓名、学号、称呼（如 `小明，李同学，班长`，可用中英文逗号、分号或换行分隔）和需要强提醒的关键词。
3. **设置**页：选择识别模型。小档随应用内置；中、大档在应用内下载。
4. 如需 AI 参考答案：添加至少一个 HTTPS BaseURL、模型名和 API Key，选择“极速”或“强力”角色。
5. 回到**听课**页，点“开始听课”，确认课程后开始。可以先点“测试提醒”确认通知和震动正常。
6. 下课后在**记录**页回放、搜索或导出。

使用外置麦克风（USB、蓝牙或有线）时，把输入策略改为“外置”。默认开启的“教室远场增强”只作用于送进识别器的声音，不改变保存的原始录音。

## 离线识别模型

| 档位 | 适合 | 获取方式 | 下载体积 |
|---|---|---|---:|
| 小档 | 存储紧张、快速启动；中/大档不可用时自动兜底 | 应用内置 Vosk 中文小模型 | 无需下载 |
| 中档 | 更稳的流式课堂转写 | 应用内下载 sherpa-onnx Zipformer INT8 | 约 127 MiB |
| 大档 | 在流式结果后再精修一遍 | 应用内下载 sherpa-onnx Zipformer CTC INT8（依赖中档） | 约 288 MiB |

- 下载在前台服务中进行，支持进度、暂停、断点续传、重试和删除。
- 下载源可选 GitHub 官方 Release 或国内镜像 `hf-mirror.com`；首选来源失败时自动改用另一个。
- 每个模型文件的精确大小和 SHA-256 都固定在客户端中（`OfflineModelCatalog.kt`），两个来源都用同一组校验值，不一致的文件会被删除，不会安装。

## AI 服务配置

建议同时配置一个低延迟模型和一个更可靠的强模型：

| 角色 | 建议超时 | 行为 |
|---|---:|---|
| 极速 | 5–15 秒 | 第一份答案立即更新通知 |
| 强力 | 25–45 秒 | 返回后更新同一条通知，并优先展示 |

服务需兼容 OpenAI Chat Completions，BaseURL 必须是 HTTPS。`https://example.com` 和 `https://example.com/v1` 会自动补全为 `/v1/chat/completions`；已经以 `/chat/completions` 结尾的地址保持不变。每个模型单独保存地址、模型名、角色、Key、超时和启用状态，一个服务失败不影响其他服务。

## 隐私与数据

| 数据 | 存放与去向 |
|---|---|
| 原始录音 | 手机本地 WAV（可选在本机压缩为 AAC），保留到你手动删除；从不发给 AI 服务 |
| 远场增强后的声音 | 只在内存中送给识别器，不另存、不上传 |
| 转写、提醒、AI 答案 | 手机本地 SQLite，按课程和课次保存，保留到你手动删除 |
| 课表 | 只存在手机上；上课提醒由系统闹钟在本机触发 |
| AI API Key | Android Keystore 保护的本地加密存储 |
| 发给 AI 的内容 | 只有提醒前后的文字，只发给你配置的 HTTPS 服务 |
| 系统备份 | 录音、密钥配置和课堂历史数据库都被排除在云备份与设备迁移之外 |
| 中/大档模型 | 从 `k2-fsa/sherpa-onnx` 官方 Release 或 `hf-mirror.com` 下载，安装前校验 SHA-256 |

课醒不使用语音播报，也不上传原始录音。录音前请确认已取得必要同意，并遵守学校规定和当地法律；不要在考试或禁止使用电子设备的场合使用。公开提交 Issue 时，请勿附带真实录音、转写、API Key 或其他个人信息；安全问题请见 [SECURITY.md](SECURITY.md)。

## 已知限制

- 教室距离、回声、多人同时说话、口音和生僻姓名都会影响识别。软件增益无法找回麦克风没录到的声音；距离远时请把手机前移或使用靠近老师的外置麦克风。项目不承诺 100% 准确。
- 课表需要手动录入，暂不支持从教务系统导入。
- 课后压缩和上课提醒尚未经过长时间真机验证；提醒使用非精确闹钟，系统可能前后挪动几分钟以省电。
- 前台服务和唤醒锁能提高后台稳定性，但不能绕过强制停止、麦克风隐私开关或厂商的极端省电策略。MagicOS 是否把实时通知显示为灵动胶囊由系统决定。
- 目前只有中文界面和 `arm64-v8a` 版本。
- 手表端只与正式签名的 `com.classcue.kexing` 配对；构建手表 HAP 需要华为 DevEco/Hvigor、开发者自己的签名证书/Profile 和真机，这些材料不在公开仓库中。

## 从源码构建

| 组件 | 要求 |
|---|---|
| JDK | 17 |
| Android SDK / Build Tools | 36 / 36.0.0 |
| Android Studio | 2025.2.2 或兼容版本 |

```bash
git clone https://github.com/lu0973-cb02d/ClassCue.git
cd ClassCue
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 使用 `gradlew.bat`。中国大陆网络如无法稳定访问 Google Maven，可先设置 `$env:KEXING_MAVEN_PROXY = "https://maven.aliyun.com/repository"`。

| 构建类型 | 命令 | 应用 ID | 说明 |
|---|---|---|---|
| `debug` | `assembleDebug` | `com.classcue.kexing.debug` | 本地开发，可调试 |
| `github` | `assembleGithub` | `com.classcue.kexing.github` | 与 Releases 相同的优化公开版 |
| `release` | `assembleRelease` | `com.classcue.kexing` | 需要你自己的签名证书，见 [FORMAL_SIGNING_REQUIRED.md](FORMAL_SIGNING_REQUIRED.md) |

三种构建包名不同，可以同时安装。APK 输出在 `app/build/outputs/apk/<构建类型>/`。推送到 `main` 后，GitHub Actions 会运行测试和 Lint、构建 `github` 版，并在存在对应 `release-notes/v<版本>.md` 时自动发布 Release；Pull Request 会运行同样的测试、Lint 和 `github` 版构建，但不使用签名密钥，也不发布。当前测试与 Lint 结果见 [BUILD_STATUS.md](BUILD_STATUS.md)。

## 项目结构

```text
ClassCue/
├─ app/src/main/java/com/classcue/kexing/
│  ├─ data/       # 课堂历史数据库、录音文件、课表、设置与密钥存储
│  ├─ engine/     # 录音、离线识别、提醒判断、上课提醒、模型下载、AI 请求
│  ├─ model/      # 设置、课表和课堂数据模型
│  └─ ui/         # Jetpack Compose 界面：听课、课表、记录、设置
├─ app/src/main/assets/vosk-model-small-cn-0.22/  # 内置小档模型
├─ app/src/test/                # 单元测试与截图测试
├─ wearable/KexingWatchCue/     # 华为 Lite Wearable 手表端源码
├─ release-notes/               # 各版本更新说明（CI 据此发布）
├─ docs/screenshots/            # README 截图
└─ LICENSES/                    # 第三方许可证副本
```

## 贡献

欢迎通过 Issue 报告可复现的问题或提出建议，也欢迎 Pull Request。提交前请至少运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

请勿提交密钥、证书、真实课堂录音、个人转写或模型服务凭据。

## 许可证

课醒自有的源代码和文档以 [MIT License](LICENSE) 开源。Vosk、sherpa-onnx、AndroidX、Jetpack Compose、JNA、Apache Commons 及预训练模型等第三方内容适用各自的许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [LICENSES/](LICENSES)。
