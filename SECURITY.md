# Security Notes

## Current App Security Posture

- The app requests only the camera permission.
- The app does not request internet, location, contacts, microphone, phone, SMS, or install-package permissions.
- Android backup is disabled for app data.
- Cleartext network traffic is disabled.
- Shared export files are exposed only through the app's FileProvider cache path.
- Release builds use R8 minification, shrinking, and obfuscation.
- Release APK/AAB builds require a stable signing key through GitHub Actions secrets.

## Copy Protection

No Android app can be made impossible to copy or reverse engineer. The project
therefore uses layered protection:

- Release binaries are obfuscated with R8.
- Release artifacts must be signed with a private key.
- The repository is marked "All rights reserved" and does not grant permission
  to copy, redistribute, or create derivative applications.
- For stronger source-code protection, keep the repository private and invite
  only trusted collaborators.

## Recommended Play Store Settings

- Use Play App Signing.
- Keep the upload key private and backed up securely.
- Enable internal testing before production rollout.
- Complete the Data safety form accurately: the app processes photos and
  user-entered text on-device and does not transmit them to an app server.
- Do not add analytics, ads, cloud backup, or crash reporting SDKs unless the
  privacy policy and Data safety form are updated first.

## Reporting Issues

Security issues should be reported privately to the repository owner instead of
being posted publicly in issues.
