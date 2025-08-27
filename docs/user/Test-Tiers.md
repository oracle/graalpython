# Detailed Test Tier Breakdown

GraalPy test tiers are similar to [CPython Platform Support Tiers](https://peps.python.org/pep-0011/), but do not constitute or imply any commitment to support.

Generally, running pure Python code on GraalPy without JIT is compatible with any recent JDK.
However, support for native extensions, platform-specific APIs, and just-in-time compilation is more limited.

Platform testing is organized into Tiers, each with specific goals.
Tiers are identified by a "target tuple": `[CPU architecture]-[Operating System]-[libc]-[JDK]-[Java version]`.
JDK names correspond to those used by [SDKMAN!](https://sdkman.io/).
The term "graal" is used to refer to testing on both Oracle GraalVM and GraalVM Community Edition, including GraalVM Native Image.
The following tables list tested platforms by tier.
Platforms not listed are untested.

**Tier 1**

- CI failures block releases.
- Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or reverted immediately.
- All core developers are responsible to keep main, and thus these platforms, working.
- Platform-specific Python APIs and Python C extensions are fully tested.

| Platform                         | Notes                      |
|----------------------------------|----------------------------|
| amd64-linux-glibc-graal-latest   | Oracle Linux 8 or similar. |
| aarch64-linux-glibc-graal-latest | Oracle Linux 8 or similar. |

**Tier 2**

- CI failures block releases.
- Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or tests marked as skipped.
- Circa 10% of tests running on Tier 1 platforms may be skipped on Tier 2 platforms.
- Platform-specific Python APIs are fully tested; Python C extensions may have more issues than on Tier 1 platforms.

| Platform                          | Notes                   |
|-----------------------------------|-------------------------|
| aarch64-macos-darwin-graal-latest | macOS on M-series CPUs. |

**Tier 3**

- CI failures block releases.
- Changes which would break the main or release branches are not allowed to be merged; any breakage should be fixed or tests marked as skipped.
- Circa 25% of tests running on Tier 1 platforms may be skipped on Tier 3.
- Tests for platform-specific Python APIs and Python C extension are run, but not prioritized.

| Platform                        | Notes                                        |
|---------------------------------|----------------------------------------------|
| amd64-macos-darwin-graal-latest | macOS on Intel CPUs running BigSur or newer. |
| amd64-windows-msvc-graal-latest | Windows 11, Windows Server 2025, or newer.   |
| amd64-linux-glibc-oracle-21     | JDK 21 is tested without JIT compilation.    |
| aarch64-linux-glibc-oracle-21   | JDK 21 is tested without JIT compilation.    |
| aarch64-macos-darwin-oracle-21  | JDK 21 is tested without JIT compilation.    |
| amd64-macos-darwin-oracle-21    | JDK 21 is tested without JIT compilation.    |
| amd64-windows-msvc-oracle-21    | JDK 21 is tested without JIT compilation.    |
