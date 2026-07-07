# GraalPy GraalOS Standalone

This package runs GraalPy through `graalhost` with a sandboxed default
configuration. The goal is to feel like a normal Python installation for local
command-line use, while making resource usage, filesystem, and network access
explicit and sandboxing the entire execution to prevent untrusted Python code
or native extensions from compromising the system.

## Quick Start

Run Python normally:

```bash
bin/graalpy
bin/graalpy -c 'print(42)'
bin/python
bin/python3
```

Show Python help:

```bash
bin/graalpy --help
```

The launcher passes Python's own options through unchanged. After Python prints
its help, the launcher appends a short section describing the additional
`--graalhost.*` options.

Enable graalhost diagnostics for one run:

```bash
bin/graalpy --graalhost.verbose -c 'print(42)'
```

## What Is Sandboxed By Default

By default, the standalone:

- keeps `stdin`, `stdout`, and `stderr` attached to your terminal
- allows access to the standalone tree and the small set of system files needed
  for runtime startup
- denies general outbound network access
- denies bind and listen on TCP and UDP ports

This means `bin/graalpy` behaves like a local Python process, but it does not
automatically get unrestricted filesystem or internet access.

## Package Layout

- `bin/graalpy`, `bin/python`, `bin/python3`: launch GraalPy inside graalhost
- `config.json`: sandbox and launcher configuration
- `lib/graalos/graalpy-sandbox-launcher`: shell wrapper used by the launchers
- `lib/graalos/graalpy-sandbox-expand-config`: fills in generated filesystem
  mappings
- `lib/graalos/graalhost`: embedded GraalOS runtime

## `config.json`

`config.json` is the main file you edit to change sandbox behavior. The
launcher expands it before starting `graalhost`.

Common top-level fields:

- `env`: environment variables visible inside the sandboxed process
- `working_dir`: initial working directory inside the virtual filesystem
- `fds`: how standard input, output, error, and other file descriptors are
  wired
- `allowed_ports`: explicit bind and listen allowlist
- `netmappings`: outbound and inbound network policy
- `allow_runtime_codegen`: allow runtime-generated code after GraalOS
  binsweep verification
- `allow_signal_self_snapshot`: allows the process to create a snapshot by
  signaling itself
- `memlimit`: memory budget in GiB
- `testing_default_mappings`: keep this enabled for the packaged standalone

The launcher also understands a `graalhost` section:

```json
"graalhost": {
  "seccomp": null,
  "log_level": null,
  "log_to": null,
  "visorcalloutput": null,
  "extra_args": []
}
```

Meaning:

- `seccomp`: forwarded as `--seccomp`
- `log_level`: forwarded as `--log_level`
- `log_to`: forwarded as `--log_to`
- `visorcalloutput`: forwarded as `--visorcalloutput`
- `extra_args`: additional raw graalhost arguments, one item per array entry

If you do not set graalhost logging options, the launcher stays quiet by
default. `--graalhost.verbose` overrides that for a single invocation.

## Launcher Options

These options are consumed by the launcher and are not passed to Python:

- `--graalhost.verbose`
- `--graalhost.run_snapshot=PATH`
- `--graalhost.log_level=LEVEL`
- `--graalhost.log_to=DEST`
- `--graalhost.visorcalloutput=DEST`
- `--graalhost.seccomp=MODE`
- `--graalhost.extra_arg=ARG`

`--graalhost.run_snapshot=PATH` restores a previously created GraalOS snapshot
instead of starting a fresh Python process. It should be used by itself:

```bash
bin/graalpy --graalhost.run_snapshot=/path/to/persistIso...
```

## Common Scenarios

### Use It Like Normal Python

```bash
bin/graalpy -c 'print("hello")'
printf 'hello\n' | bin/graalpy -c 'print(input())'
```

The default config keeps the terminal connected:

```json
"fds": {
  "stdin": "stdin",
  "stdout": "stdout",
  "stderr": "stderr"
}
```

### Redirect Standard Streams

To write output to files, edit `config.json`:

```json
"fds": {
  "stdin": "null",
  "stdout": "file:/tmp/graalpy.stdout",
  "stderr": "file:/tmp/graalpy.stderr"
}
```

Use `append:/path` instead of `file:/path` if you want append mode.

### Keep Networking Disabled

This is the default. If you do not add `netmappings`, the process does not get
general outbound network access. If `allowed_ports` is empty, it also cannot
bind or listen on ports.

### Allow an Outbound TCP Connection

To allow connections to `127.0.0.1:6010`, add:

```json
"netmappings": [
  {
    "networks": [
      {
        "ips": ["127.0.0.1/32"],
        "protocols": [
          { "type": "tcp", "outgoing_ports": ["6010"] }
        ]
      }
    ]
  }
]
```

If you use hostnames instead of literal IPs, your network policy also needs to
allow DNS.

### Allow an Incoming Listener

To allow listening on `127.0.0.1:6006`, add:

```json
"allowed_ports": [6006],
"netmappings": [
  {
    "networks": [
      {
        "ips": ["127.0.0.1/32"],
        "protocols": [
          { "type": "tcp", "incoming_ports": ["6006"] }
        ]
      }
    ]
  }
]
```

### Create and Resume a Snapshot

If you want to resume a warm Python process later, enable self-snapshotting in
`config.json`:

```json
"allow_signal_self_snapshot": true
```

Then run a Python program that saves its snapshot path and signals itself with
`SIGSTOP` when it is ready:

```python
import os
import signal

print("Ready to snapshot")
os.kill(os.getpid(), signal.SIGSTOP)
```

After GraalOS writes the snapshot file, resume it with:

```bash
bin/graalpy --graalhost.run_snapshot=/path/to/persistIso...
```

Restoring a snapshot uses the saved process state. It does not take additional
Python command-line arguments on the same invocation.

When `allow_signal_self_snapshot` is enabled, the launcher keeps the generated
expanded endpoint config under `tmp/graalpy-sandbox.*` instead of deleting it
at process exit. Snapshot restore needs that original directory to remain
available because the saved endpoint configuration records it as
`endpoint_config_path`.

### Show Graalhost Diagnostics

For launcher-level troubleshooting:

```bash
bin/graalpy --graalhost.verbose -c 'print("hello")'
```

For more control, use one-run overrides such as:

```bash
bin/graalpy \
  --graalhost.log_level=debug \
  --graalhost.log_to=stderr \
  --graalhost.visorcalloutput=@stderr \
  -c 'print("hello")'
```

### Install extra packages

You may install additional packages directly into the standalone from the
outside, by selecting compatible tags, for example:

```bash
python3 -m pip install \
  --target GRAALPY_NATIVE_GRAALOS_STANDALONE/lib/python3.12/site-packages \
  --only-binary=:all: \
  --python-version 3.12 \
  --implementation py --implementation graalpy \
  --abi none --abi graalpy250_312_native \
  --platform any --platform graalos_x86_64 \
  --no-compile \
  rich asteval
```

You may have to set --extra-index-url to an index that provides provides
pre-built binary wheels for GraalOS, since this building these requires a
special toolchain.

## Notes About Graalhost

The standalone wraps `graalhost`, which is the GraalOS runtime responsible for:

- launching the Python isolate
- applying filesystem and network policy
- routing file descriptors
- creating and restoring snapshots
- emitting host-side diagnostics

The launcher covers common usage. If you need the full host CLI, run:

```bash
lib/graalos/graalhost --help
```
