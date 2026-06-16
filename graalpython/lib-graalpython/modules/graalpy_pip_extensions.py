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

import abc
import logging
import os
import re
import sys
import tempfile
import zipfile
from types import SimpleNamespace
from contextlib import contextmanager
from pathlib import Path
import tomllib as tomli
from tomllib import TOMLDecodeError
from urllib.parse import urlparse, urljoin, urlunparse
from urllib.request import url2pathname

try:
    from pip._internal.models.link import Link as _PipLink
except ImportError:
    _PipLink = object

MARKER_FILE_NAME = 'GRAALPY_MARKER'
METADATA_FILENAME = 'metadata.toml'
DEFAULT_PATCHES_PATH = Path(__graalpython__.core_home) / 'patches'
VERSION_PARAMETER = '<version>'
DEFAULT_PATCHES_URL = f'https://raw.githubusercontent.com/oracle/graalpython/refs/heads/github/patches/{VERSION_PARAMETER}/graalpython/lib-graalpython/patches/'

PATCHES_URL = os.environ.get('PIP_GRAALPY_PATCHES_URL', DEFAULT_PATCHES_URL)
DISABLED_PATCHES_URL = 'disabled'
DISABLE_PATCHING = os.environ.get('PIP_GRAALPY_DISABLE_PATCHING', '').lower() in ('true', '1')
DISABLE_VERSION_SELECTION = os.environ.get('PIP_GRAALPY_DISABLE_VERSION_SELECTION', '').lower() in ('true', '1')

GRAALPY_VERSION = os.environ.get('TEST_PIP_GRAALPY_VERSION', __graalpython__.get_graalvm_version())

logger = logging.getLogger(__name__)


def canonicalize_name(name):
    return re.sub(r"[-_.]+", "-", name).lower()


def specifier_contains(specifier, version):
    try:
        from pip._vendor.packaging.specifiers import SpecifierSet
    except ImportError:
        try:
            from packaging.specifiers import SpecifierSet
        except ImportError:
            return any(
                spec.strip().removeprefix("==").strip() == version
                for spec in specifier.split(",")
                if spec.strip().startswith("==")
            )
    return SpecifierSet(specifier).contains(version)


def url_for_file(patches_url, filename):
    scheme, netloc, path, params, query, fragment = urlparse(patches_url)
    path = urljoin(path, filename)
    return urlunparse((scheme, netloc, path, params, query, fragment))


class RepositoryException(Exception):
    pass


class RepositoryNotFound(RepositoryException):
    pass


class AbstractPatchRepository(metaclass=abc.ABCMeta):
    def __init__(self, metadata: dict):
        self._repository = metadata

    @staticmethod
    def metadata_from_string(metadata_content) -> dict:
        try:
            parsed_metadata = tomli.loads(metadata_content)
            return {canonicalize_name(name): data for name, data in parsed_metadata.items()}
        except TOMLDecodeError as e:
            raise RepositoryException(f"'{METADATA_FILENAME}' cannot be parsed: {e}")

    def get_rules(self, name):
        if metadata := self._repository.get(canonicalize_name(name)):
            return metadata.get('rules')

    def get_add_sources(self, name):
        if metadata := self._repository.get(canonicalize_name(name)):
            return metadata.get('add-sources')

    def get_priority_for_version(self, name, version):
        if rules := self.get_rules(name):
            for rule in rules:
                if self.rule_matches_version(rule, version):
                    return rule.get('install-priority', 1)
        return 0

    @staticmethod
    def rule_matches_version(rule, version):
        return not rule.get('version') or specifier_contains(rule['version'], version)

    def get_suggested_version_specs(self, name):
        versions = set()
        if rules := self.get_rules(name):
            for rule in rules:
                if 'patch' in rule and rule.get('install-priority', 1) > 0 and (version := rule.get('version')):
                    versions.add(version)
        return versions

    def get_matching_rule(self, name, requested_version, dist_type):
        if metadata := self.get_rules(name):
            for rule in metadata:
                if rule.get('dist-type', dist_type) != dist_type:
                    continue
                if not self.rule_matches_version(rule, requested_version):
                    continue
                return rule

    @abc.abstractmethod
    def resolve_patch(self, patch_name: str):
        pass


class EmptyRepository(AbstractPatchRepository):
    def __init__(self):
        super().__init__({})

    def resolve_patch(self, patch_name: str):
        raise AssertionError("Invalid call")


class LocalPatchRepository(AbstractPatchRepository):
    def __init__(self, patches_path: Path, repository_data: dict):
        super().__init__(repository_data)
        self.patches_path = patches_path

    @classmethod
    def from_path(cls, patches_path: Path):
        try:
            with open(patches_path / METADATA_FILENAME) as f:
                metadata_content = f.read()
        except FileNotFoundError:
            raise RepositoryNotFound(f"'{METADATA_FILENAME}' does not exist")
        except OSError as e:
            raise RepositoryException(f"'{METADATA_FILENAME}' cannot be read: {e}")
        return cls(patches_path, cls.metadata_from_string(metadata_content))

    @contextmanager
    def resolve_patch(self, patch_name: str):
        yield self.patches_path / patch_name


class RemotePatchRepository(AbstractPatchRepository):
    def __init__(self, patches_url: str, repository_data: dict):
        super().__init__(repository_data)
        self.patches_url = patches_url

    @staticmethod
    def get_session():
        try:
            from pip._internal.cli.index_command import _GRAALPY_SESSION
            if _GRAALPY_SESSION:
                return _GRAALPY_SESSION
        except ImportError:
            pass
        from pip._vendor import requests
        return requests.Session()

    @classmethod
    def from_url(cls, patches_url: str):
        try:
            url = url_for_file(patches_url, METADATA_FILENAME)
            response = cls.get_session().get(url)
            if response.status_code == 404:
                raise RepositoryNotFound(f"'{METADATA_FILENAME}' not found")
            response.raise_for_status()
            if not response.encoding:
                response.encoding = 'utf-8'
            metadata_content = response.text
        except Exception as e:
            raise RepositoryException(f"'{METADATA_FILENAME}' cannot be retrieved': {e}")
        return cls(patches_url, cls.metadata_from_string(metadata_content))

    @contextmanager
    def resolve_patch(self, patch_name: str):
        from pip._vendor import requests

        try:
            response = self.get_session().get(url_for_file(self.patches_url, patch_name))
            response.raise_for_status()
        except requests.RequestException as e:
            logger.warning("Failed to download GraalPy patch '%s': %s", patch_name, e)
            yield None
        else:
            with tempfile.TemporaryDirectory() as tempdir:
                if not response.encoding:
                    response.encoding = 'utf-8'
                patch_file = Path(tempdir) / patch_name
                with open(patch_file, 'w') as f:
                    f.write(response.text)
                yield patch_file


__PATCH_REPOSITORY = None


def repository_from_url_or_path(url_or_path):
    if '://' not in url_or_path:
        return LocalPatchRepository.from_path(Path(url_or_path))
    elif url_or_path.startswith('file:'):
        patches_path = Path(url2pathname(urlparse(url_or_path).path))
        return LocalPatchRepository.from_path(patches_path)
    else:
        patches_url = url_or_path
        if not patches_url.endswith('/'):
            patches_url += '/'
        return RemotePatchRepository.from_url(patches_url)


def create_patch_repository(patches_url):
    if patches_url and VERSION_PARAMETER in patches_url:
        if not GRAALPY_VERSION.endswith('-dev'):
            patches_url = patches_url.replace(VERSION_PARAMETER, GRAALPY_VERSION)
        else:
            logger.debug("Skipping versioned GraalPy patch repository on snapshot build")
            patches_url = None
    if patches_url and patches_url != DISABLED_PATCHES_URL:
        logger.info(
            "Loading GraalPy post-release patch repository from %s. "
            "This can be controlled with PIP_GRAALPY_PATCHES_URL environment variable. Set to '%s' to disable",
            patches_url, DISABLED_PATCHES_URL)
        try:
            return repository_from_url_or_path(patches_url)
        except RepositoryException as e:
            if patches_url == DEFAULT_PATCHES_URL and isinstance(e, RepositoryNotFound):
                logger.info("No post-release patch repository published yet")
            else:
                logger.warning("Failed to load GraalPy patch repository: %s", e)
                logger.warning("Falling back to bundled GraalPy patch repository")
    try:
        return LocalPatchRepository.from_path(DEFAULT_PATCHES_PATH)
    except RepositoryException as e:
        logger.warning("Failed to load internal GraalPy patch repository: %s", e)
    return EmptyRepository()


def get_patch_repository():
    global __PATCH_REPOSITORY
    if not __PATCH_REPOSITORY:
        __PATCH_REPOSITORY = create_patch_repository(PATCHES_URL)
    return __PATCH_REPOSITORY


def apply_graalpy_patches(filename, location, warn_suggested_versions=False):
    """
    Applies any GraalPy patches to package extracted from 'filename' into 'location'.
    Note that 'location' must be the parent directory of the package directory itself.
    For example: /path/to/site-package and not /path/to/site-packages/mypackage.
    """
    if DISABLE_PATCHING:
        return

    # we expect filename to be something like "pytest-5.4.2-py3-none-any.whl"
    archive_name = os.path.basename(filename)
    if archive_name.endswith('.whl'):
        name_ver_match = re.match(
            r"^(?P<name>[^-]+)-(?P<version>[^-]+)(?:-(?P<build_tag>[^-]+))?-(?P<python_tag>[^-]+)-(?P<abi_tag>[^-]+)-(?P<platform_tag>[^-]+)\.(?P<suffix>whl)$",
            archive_name,
            re.I,
        )
    else:
        name_ver_match = re.match(
            r"^(?P<name>.*)-(?P<version>[^-]+)\.(?P<suffix>tar\.gz|tar|zip)$",
            archive_name,
            re.I,
        )
    if not name_ver_match:
        logger.warning(f"GraalPy warning: could not parse package name, version, or format from {archive_name!r}.\n"
                       "Could not determine if any GraalPy specific patches need to be applied.")
        return

    name = name_ver_match.group('name')
    version = name_ver_match.group('version')
    suffix = name_ver_match.group('suffix')
    is_wheel = suffix == "whl"

    if is_wheel and is_wheel_marked(filename):
        # We already processed it when building from source
        return

    import autopatch_capi
    import subprocess

    autopatch_capi.auto_patch_tree(location)

    logger.info(f"Looking for GraalPy patches for {name}")
    repository = get_patch_repository()

    if is_wheel:
        # patches intended for binary distribution:
        rule = repository.get_matching_rule(name, version, 'wheel')
    else:
        # patches intended for source distribution if applicable
        rule = repository.get_matching_rule(name, version, 'sdist')
        if not rule:
            rule = repository.get_matching_rule(name, version, 'wheel')
        if rule and (subdir := rule.get('subdir')):
            # we may need to change wd if we are actually patching a source distribution
            # with a patch intended for a binary distribution, because in the source
            # distribution the actual deployed sources may be in a subdirectory (typically "src")
            location = os.path.join(location, subdir)
    if rule:
        if patch := rule.get('patch'):
            with repository.resolve_patch(patch) as patch_path:
                if not patch_path:
                    return
                logger.info(f"Patching package {name} using {patch}")
                exe = '.exe' if os.name == 'nt' else ''
                try:
                    subprocess.run([f"patch{exe}", "-f", "-d", str(location), "-p1", "-i", str(patch_path)], check=True)
                except FileNotFoundError:
                    logger.warning(
                        "WARNING: GraalPy needs the 'patch' utility to apply compatibility patches. Please install it using your system's package manager.")
                except subprocess.CalledProcessError:
                    logger.warning(f"Applying GraalPy patch failed for {name}. The package may still work.")
                except Exception:
                    logger.exception(f"Failed to execute patch utility")
    elif version_specs := repository.get_suggested_version_specs(name):
        logger.info("We have patches to make this package work on GraalVM for some version(s).")
        logger.info("If installing or running fails, consider using one of the versions that we have patches for:")
        for version_spec in version_specs:
            logger.info(f'{name} {version_spec}')
        if warn_suggested_versions:
            from warnings import warn
            warn(
                "GraalPy has compatibility patches for "
                f"{name} version(s): {', '.join(sorted(version_specs))}. "
                "If installing or running this package fails, consider using one of those versions."
            )


def apply_graalpy_sort_order(sort_key_func):
    if DISABLE_VERSION_SELECTION:
        return sort_key_func

    def wrapper(self, candidate):
        default_sort_key = sort_key_func(self, candidate)
        priority = get_patch_repository().get_priority_for_version(candidate.name, str(candidate.version))
        return priority, default_sort_key

    return wrapper


class AddedSourceLink(_PipLink):
    def __init__(self, url: str, filename: str):
        if _PipLink is object:
            self._url = url
        else:
            super().__init__(url)
        self._filename = filename

    @property
    def filename(self) -> str:
        return self._filename

    @property
    def url(self) -> str:
        if _PipLink is object:
            return self._url
        return super().url

    def __str__(self):
        return self.url


def installation_candidate(name, version, link):
    try:
        from pip._internal.models.candidate import InstallationCandidate
        return InstallationCandidate(name=name, version=version, link=link)
    except ImportError:
        return SimpleNamespace(name=name, version=version, link=link)


def link_for_url(url):
    try:
        from pip._internal.models.link import Link
        return Link(url)
    except ImportError:
        return AddedSourceLink(url, os.path.basename(urlparse(url).path))


def get_graalpy_candidates(name):
    repository = get_patch_repository()
    candidates = []
    for add_source in repository.get_add_sources(name) or []:
        version = add_source['version']
        url = add_source['url']
        match = re.search(r'\.(tar\.(?:gz|bz2|xz)|zip|whl)$', urlparse(url).path)
        assert match, "Couldn't determine URL suffix"
        suffix = match.group(1)
        # We need to force the filename to match the usual convention, otherwise we won't find a patch
        link = AddedSourceLink(url, f'{name}-{version}.{suffix}')
        candidates.append(installation_candidate(name=name, version=version, link=link))
    if name == 'graalpy-virtualenv-seeder':
        link = link_for_url(Path(os.path.join(sys.base_prefix, 'graalpy_virtualenv_seeder')).resolve().as_uri())
        candidates.append(installation_candidate(name=name, version='0.0.1', link=link))
    return candidates


def mark_wheel(path):
    if DISABLE_PATCHING:
        return
    with zipfile.ZipFile(path, 'a') as z:
        dist_info = None
        for name in z.namelist():
            if m := re.match(r'([^/]+.dist-info)/', name):
                dist_info = m.group(1)
                break
        assert dist_info, "Cannot find .dist_info in built wheel"
        marker = f'{dist_info}/{MARKER_FILE_NAME}'
        with z.open(marker, 'w'):
            pass


def is_wheel_marked(path):
    with zipfile.ZipFile(path) as z:
        return any(re.match(rf'[^/]+.dist-info/{MARKER_FILE_NAME}$', f) for f in z.namelist())
