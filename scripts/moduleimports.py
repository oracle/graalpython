#!/usr/bin/env python3
import os, subprocess, sys


if len(sys.argv) == 2:
    cmd = sys.argv[1]
else:
    cmd = "mx python --python.WithJavaStacktrace=true "


okoutput = b"Please note: This Python implementation is in the very early stages, and can run little more than basic benchmarks at this point.\n"
success = []
fail = []


lib = os.listdir("%s/../graalpython/lib-python/3" % os.path.dirname(__file__))
lib.sort()

for i in lib:
    if i.endswith(".py"):
        f = os.path.basename(i).replace(".py", "")
    elif os.path.isdir(i) and "__init__.py" in [os.path.basename(j) for j in os.listdir(i)]:
        f = os.path.basename(i)
    else:
        continue
    sys.stdout.write(f)
    sys.stdout.flush()
    proc = subprocess.run(
        "%s -c 'import %s'" % (cmd, f),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=True
    )
    if proc.stderr == okoutput:
        sys.stdout.write(".\n")
        success.append(f)
    else:
        sys.stdout.write("F\n")
        fail.append((f, proc.stderr))


print("Successes %d, Failures %d (~%f %%)", len(success), len(fail), len(success) / (len(success) + len(fail)) * 100)
for f,stderr in fail:
    print(f)
    print(stderr.decode("ascii"))
