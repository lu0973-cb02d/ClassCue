<div align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_new.png" width="112" alt="ClassCue icon">
  <h1>ClassCue</h1>
  <p>Real-time classroom transcription, name-call alerts, and optional AI answers for Android</p>

  <p>
    <img alt="Version 0.2.6" src="https://img.shields.io/badge/version-0.2.6-6750A4">
    <img alt="Android 13–16" src="https://img.shields.io/badge/Android-13--16-3DDC84?logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Java_17-7F52FF?logo=kotlin&logoColor=white">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-blue"></a>
  </p>
  <p>
    <a href="README.md">简体中文</a> · <strong>English</strong>
  </p>
</div>

ClassCue records a class only after the user starts a session. The same microphone stream is written to a local WAV file and processed by an on-device speech recognizer. ClassCue watches the transcript for signs that the teacher may be calling on the user or asking a question, then raises a high-priority notification. If enabled, it can also send the relevant text to AI endpoints configured by the user and display the returned answers.

> [!IMPORTANT]
> The current repository version is `0.2.6`. This release tightens far-field recognition, pause preservation, end-of-session draining, and false-positive control. It also restores the optional Huawei watch source project and the phone-side Wear Engine channel. A formally signed watch HAP still requires the developer's own signing materials and real-device regression testing.

## Why ClassCue exists

A voice recorder preserves audio, and a general-purpose transcription app produces text, but neither understands when a teacher may be addressing a particular student or where the relevant question begins. ClassCue turns that classroom-specific problem into a local-first pipeline:

1. Preserve the original class WAV while optionally enhancing the recognizer input for distant speech and transcribing it on-device.
2. Analyze name calls, question phrases, and nearby context to decide whether an alert is warranted.
3. Show the teacher's original words first, so the user can verify the detected question.
4. When AI is enabled, send text context—not the audio—to user-configured model services.
5. Keep recordings, transcripts, alerts, and answers in local history for later review.

## Feature overview

| Area | Current implementation |
|---|---|
| Offline transcription | A single `AudioRecord` stream; a bundled small Vosk model and two optional sherpa-onnx Zipformer tiers; far-field gain, soft limiting, conservative noise gating, and pause preservation |
| Microphone input | System automatic, built-in, or external-first routing, with raw level, recognizer level, and applied gain shown in the UI |
| Classroom cue detection | Final-transcript confirmation for names, student numbers, forms of address, name-call phrases, explicit questions, whole-class questions, and custom high-priority keywords |
| Context capture | Handles both “question before name” and “name before question”; context windows are adjustable from 30 to 120 seconds |
| Notifications | High-priority phone banners, configurable vibration strength, Android promoted ongoing notification requests, and MagicOS compatibility checks |
| Huawei watch | `wearable/KexingWatchCue` contains the Lite Wearable source; the phone can send classroom cues and AI answers over Wear Engine |
| AI answers | Parallel requests to multiple HTTPS services compatible with OpenAI Chat Completions, with separate fast/thorough roles and timeouts |
| Manual retry | Select Chinese characters or English word tokens from the original transcript, including non-contiguous selections and drag selection, then resend to chosen models |
| Local archive | Course- and lesson-based WAV recordings, transcripts, alerts, AI answers, and recording offsets, retained until manually deleted |
| Markdown export | Export one lesson, the current course, or every course with transcripts, classroom questions, alerts, and AI answers |
| Secret storage | API keys are encrypted with AES-GCM using Android Keystore; recordings and key configuration are excluded from system backup |

ClassCue does not use text-to-speech, and it does not upload raw recordings to AI services.

## Basic workflow

1. On first launch, grant microphone and notification permissions.
2. Enter a course name on the Listen screen. In Settings, enter the names, student number, forms of address, and high-priority keywords that may refer to you.
3. Choose the small, medium, or large offline recognition tier. Medium and large models are downloaded only when requested.
4. Classroom far-field enhancement is enabled by default. When using a USB, Bluetooth, or wired microphone, select the External input strategy.
5. To enable AI answers, add at least one HTTPS BaseURL, model name, and API key, then assign either the Fast or Thorough role.
6. Return to the main screen and start a class session. Notifications show the teacher's original words first and update when AI results arrive; after class, export Markdown from History.
7. To use the watch, build and sign the HAP under `wearable/KexingWatchCue`, then enter that HAP's SHA-256 fingerprint in Settings → Huawei watch. Sending is intentionally blocked until formal signing and device pairing are complete.

Multiple forms of address can be entered in one field and separated by a Chinese comma, comma, semicolon, or newline—for example: `Xiao Ming, Student Li, class monitor, class representative`.

## Offline model tiers

| Tier | Purpose | Delivery | Download size |
|---|---|---|---:|
| Small | Low storage use and immediate startup | Bundled Vosk small Chinese model | Included in the app |
| Medium | More stable streaming classroom transcription | On-demand Zipformer INT8 download | About 127 MiB |
| Large | A second, higher-accuracy pass after streaming recognition | On-demand Zipformer CTC INT8 download | About 288 MiB |

Model downloads run in a dedicated foreground data-sync service and provide progress, current speed, pause, resume, retry, and delete controls. The large tier depends on the medium streaming model, so ClassCue queues the medium package first when required.

The exact byte length and SHA-256 of every model package are pinned in the client (see `OfflineModelCatalog.kt`). A download stops early if the server advertises a different size, and a finished archive is installed only after its SHA-256 matches; a mismatching file is deleted and the user is asked to download again. If upstream ever replaces a package under the same URL, it cannot be installed until a new ClassCue release re-verifies and re-pins it.

## Quick start

### Requirements

| Component | Requirement |
|---|---|
| Operating system | Android 13–16 |
| CPU architecture | `arm64-v8a` |
| JDK | 17 |
| Android SDK | 36 |
| Android Build Tools | 36.0.0 |
| Android Studio | 2025.2.2 or a compatible release |

### Clone and build

```bash
git clone https://github.com/lu0973-cb02d/ClassCue.git
cd ClassCue
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows PowerShell or Command Prompt:

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If access to Google Maven is unreliable in mainland China, you can temporarily provide a compatible mirror base URL:

```powershell
$env:KEXING_MAVEN_PROXY = "https://maven.aliyun.com/repository"
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The current version is `0.2.6 (15)`. The Debug version name is `0.2.6-debug`, and its application ID is `com.classcue.kexing.debug`, allowing it to be installed separately from builds that use the release application ID. An installable test APK and its SHA-256 checksum are available from [Releases](https://github.com/lu0973-cb02d/ClassCue/releases). The test APK uses an Android Debug signature and cannot update a build signed with a different certificate.

## Configuring AI services

A practical setup is one low-latency model and one more capable model:

| Role | Suggested timeout | Behavior |
|---|---:|---|
| Fast | 5–15 seconds | The first successful answer updates the notification immediately |
| Thorough | 25–45 seconds | A later result updates the same notification and is shown first |

The BaseURL must use HTTPS. ClassCue normalizes common forms to a Chat Completions endpoint:

```text
https://example.com      → https://example.com/v1/chat/completions
https://example.com/v1   → https://example.com/v1/chat/completions
```

An address already ending in `/chat/completions` is left unchanged. Each model keeps its own BaseURL, model name, role, API key, enabled state, and timeout. A failure from one service does not block the others.

## Privacy and data boundaries

| Data | Default destination |
|---|---|
| Raw microphone audio | A local WAV file retained until manual deletion; never sent to AI services |
| Far-field enhanced audio | Used only in memory by the offline recognizer; neither stored separately nor uploaded |
| Offline transcript | Recognized on-device and stored by course and lesson until manual deletion |
| Alerts and answers | Local SQLite history retained until manual deletion |
| AI API keys | Encrypted local storage protected by Android Keystore |
| AI requests | Text context sent only to HTTPS services configured by the user |
| Medium and large models | Downloaded from official `k2-fsa/sherpa-onnx` GitHub Release assets and checked against pinned SHA-256 values before installation |

Obtain any required consent before recording and follow school policies and applicable laws. Do not use this project during exams or wherever electronic devices are prohibited. Never attach real recordings, classroom transcripts, API keys, or other personal information to a public issue. See [SECURITY.md](SECURITY.md) for security reporting guidance.

## Project structure

```text
ClassCue/
├─ app/src/main/java/com/classcue/kexing/
│  ├─ data/       # Local history, recording repository, settings, and secrets
│  ├─ engine/     # Recording, ASR, cue classification, model downloads, and AI requests
│  ├─ model/      # Application settings and domain models
│  └─ ui/         # Jetpack Compose UI
├─ app/src/main/assets/
│  └─ vosk-model-small-cn-0.22/  # Bundled offline small model
├─ app/src/test/  # JVM unit tests
├─ wearable/KexingWatchCue/  # Huawei Lite Wearable source and pre-signing checks
├─ design/        # App icon design assets
└─ LICENSES/      # Copies of third-party licenses
```

## Build status

The current public snapshot has been verified with:

- 35 passing JVM unit tests and no failures.
- A successful complete Debug APK build.
- Android Lint with no errors, 24 warnings, and 1 hint.
- GitHub Actions builds the APK and attaches it to the corresponding Release.

See [BUILD_STATUS.md](BUILD_STATUS.md) for the complete scope and known warnings. Publishers who need a signed APK should read [FORMAL_SIGNING_REQUIRED.md](FORMAL_SIGNING_REQUIRED.md).

## Known limitations

- Classroom distance, echo, overlapping speakers, accents, and uncommon names all affect recognition accuracy. Software gain cannot restore speech that the microphone never captured. For long distances, move the phone closer or use an external microphone near the teacher; the project does not claim 100% accuracy.
- Long-term 16 kHz mono WAV storage uses roughly 110 MiB per hour. Export or delete old recordings from History as needed.
- Foreground services and a wake lock improve background reliability but cannot bypass force-stop, the microphone privacy switch, or aggressive vendor battery management.
- Whether MagicOS renders an Android ongoing notification as a Live Capsule is decided by the operating system. ClassCue does not call undocumented vendor APIs.
- The current UI is Chinese-only, and the build target is `arm64-v8a` only.
- The watch source still requires Huawei DevEco/Hvigor, a formal HAP signing certificate/Profile, and a real Huawei watch. Those materials are intentionally excluded, so this repository does not claim to contain a submission-ready signed HAP.

## Contributing

Bug reports, feature proposals, and pull requests are welcome. Before submitting a code change, run at least:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Do not commit keys, certificates, unauthorized third-party platform materials, real classroom recordings, personal transcripts, or model-service credentials.

## License and third-party software

Original ClassCue source code and documentation are released under the [MIT License](LICENSE).

Vosk, sherpa-onnx, AndroidX, Jetpack Compose, JNA, Apache Commons, pretrained models, and other third-party materials remain subject to their own licenses and model documentation. The repository-wide MIT license does not replace those terms. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt).
