# Cuddly Eureka — Android Python + Shell Terminal

An Android terminal app for Meta Quest and Android devices.

## Current architecture

- Python 3.12 embedded with Chaquopy.
- Runtime package installs via bundled pip into app-private storage. Only wheels compatible with the device's Android ABI/Python version will install; packages requiring unsupported native builds will fail with pip's error.
- Shell commands are delegated to the active Shizuku-compatible service (ByteZuku/Shizuku) after explicit app authorization.
- The shell identity is detected at runtime. ByteZuku authorization does not automatically mean root: the service may be UID 2000 (ADB shell) or UID 0 (root), depending on how its service was started.
- Commands such as `pm`, `settings`, `dumpsys`, `am`, `getprop`, and `id` are shell commands. This is not a full host-side ADB client: `adb devices` and USB transport management are not provided.

## ByteZuku setup

1. Install and start ByteZuku on the device.
2. Pair/start its service using ByteZuku's own setup flow.
3. Open Cuddly Eureka and grant its request in ByteZuku's Authorized Apps screen.
4. Tap **Connect** in the app. The status panel reports the actual UID and SELinux context when available.

The app uses the public Shizuku API protocol. ByteZuku compatibility depends on its implementation of that API; if the Binder is not exposed as expected, the app reports that instead of claiming it is connected.

## Python and pip

Use `python <code>` to execute Python. Use `pip install <package>` to install a compatible wheel into the app's private Python package directory, and `pip list` to list installed runtime packages. This is not a desktop Linux environment: Android wheel availability and ABI compatibility apply.

## Build

GitHub Actions runs on pushes to `main` and manual dispatch. It builds a debug APK and uploads it as the `cuddly-eureka-debug-apk` artifact.

Requirements: JDK 17, Android SDK platform 35, Build Tools 35.0.0, Gradle 8.13.

## Security

Commands run with the identity explicitly granted by the local ByteZuku/Shizuku service. Review commands before execution. Do not paste commands from untrusted sources. Python packages execute inside this app's process and may access the permissions granted to this app.

## License

Project code: MIT. Third-party dependencies retain their own licenses.
