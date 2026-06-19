# GraalOS Standalone Sandbox Demo

This demo shows a small chat-style expression evaluator running inside the
GraalPy GraalOS standalone.

The story:

1. `rich` renders a friendly terminal UI.
2. `asteval` evaluates normal user expressions with an application-level
   restricted evaluator. This demo also removes `open` from that evaluator.
3. `/unsafe ...` expressions deliberately bypass `asteval` and use Python
   `eval` directly.
4. The process is still inside the GraalOS sandbox, so file, subprocess,
   network, and native library attempts remain contained.

## Setup

From the rebuilt standalone directory, I ran this. This needs a patched
graalhost right now (I reported GRAALOS-8260), so the bundle currently includes
it.

```bash
cd mxbuild/linux-amd64/GRAALPY_NATIVE_GRAALOS_STANDALONE
./bin/graalpy -Im ensurepip
python3 -m pip download --only-binary=:all: --dest demo-wheels rich asteval
./bin/graalpy -Im pip install --no-index --find-links /demo-wheels rich asteval
```

The online download is intentionally done outside the sandbox. The sandboxed
standalone has no outbound network mapping by default, which is one of the
things the demo can show.

Copy or place `graalos_sandbox_chat.py` in the standalone root, then run:

```bash
./bin/graalpy graalos_sandbox_chat.py
```

For a non-interactive walkthrough:

```bash
./bin/graalpy graalos_sandbox_chat.py --demo
```

## Demo Beats

Start with a normal expression:

```python
sum([i*i for i in range(1000)])
```

Then show that the app-level evaluator blocks direct file access:

```python
open('/etc/passwd').read()
```

Switch to `/unsafe` mode to bypass the app-level evaluator while keeping the
outer GraalOS sandbox:

```python
/unsafe open('/etc/passwd').read().splitlines()[:3]
/unsafe open('/etc/shadow').read()
/unsafe __import__('subprocess').run(['/bin/sh', '-c', 'id'], capture_output=True, text=True)
/unsafe __import__('socket').create_connection(('example.com', 80), timeout=2)
/unsafe __import__('ctypes').CDLL('libc.so').system(b'cat /etc/shadow')
```

Expected result: harmless operations work or fail normally; sensitive host
resources are unavailable because the process only sees the sandboxed virtual
filesystem, process namespace, and configured network policy. The native
`system()` probe returns `-1`, which the demo renders as blocked.

## Why This Is Useful

`asteval` is an application-level guardrail. It reduces accidental exposure but
it is not a complete containment boundary. GraalOS is the outer boundary: even
if application logic accidentally evaluates dangerous code in `/unsafe` mode,
the runtime still mediates filesystem, subprocess, native, and network behavior.
