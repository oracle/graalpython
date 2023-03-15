This directory contains patches applied by pip when installing packages. There is a directory for each package that
contains patches and optionally a configuration file that can specify rules for matching patches to package versions and
can also influence pip version selection mechanism.

Configuration files are named `metadata.toml` and can contain the following:

```toml
# The file defines an array of tables (dicts) named `patches`. The patch selection process iterates it and picks the
# first patch one that matches in version and dist type.
# The next entry will apply to a wheel foo-1.0.0
[[patches]]
# Mandatory. Relative path to the patch file
patch = 'foo-1.0.0.patch'
# Optional. Version specifier according to https://peps.python.org/pep-0440/#version-specifiers. If omitted, it will
# match any version
version = '== 1.0.0'
# Optional. Type of distribution artifact. One of `wheel` or `sdist`. Omit unless you want to have separate patches for
# wheels and sdists.
dist-type = 'wheel'
# Optional. When applying a patch for a sdist that was created against a wheel, there can be a mismatch in the paths,
# when the wheel was built from a subdirectory. When applying a patch on a sdist, this option will cause the patch
# process to be run from given subdirectory. Has no effect when applying patches on wheels. 
subdir = 'src'

# The next entry will apply to all other artifacts of foo
[[patches]]
patch = 'foo.patch'
```
