# Third-party notices

This application uses the following third-party components:

- Vosk Android 0.3.75 and `vosk-model-small-cn-0.22`, from Alpha Cephei. Vosk is distributed under the Apache License 2.0. The small Chinese model is bundled for the offline compatibility tier.
- sherpa-onnx 1.13.5, from the Next-gen Kaldi project, distributed under the Apache License 2.0.
- Optional Zipformer model packages are downloaded directly from the official `k2-fsa/sherpa-onnx` GitHub release assets on first use of the medium or large tier. They are not redistributed inside this APK. Refer to the README/model card shipped by the upstream package for the model-specific license and training information.
- JNA, Apache Commons Compress, Apache Commons IO, AndroidX, Jetpack Compose, and their transitive dependencies are used under their respective open-source licenses.
- Huawei Wear Engine Android SDK is an optional proprietary dependency used only for the phone-to-watch channel. It remains subject to Huawei's developer terms and is not relicensed by the MIT license in this repository. The bundled Lite Wearable compatibility wrapper is retained with the watch source for the target platform; consult Huawei's current distribution terms before redistributing a signed HAP.

The MIT license at the repository root applies only to original ClassCue code and documentation. It does not replace the licenses, notices, model cards, or attribution requirements of third-party components and assets. A copy of the Apache License 2.0 is provided at `LICENSES/Apache-2.0.txt`.

Source and model references:

- https://alphacephei.com/vosk/
- https://alphacephei.com/vosk/models
- https://github.com/k2-fsa/sherpa-onnx
- https://k2-fsa.github.io/sherpa/onnx/pretrained_models/
- https://developer.huawei.com/consumer/cn/hms/huawei-wearengine
