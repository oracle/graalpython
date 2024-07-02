This directory contains patches applied by pip when installing packages. There is a directory for each package that
contains patches and optionally a configuration file that can specify rules for matching patches to package versions and
can also influence pip version selection mechanism.

Configuration files are named `metadata.toml` and can contain the following:

```toml
# The file defines an array of tables (dicts) named `patches`. The patch selection process iterates it and picks the
# first patch one that matches in version and dist type.
# The next entry will apply to a wheel foo-1.0.0
[[rules]]
# Optional. Relative path to a patch file. May be omitted when the entry just specifies `install-priority`
patch = 'foo-1.0.0.patch'
# Required if 'patch' is specified. SPDX license expression for the package (allows parentheses, 'AND', 'OR', 'WITH').
# Allowed licenses are enumerated in mx.graalpython/verify_patches.py
license = 'MIT'
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
# Optional. Can specify preference for or against this version when selecting which version to install. Defaults to 1.
# When ordering all available versions in the index, each version gets a priority of the first entry it matches in this
# file. If it doesn't match, it gets priority 0. Versions with higher priority are then prefered for installation. This
# means that by default, versions with patches are prefered. Set the priority to 0 if you want the version not to be
# prefered, for example when keeping an old patch that was accepted upstream in a newer version. Set the version to
# a number greater than 1 if you want given version to be preferred to other entries. Additionally, if you set the
# priority to 0, the version will not be shown in the suggestion list we display when we didn't find an applicable patch
install-priority = 1

# The next entry will apply to all other artifacts of foo
[[patches]]
patch = 'foo.patch'
license = 'MIT'
```
