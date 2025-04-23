#
# Copyright (c) 2025, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#

VERSION = "0.9.0"

import argparse
import os
import shlex
import subprocess
import sys
import tempfile
import venv

from pathlib import Path


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--cflags", required=True)
    parsed_args = parser.parse_args()

    setup = Path(__file__).parent.absolute() / "setup.py"
    build = Path(parsed_args.out).absolute()
    build.mkdir(parents=True, exist_ok=True)

    if sys.platform != "win32":
        executable = " ".join(map(shlex.quote, __graalpython__.executable_list))
        with open(build / "graalpy", "w") as f:
            p = Path(f.name).absolute()
            f.write(f"""#!/bin/bash
            exec {executable} -B --DisableFrozenModules --Executable="$0" "$@"
            """)
            p.chmod(0o777)
            sys._base_executable = sys.executable = str(p)
    else:
        # win32 works because venv generates a venvlauncher there
        pass

    venv.main(args=[str(build / "venv")])
    if sys.platform == "win32":
        exe = build / "venv" / "Scripts" / "graalpy.exe"
    else:
        exe = build / "venv" / "bin" / "graalpy" 

    subprocess.run(
        map(str, [
            exe,
            setup,
            "build",
            "--build-lib",
            build / "build",
            "--build-temp",
            build / "temp",
            "install",
            "--single-version-externally-managed",
            "--root=/",
        ]),
        cwd=setup.parent,
        check=True,
        env=os.environ.copy() | {
            "SETUPTOOLS_SCM_PRETEND_VERSION": VERSION,
            "CFLAGS": os.environ.get("CFLAGS", "") + f" {parsed_args.cflags}"
        },
    )
