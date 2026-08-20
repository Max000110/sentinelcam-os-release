# Edge AI & Vision Model Security

## 1. Model Storage & Execution
- **Local YUV Inference**: AI object detection and face recognition execute strictly on-device on the Android phone.
- **Isolated Threading**: AI inference runs on a background daemon worker thread (`SentinelCam-AI-Sampler`), preventing frame pipeline injection or denial of service.
- **Model Tampering Prevention**: Bundled model assets in APK assets are verified at compile-time and read-only at runtime.
