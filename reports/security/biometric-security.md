# Biometric Face Data Protection & Privacy

## 1. Consent-First Architecture
- **Opt-In Mandate**: Face enrollment strictly enforces `consent_granted == True`. Submissions without explicit consent are rejected with HTTP 400.
- **Encryption at Rest**: Raw 128/512-dimensional facial embedding vectors are encrypted using Fernet (AES-128-CBC with HMAC-SHA256 authenticated encryption) before storage in `face_embeddings`.
- **Zero Raw Vectors in Logs**: Loggers never print facial vector arrays or plaintext biometric descriptors.
- **Instant Hardware Privacy Mode**: Activating privacy mode disables face recognition and AI tracking immediately across the entire node.
