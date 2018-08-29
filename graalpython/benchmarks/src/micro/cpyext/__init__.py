from importlib import invalidate_caches
from distutils.core import setup, Extension
__dir__ = __file__.rpartition("/")[0]


def ccompile(name, code):
    source_file = '%s/%s.c' % (__dir__, name)
    with open(source_file, "w") as f:
        f.write(code)
    module = Extension(name, sources=[source_file])
    args = ['--quiet', 'build', 'install_lib', '-f', '--install-dir=%s' % __dir__]
    setup(
        script_name='setup',
        script_args=args,
        name=name,
        version='1.0',
        description='',
        ext_modules=[module]
    )
    # IMPORTANT:
    # Invalidate caches after creating the native module.
    # FileFinder caches directory contents, and the check for directory
    # changes has whole-second precision, so it can miss quick updates.
    invalidate_caches()
