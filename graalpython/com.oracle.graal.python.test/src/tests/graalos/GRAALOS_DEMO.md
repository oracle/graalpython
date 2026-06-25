# GraalOS Standalone Sandbox Demo

This demo shows a small chat-style Python evaluator running inside the
GraalPy GraalOS standalone.

The story:

1. `rich` renders a friendly terminal UI.
2. The demo treats each entered expression as untrusted Python code, such as
   code produced by an LLM agent or pasted by a human operator.
3. The process is inside the GraalOS sandbox, so file, subprocess,
   network, and native library attempts remain contained.

## Setup

We can install the `rich` wheel directly into the standalone's `site-packages`
using any standard Python. While we could run `ensurepip` and `pip` inside the
sandbox by configuring the appropriate network access, we do this here
intentionally done outside the sandbox. The sandboxed standalone has no
outbound network mapping by default, which is one of the things the demo can
show.

```bash
python3 -m pip install \
  --target GRAALPY_NATIVE_GRAALOS_STANDALONE/lib/python3.12/site-packages \
  --only-binary=:all: \
  --python-version 3.12 \
  --implementation py --implementation graalpy \
  --abi none --abi graalpy250_312_native \
  --platform any --platform graalos_x86_64 \
  --no-compile \
  rich
```

There should be a file `test_graalos_sandbox_chat.py` in this directory. If
not, find it in and copy it from the GraalPy source repository. From inside the
sandbox that file is available as `/test_graalos_sandbox_chat.py`, so run:

```bash
./bin/graalpy /test_graalos_sandbox_chat.py
```

For a non-interactive walkthrough:

```bash
./bin/graalpy /test_graalos_sandbox_chat.py --demo
```

## Demo Beats

Start with a normal expression:

```python
sum([i*i for i in range(1000)])
```

Then move on to untrusted code that tries to access host resources:

```python
open('/etc/passwd').read()
open('/etc/passwd').read().splitlines()[:3]
open('/etc/shadow').read()
__import__('subprocess').run(['/bin/sh', '-c', 'id'], capture_output=True, text=True)
__import__('socket').create_connection(('example.com', 80), timeout=2)
__import__('ctypes').CDLL('libc.so').system(b'cat /etc/shadow')
```

Expected result: harmless operations work or fail normally; sensitive host
resources are unavailable because the process only sees the sandboxed virtual
filesystem, process namespace, and configured network policy. The native
`system()` probe returns `-1`, which the demo renders as blocked.

## Why This Is Useful

This is a deliberately unsafe application pattern: it evaluates untrusted Python
code directly. That is useful for demonstrating the actual containment boundary.
GraalOS is that boundary, and it mediates filesystem, subprocess, native, and
network behavior even when the application itself offers no extra guardrails.
