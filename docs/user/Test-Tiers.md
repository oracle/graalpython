# Detailed Test Tier Breakdown

GraalPy organizes platform testing into tiers that indicate the level of testing rigor and support you can expect for different platform configurations. This tiering system helps you understand:

- How thoroughly your platform is tested
- What level of stability to expect
- Which features are fully supported vs. experimental

Platforms are identified using the target tuple format: `[CPU architecture]-[Operating System]-[libc]-[JDK]-[Java version]`. JDK names follow [SDKMAN!](https://sdkman.io/) conventions, and "graal" refers to both Oracle GraalVM and GraalVM Community Edition (including Native Image).

> **Important:** GraalPy test tiers are similar to [CPython Platform Support Tiers](https://peps.python.org/pep-0011/), but do not constitute or imply any commitment to support.

Pure Python code runs reliably on GraalPy with recent JDKs when JIT compilation is disabled. However, advanced features like native extensions, platform-specific APIs, and JIT compilation have varying support depending on your platform tier.

## Tier 1

- **Stability:** CI failures block releases. Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or reverted immediately.
- **Responsibility:** All core developers are responsible to keep main, and thus these platforms, working.
- **Coverage:** Platform-specific Python APIs and Python C extensions are fully tested.

| Platform                         | Notes                      |
|----------------------------------|----------------------------|
| amd64-linux-glibc-graal-latest   | Oracle Linux 8 or similar. |
| aarch64-linux-glibc-graal-latest | Oracle Linux 8 or similar. |

## Tier 2

- **Stability:** CI failures block releases. Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or tests marked as skipped.
- **Test Coverage:** Circa 10% of tests running on Tier 1 platforms may be skipped on Tier 2 platforms.
- **Feature Support:** Platform-specific Python APIs are fully tested; Python C extensions may have more issues than on Tier 1 platforms.

| Platform                          | Notes                   |
|-----------------------------------|-------------------------|
| aarch64-macos-darwin-graal-latest | macOS on M-series CPUs. |

## Tier 3

- **Stability:** CI failures block releases. Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or tests marked as skipped.
- **Test Coverage:** Circa 25% of tests running on Tier 1 platforms may be skipped on Tier 3.
- **Feature Support:** Tests for platform-specific Python APIs and Python C extension are run, but not prioritized.

| Platform                        | Notes                                        |
|---------------------------------|----------------------------------------------|
| amd64-macos-darwin-graal-latest | macOS on Intel CPUs running Big Sur or newer. |
| amd64-windows-msvc-graal-latest | Windows 11, Windows Server 2025, or newer.   |
| amd64-linux-glibc-oracle-21     | JDK 21 is tested without JIT compilation.    |
| aarch64-linux-glibc-oracle-21   | JDK 21 is tested without JIT compilation.    |
| aarch64-macos-darwin-oracle-21  | JDK 21 is tested without JIT compilation.    |
| amd64-macos-darwin-oracle-21    | JDK 21 is tested without JIT compilation.    |
| amd64-windows-msvc-oracle-21    | JDK 21 is tested without JIT compilation.    |

## Tier 4

- **Stability:** CI failures do not block releases. Tests may be broken on the main and release branches.
- **Test Coverage:** Smoke tests with platform-agnostic pure Python workloads are run on a regular schedule.
- **Feature Support:** Only basic pure Python functionality is tested; platform-specific features and extensions are not prioritized.

| Platform                        | Notes                                         |
| ------------------------------- | ----------------------------------------------|
| amd64-linux-musl-graal-latest   | Ensures GraalPy can be built for and used on musl libc platforms such as Alpine Linux. |
| amd64-linux-glibc-j9-17         | Ensures that non-Oracle JVMs work for pure Python code without JIT. |
| ppc64le-linux-glibc-oracle-17   | Ensures that other architectures (ppc64le, s390x, risc-v) work for pure Python code without JIT. |
