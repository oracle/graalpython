# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import sys
from contextlib import contextmanager
from functools import lru_cache
from pathlib import Path
from subprocess import check_output as subprocess_check_output

from virtualenv.create.creator import Creator
from virtualenv.create.describe import PosixSupports, WindowsSupports
from virtualenv.seed.embed.pip_invoke import PipInvoke
from virtualenv.seed.wheels import get_wheel
from virtualenv.seed.wheels.bundle import from_dir


class AbstractGraalPyCreator(Creator):
    """
    Describe and fake Creator service for GraalPy.

    For the time being, we expect users to just use the builtin 'venv' creator
    with GraalPy, but the virtualenv package cannot just support GraalPy as a
    generic "venv capable python", because it needs a Describe service for the
    Seeder and Activator services.

    Note: that even if we provided GraalPy specific Creator service, the builtin
    'venv' Creator takes precedence as the default. Therefore, the users would
    have to enable the GraalPy Creator via explicit option `--creator graalpy`.

    Note: GraalPy cannot just simply pretend that it is CPython, because then the
    virtualenv Creator would not set up the toolchain executables in the new virtual
    environment.
    """

    def __init__(self, options, interpreter):
        super().__init__(options, interpreter)
        self.options = options

    @classmethod
    def can_create(cls, interpreter):
        # We are fake creator, we actually do not want to be used as Creator
        return None

    @classmethod
    def can_describe(cls, interpreter):
        if not (interpreter.implementation == "GraalVM" and super().can_describe(interpreter)):
            return False

        # We monkey patch SeederSelector to use our ensurepip, but only if we know that there
        # is a chance that we are creating virtualenv for GraalPy, so that we do not mess up
        # with virtualenv if not necessary. This relies on the fact that virtualenv calls this
        # because it calls SeederSelector._get_default, but even if we monkey patched SeederSelector
        # eagerly, that code would still be executed only when virtualenv loads our plugin, which
        # happens to be very close to the point when it calls can_describe
        try:
            from virtualenv.run import SeederSelector
            _get_default_orig = SeederSelector._get_default

            def _seeder_selector_get_default_override(self):
                if self.interpreter.implementation == "GraalVM":
                    return "graalpy"
                else:
                    return _get_default_orig()

            SeederSelector._get_default = _seeder_selector_get_default_override
        except ImportError:
            pass
        except AttributeError:
            pass
        return True

    @classmethod
    def exe_stem(cls):
        return "graalpy"

    def create(self):
        raise RuntimeError("Please use the 'venv' creator with GraalPy. "
                           "It should be the default and can be explicitly requested "
                           "with command line option `--creator venv`, "
                           "or environment variable VIRTUALENV_CREATOR=venv")


class GraalPyCreatorPosix(AbstractGraalPyCreator, PosixSupports):
    pass


class GraalPyCreatorWindows(AbstractGraalPyCreator, WindowsSupports):
    pass


@lru_cache()
def get_ensurepip_path(exe):
    if exe.samefile(sys.executable):
        import ensurepip
        ensurepip_path = ensurepip.__path__[0]
    else:
        cmd = [str(exe), "-u", "-c", 'import ensurepip; print(ensurepip.__path__[0])']
        ensurepip_path = subprocess_check_output(cmd, universal_newlines=True).strip()
    return Path(ensurepip_path) / '_bundled'


def pip_wheel_env_run(search_dirs, app_data, env, exe):
    env = env.copy()
    env.update({"PIP_USE_WHEEL": "1", "PIP_USER": "0", "PIP_NO_INPUT": "1"})
    ensurepip_path = get_ensurepip_path(exe)
    if ensurepip_path:
        wheel_paths = list(ensurepip_path.glob('pip-*.whl'))
        if wheel_paths:
            env["PYTHONPATH"] = str(wheel_paths[0])
            return env
    wheel = get_wheel(
        distribution="pip",
        version=None,
        for_py_version=f"{sys.version_info.major}.{sys.version_info.minor}",
        search_dirs=search_dirs,
        download=False,
        app_data=app_data,
        do_periodic_update=False,
        env=env,
    )
    if wheel is None:
        raise RuntimeError("could not find the embedded pip")
    env["PYTHONPATH"] = str(wheel.path)
    return env


class GraalPySeeder(PipInvoke):
    """
    Seeder for GraalPy that reuses the PipInvoke seeder to install setuptools
    and pip bundled with GraalPy in the ensurepip module, so that we can be
    sure that the new environment is seeded with versions compatible with
    GraalPy. This applies only if the user does not explicitly set the
    setuptools/pip versions via command line flags to virtualenv.

    The choice of the wheel package version is left to the base class.

    We have to resort to reusing implementation classes from virtualenv, because
    we would end up duplicating them here anyway in order for our seeder to match
    the user expectations w.r.t. caching, internet access, etc., and to work
    seamlessly with other tools, like tox, which just passes --no-download
    regardless whether the seeder may support that flag or not (the latter case
    casing an error).
    """

    def run(self, creator):
        if not self.enabled:
            return
        for_py_version = creator.interpreter.version_release_str
        with self.get_pip_install_cmd(creator.exe, for_py_version) as cmd:
            env = pip_wheel_env_run(self.extra_search_dir, self.app_data, self.env, creator.exe)
            self._execute(cmd, env)

    @contextmanager
    def get_pip_install_cmd(self, exe, for_py_version):
        cmd = [str(exe), "-m", "pip", "-q", "install", "--only-binary", ":all:", "--disable-pip-version-check"]
        ensurepip_path = get_ensurepip_path(exe)
        for dist, version in self.distribution_to_versions().items():
            if ensurepip_path and version == 'bundle':
                if dist in ('pip', 'setuptools'):
                    wheel = from_dir(dist, None, for_py_version, [ensurepip_path])
                else:
                    # For wheel, install just `wheel` our patching logic should pick the right version
                    cmd.append(dist)
                    continue
            else:
                wheel = get_wheel(
                    distribution=dist,
                    version=version,
                    for_py_version=for_py_version,
                    search_dirs=self.extra_search_dir,
                    download=False,
                    app_data=self.app_data,
                    do_periodic_update=self.periodic_update,
                    env=self.env,
                )
            if wheel is None:
                raise RuntimeError("could not get wheel for distribution {}".format(dist))
            cmd.append(str(wheel.path))
        yield cmd
