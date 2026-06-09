# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
# The above copyright notice and either this complete permission notice or a
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

import os
import re
import sys
from pathlib import Path
from urllib.parse import urlparse
from warnings import warn

import graalpy_pip_extensions as graalpy


PIP_CLI_PARSE_ARGS = "pip.cli.parse_args"
PIP_FIND_ALL_CANDIDATES = "pip.package_finder.find_all_candidates"
PIP_REQUIREMENT_SOURCE_READY = "pip.requirement.source_ready"
PIP_WHEEL_INSTALL = "pip.wheel.install"

GRAALPY_WHEELS_URL = "https://www.graalvm.org/python/wheels/"
PIP_INDEX_URL_ENV_VAR = "PIP_INDEX_URL"
PIP_CACHE_DIR_ENV_VAR = "PIP_CACHE_DIR"
CACHE_DIR_SUFFIX = "-graalpy"

_PATCHED_SOURCES = set()
_PATCHED_DISTS = set()


def _apply_cli_defaults(options, args):
    if PIP_INDEX_URL_ENV_VAR not in os.environ and hasattr(options, "extra_index_urls"):
        extra_index_urls = getattr(options, "extra_index_urls")
        if extra_index_urls is None:
            extra_index_urls = []
        elif not isinstance(extra_index_urls, list):
            extra_index_urls = list(extra_index_urls)
        if GRAALPY_WHEELS_URL not in extra_index_urls:
            extra_index_urls.insert(0, GRAALPY_WHEELS_URL)
        options.extra_index_urls = extra_index_urls

    if PIP_CACHE_DIR_ENV_VAR not in os.environ and hasattr(options, "cache_dir") and not any(arg == "--cache-dir" or arg.startswith("--cache-dir=") or arg == "--no-cache-dir" for arg in args or ()):
        cache_dir = getattr(options, "cache_dir")
        if cache_dir:
            cache_path = Path(cache_dir)
            if cache_path.name and not cache_path.name.endswith(CACHE_DIR_SUFFIX):
                options.cache_dir = str(cache_path.with_name(cache_path.name + CACHE_DIR_SUFFIX))


def _candidate_details(candidate):
    link = candidate.link
    return (
        candidate.name,
        str(candidate.version),
        {
            "url": link.url,
            "filename": link.filename,
            "comes_from": getattr(link, "comes_from", "GraalPy compatibility patches"),
            "requires_python": getattr(link, "requires_python", None),
            "yanked_reason": getattr(link, "yanked_reason", None),
        },
    )


def _add_graalpy_candidates(project_name, extra_candidates):
    extra_candidates.extend(_candidate_details(candidate) for candidate in graalpy.get_graalpy_candidates(project_name))


def _archive_name(link):
    if filename := getattr(link, "filename", None):
        return os.path.basename(filename)
    path = urlparse(str(link)).path
    return os.path.basename(path) or os.path.basename(str(link))


def _dist_key(filename):
    match = re.match(r"^(?P<name>.*?)-(?P<version>[^-]+).*?\.(?:tar\.(?:gz|bz2|xz)|tar|zip|whl)$", filename, re.I)
    if match:
        return graalpy.canonicalize_name(match.group("name")), match.group("version")
    return None


def _apply_graalpy_source_patches(link, source_dir):
    if source_dir is None:
        return
    source_path = Path(source_dir).resolve()
    if source_path in _PATCHED_SOURCES:
        return
    _PATCHED_SOURCES.add(source_path)

    archive_name = _archive_name(link)
    if key := _dist_key(archive_name):
        _PATCHED_DISTS.add(key)
    graalpy.apply_graalpy_patches(archive_name, source_path, warn_suggested_versions=True)


def _find_dist_info(root):
    for path in root.iterdir():
        if path.is_dir() and path.name.endswith(".dist-info"):
            return path
    raise RuntimeError("Cannot find .dist-info directory in wheel")


def _write_record(root, dist_info):
    import base64
    import csv
    import hashlib

    record = dist_info / "RECORD"
    rows = []
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        rel = path.relative_to(root).as_posix()
        if path == record:
            rows.append((rel, "", ""))
            continue
        digest = hashlib.sha256(path.read_bytes()).digest()
        encoded = base64.urlsafe_b64encode(digest).rstrip(b"=").decode("ascii")
        rows.append((rel, f"sha256={encoded}", str(path.stat().st_size)))
    with record.open("w", encoding="utf-8", newline="") as f:
        csv.writer(f).writerows(rows)


def _extract_wheel(wheel_path):
    import zipfile
    from tempfile import TemporaryDirectory

    tmpdir = TemporaryDirectory()
    root = Path(tmpdir.name)
    with zipfile.ZipFile(wheel_path) as z:
        for info in z.infolist():
            target = Path(z.extract(info, root))
            mode = info.external_attr >> 16
            if mode and not info.is_dir():
                target.chmod(mode & 0o777)
    return root, tmpdir


def _rewrite_wheel(root, wheel_path):
    import zipfile
    from tempfile import NamedTemporaryFile

    wheel_path = Path(wheel_path)
    with NamedTemporaryFile(dir=wheel_path.parent, prefix=wheel_path.name, suffix=".tmp", delete=False) as tmp_file:
        tmp_path = Path(tmp_file.name)
    try:
        with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED) as z:
            for path in sorted(p for p in root.rglob("*") if p.is_file()):
                z.write(path, path.relative_to(root).as_posix())
        os.replace(tmp_path, wheel_path)
    finally:
        try:
            tmp_path.unlink()
        except FileNotFoundError:
            pass


def _mark_extracted_wheel(root):
    dist_info = _find_dist_info(root)
    (dist_info / graalpy.MARKER_FILE_NAME).touch()
    _write_record(root, dist_info)


def _apply_graalpy_wheel_patches(wheel_path):
    wheel_path = Path(wheel_path)
    if graalpy.DISABLE_PATCHING or not wheel_path.is_file() or wheel_path.suffix != ".whl" or graalpy.is_wheel_marked(wheel_path):
        return
    if _dist_key(wheel_path.name) in _PATCHED_DISTS:
        graalpy.mark_wheel(wheel_path)
        return

    root, cleanup = _extract_wheel(wheel_path)
    try:
        graalpy.apply_graalpy_patches(wheel_path, root, warn_suggested_versions=True)
        _mark_extracted_wheel(root)
        _rewrite_wheel(root, wheel_path)
    finally:
        cleanup.cleanup()


def _audit_hook(event, args):
    try:
        if event == PIP_CLI_PARSE_ARGS:
            options, parsed_args, _verbosity = args
            _apply_cli_defaults(options, parsed_args)
        elif event == PIP_FIND_ALL_CANDIDATES:
            project_name, _candidates, extra_candidates = args
            _add_graalpy_candidates(project_name, extra_candidates)
        elif event == PIP_REQUIREMENT_SOURCE_READY:
            _name, _specifier, link, source_dir, _editable, _verbosity = args
            _apply_graalpy_source_patches(link, source_dir)
        elif event == PIP_WHEEL_INSTALL:
            _name, wheel_path = args
            _apply_graalpy_wheel_patches(wheel_path)
    except Exception as e:
        warn(f"failed to apply pip compatibility patches: {e}")


sys.addaudithook(_audit_hook)
