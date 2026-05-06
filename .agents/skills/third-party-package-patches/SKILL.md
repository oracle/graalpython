---
name: third-party-package-patches
description: Create or update GraalPy third-party package compatibility patches under graalpython/lib-graalpython/patches, including PyPI source preparation, rebasing existing patches, metadata.toml updates, license checks, version-range validation, and verify_patches.py validation.
---

# Third-Party Package Patches

Use this skill when creating or updating compatibility patches for packages installed by pip on GraalPy.

## Key Files
- Source preparation: `scripts/get_pypi_source.py`
- Patch metadata: `graalpython/lib-graalpython/patches/metadata.toml`
- Patch directory: `graalpython/lib-graalpython/patches/`
- Metadata verifier: `mx.graalpython/verify_patches.py`

## Workflow
1. Identify the package name and target version. Use the normalized package key used by PyPI/pip: lowercase with runs of `-`, `_`, and `.` normalized to `-`.

2. Prepare source with the repo helper:
```bash
python scripts/get_pypi_source.py package==version
```
The script prints `Prepared source at: ...`. Use that directory as the working tree. It is already a temporary git repository with an initial commit, and it has already been processed by `graalpython/lib-graalpython/modules/autopatch_capi.py`.

3. Inspect `graalpython/lib-graalpython/patches/metadata.toml` for existing `[[package.rules]]` entries.
- If a matching patch exists for the requested version, apply it first.
- If only a nearby version has a patch, try applying that patch and rebase it carefully onto the prepared source.
- Honor `subdir` when present: pip applies sdist patches from that subdirectory. Apply and generate the patch from the same directory layout that the metadata rule will use.
- Honor `dist-type` when choosing whether the patch is for `sdist`, `wheel`, or both.

4. Apply existing patches using the same semantics as pip where practical:
```bash
patch -f -d /tmp/package-version-... -p1 -i /path/to/graalpython/lib-graalpython/patches/existing.patch
```
If the rule has `subdir = 'src'`, use `-d /tmp/package-version-.../src`. Resolve rejects by editing source files in the temporary repository, then remove any `.rej`/`.orig` files after checking them. Search for conflict markers and rejects before staging.

5. Make the GraalPy compatibility changes in the prepared source. Keep the patch minimal and package-focused.

6. Stage the desired changes in the temporary source repository:
```bash
git add -A
git diff --cached
```
Review the staged diff. Do not include generated caches, build outputs, rejected patch files, or unrelated churn.

7. Create or refresh the patch file only from the staged git diff:
```bash
git diff --cached > /path/to/graalpython/lib-graalpython/patches/package-version.patch
```
Guardrail: do not hand-edit patch files. If the patch output is wrong, fix the temporary source tree, adjust staging, and regenerate with `git diff --cached`.

8. Update `metadata.toml` if needed.
- Add a new `[[package.rules]]` entry when no suitable one exists.
- Keep existing precedent for rule ordering, patch names, comments, `install-priority`, `dist-type`, and `subdir`.
- Every rule with `patch = ...` needs `license = ...`.
- When adding a new patch entry, confirm the license from PyPI metadata, preferably the JSON API for the exact release or current package metadata. Use an SPDX identifier accepted by `mx.graalpython/verify_patches.py`.
- If upstream publishes no suitable PyPI source artifact, add `[[package.add-sources]]` with the exact version and release tarball URL, then rerun `scripts/get_pypi_source.py`.

9. Choose the version range deliberately.
- Prefer one patch over many when the same patch applies cleanly and the underlying package layout/API is stable.
- Test every version covered by a widened range, including lower and upper boundary releases.
- Small, robust patches may use an open-ended range when they are unlikely to break in future versions.
- If newer versions no longer need a patch, add a no-patch rule with a note rather than leaving users pointed at stale patched versions.

10. Validate patch application for the covered versions.
- For each version in the rule range that you claim to support, prepare a fresh source tree with `scripts/get_pypi_source.py`.
- Apply the generated patch with `patch -f -p1` from the root or `subdir` exactly as the metadata rule requires.
- Check for nonzero exit status, `.rej` files, `.orig` files, unexpected unstaged files, and conflict markers.

11. Run the repository verifier before finishing:
```bash
python mx.graalpython/verify_patches.py graalpython/lib-graalpython/patches
```

12. If you were asked to build or test the patched package, you need to rebuild GraalPy with `mx python-jvm` to pick up the changes. Create a venv with `mx python -m venv venv_name` and use it for building and testing.

## Metadata Reference
Rule keys accepted by the verifier are:
- `version`
- `patch`
- `license`
- `subdir`
- `dist-type`
- `install-priority`
- `note`

Allowed `dist-type` values are `wheel` and `sdist`.

Allowed license identifiers are maintained in `mx.graalpython/verify_patches.py`. If PyPI metadata is ambiguous, inspect the source distribution license files and report the ambiguity instead of guessing.

## Reporting
When done, report:
- package and version(s) tested
- patch file created or updated
- metadata rule added or changed
- PyPI license value used
- `verify_patches.py` result
