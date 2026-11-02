# Security

PrismLock executes inside Samsung's biometrics UI process through LSPosed. Treat
unexpected crashes, provider access, file handling and hook-scope changes seriously.

There is no stable supported release. Security fixes target the current development
branch while this repository remains private.

Report suspected vulnerabilities privately to the repository owner, AurelioMo,
using a private collaboration channel. Do not open a public issue containing an
exploit, credentials, device dumps or personal data. If GitHub private vulnerability
reporting becomes enabled, use the repository's Security reporting form.

Include the affected commit/version, device and firmware versions, reproduction
steps and a minimal sanitized log. Do not send biometric templates, keys or a
complete system APK. Never change fingerprint enrollment to demonstrate a UI bug.
