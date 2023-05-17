# Copyright (c) 2021, 2024, Oracle and/or its affiliates.
# Copyright (C) 1996-2020 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

"""Freeze specified modules
"""

from collections import namedtuple
import marshal
import os
import ntpath
import posixpath
import sys
import argparse
import shutil

from _sha256 import sha256

FROZEN_ONLY = os.path.join(os.path.dirname(__file__), "flag.py")

STDLIB_DIR: str
FROZEN_MODULES_DIR: str

OS_PATH = 'ntpath' if os.name == 'nt' else 'posixpath'

# These are modules that get frozen.
TESTS_SECTION = 'Test module'
FROZEN = [
    # See parse_frozen_spec() for the format.
    # In cases where the frozenid is duplicated, the first one is re-used.
    ('import system', [
        # These frozen modules are necessary for bootstrapping
        # the import system.
        'importlib._bootstrap : _frozen_importlib',
        'importlib._bootstrap_external : _frozen_importlib_external',
        # This module is important because some Python builds rely
        # on a builtin zip file instead of a filesystem.
        'zipimport',
        ]),
    ('stdlib - startup, without site (python -S)', [
        'abc',
        'codecs',
        '<encodings.*>',
        'io',
        ]),
    ('stdlib - startup, with site', [
        '_py_abc',
        '_weakrefset',
        'types',
        'enum',
        'sre_constants',
        'sre_parse',
        'sre_compile',
        'operator',
        'keyword',
        'heapq',
        'reprlib',
        '<collections.*>',
        'functools',
        'copyreg',
        '<re.*>',
        'locale',
        'rlcompleter',
        '_collections_abc',
        '_sitebuiltins',
        'genericpath',
        'ntpath',
        'posixpath',
        # We must explicitly mark os.path as a frozen module
        # even though it will never be imported.
        f'{OS_PATH} : os.path',
        'os',
        'site',
        'stat',
        'datetime',
        'contextlib',
        'warnings',
        'inspect',
        ]),
    ('runpy - run module with -m', [
        "importlib.util",
        "importlib.machinery",
        "runpy",
    ]),
    (TESTS_SECTION, [
        '__hello__',
        '__hello__ : __hello_alias__',
        '__hello__ : <__phello_alias__>',
        '__hello__ : __phello_alias__.spam',
        '<__phello__.**.*>',
        f'frozen_only : __hello_only__ = {FROZEN_ONLY}',
        ]),
]
BOOTSTRAP = {
    'importlib._bootstrap',
    'importlib._bootstrap_external',
    'zipimport',
}


# add graalpython modules and core files
def add_graalpython_core():
    lib_graalpython = os.path.join(os.path.dirname(__file__), "..", "lib-graalpython")
    l = []
    for name in [
        "modules/_sysconfigdata",
    ]:
        modname = os.path.basename(name)
        modpath = os.path.join(lib_graalpython, f"{name}.py")
        l.append(f"{modname} : {modname} = {modpath}")
    for name in [
        "__graalpython__",
        "_interop_behavior",
        "_sre",
        "_struct",
        "_sysconfig",
        "_weakref",
        "builtins",
        "function",
        "java",
        "pip_hook",
        "unicodedata",
        "sulong_support",
    ]:
        modname = f"graalpy.{os.path.basename(name)}"
        modpath = os.path.join(lib_graalpython, f"{name}.py")
        l.append(f"{modname} : {modname} = {modpath}")
    FROZEN.append(("graalpython-lib", l))


add_graalpython_core()

#######################################
# platform-specific helpers

if os.path is posixpath:
    relpath_for_posix_display = os.path.relpath

    def relpath_for_windows_display(path, base):
        return ntpath.relpath(
            ntpath.join(*path.split(os.path.sep)),
            ntpath.join(*base.split(os.path.sep)),
        )

else:
    relpath_for_windows_display = ntpath.relpath

    def relpath_for_posix_display(path, base):
        return posixpath.relpath(
            posixpath.join(*path.split(os.path.sep)),
            posixpath.join(*base.split(os.path.sep)),
        )


#######################################
# specs

def parse_frozen_specs(suffix):
    seen = {}
    for section, specs in FROZEN:
        parsed = _parse_specs(specs, section, seen)
        for item in parsed:
            frozenid, pyfile, modname, ispkg, section = item
            try:
                source = seen[frozenid]
            except KeyError:
                source = FrozenSource.from_id(frozenid, suffix, pyfile)
                seen[frozenid] = source
            else:
                assert not pyfile or pyfile == source.pyfile, item
            yield FrozenModule(modname, ispkg, section, source)


def _parse_specs(specs, section, seen):
    for spec in specs:
        info, subs = _parse_spec(spec, seen, section)
        yield info
        for info in subs or ():
            yield info


def _parse_spec(spec, knownids=None, section=None):
    """Yield an info tuple for each module corresponding to the given spec.

    The info consists of: (frozenid, pyfile, modname, ispkg, section).

    Supported formats:

      frozenid
      frozenid : modname
      frozenid : modname = pyfile

    "frozenid" and "modname" must be valid module names (dot-separated
    identifiers).  If "modname" is not provided then "frozenid" is used.
    If "pyfile" is not provided then the filename of the module
    corresponding to "frozenid" is used.

    Angle brackets around a frozenid (e.g. '<encodings>") indicate
    it is a package.  This also means it must be an actual module
    (i.e. "pyfile" cannot have been provided).  Such values can have
    patterns to expand submodules:

      <encodings.*>    - also freeze all direct submodules
      <encodings.**.*> - also freeze the full submodule tree

    As with "frozenid", angle brackets around "modname" indicate
    it is a package.  However, in this case "pyfile" should not
    have been provided and patterns in "modname" are not supported.
    Also, if "modname" has brackets then "frozenid" should not,
    and "pyfile" should have been provided..
    """
    frozenid, _, remainder = spec.partition(':')
    modname, _, pyfile = remainder.partition('=')
    frozenid = frozenid.strip()
    modname = modname.strip()
    pyfile = pyfile.strip()

    submodules = None
    if modname.startswith('<') and modname.endswith('>'):
        assert check_modname(frozenid), spec
        modname = modname[1:-1]
        assert check_modname(modname), spec
        if frozenid in knownids:
            pass
        elif pyfile:
            assert not os.path.isdir(pyfile), spec
        else:
            pyfile = _resolve_module(frozenid, ispkg=False)
        ispkg = True
    elif pyfile:
        assert check_modname(frozenid), spec
        assert not knownids or frozenid not in knownids, spec
        assert check_modname(modname), spec
        assert not os.path.isdir(pyfile), spec
        ispkg = False
    elif knownids and frozenid in knownids:
        assert check_modname(frozenid), spec
        assert check_modname(modname), spec
        ispkg = False
    else:
        assert not modname or check_modname(modname), spec
        resolved = iter(resolve_modules(frozenid))
        frozenid, pyfile, ispkg = next(resolved)
        if not modname:
            modname = frozenid
        if ispkg:
            pkgid = frozenid
            pkgname = modname
            pkgfiles = {pyfile: pkgid}
            def iter_subs():
                for frozenid, pyfile, ispkg in resolved:
                    if pkgname:
                        modname = frozenid.replace(pkgid, pkgname, 1)
                    else:
                        modname = frozenid
                    if pyfile:
                        if pyfile in pkgfiles:
                            frozenid = pkgfiles[pyfile]
                            pyfile = None
                        elif ispkg:
                            pkgfiles[pyfile] = frozenid
                    yield frozenid, pyfile, modname, ispkg, section
            submodules = iter_subs()

    info = (frozenid, pyfile or None, modname, ispkg, section)
    return info, submodules


#######################################
# frozen source files

class FrozenSource(namedtuple('FrozenSource', 'id pyfile frozenfile deepfreezefile')):

    @classmethod
    def from_id(cls, frozenid, suffix, pyfile=None):
        if not pyfile:
            pyfile = os.path.join(STDLIB_DIR, *frozenid.split('.')) + '.py'
            #assert os.path.exists(pyfile), (frozenid, pyfile)
        frozenfile = resolve_frozen_file(frozenid, FROZEN_MODULES_DIR, suffix)
        return cls(frozenid, pyfile, frozenfile, STDLIB_DIR)

    @classmethod
    def resolve_symbol(cls, frozen_id):
        return frozen_id.replace(".", "_").upper()

    @property
    def frozenid(self):
        return self.id

    @property
    def modname(self):
        if self.pyfile.startswith(STDLIB_DIR):
            return self.id
        return None

    @property
    def symbol(self):
        # This matches the name we assign for our Java files
        return self.resolve_symbol(self.frozenid)

    @property
    def ispkg(self):
        if not self.pyfile:
            return False
        elif self.frozenid.endswith('.__init__'):
            return False
        else:
            return os.path.basename(self.pyfile) == '__init__.py'

    @property
    def isbootstrap(self):
        return self.id in BOOTSTRAP


def resolve_frozen_file(frozenid, destdir, suffix):
    """Return the filename corresponding to the given frozen ID.

    For stdlib modules the ID will always be the full name
    of the source module.
    """
    if not isinstance(frozenid, str):
        try:
            frozenid = frozenid.frozenid
        except AttributeError:
            raise ValueError(f'unsupported frozenid {frozenid!r}')
    # We use a consistent naming convention for all frozen modules.
    frozen_symbol = FrozenSource.resolve_symbol(frozenid)
    frozenfile = f"Frozen{frozen_symbol}.{suffix}"

    if not destdir:
        return frozenfile
    return os.path.join(destdir, frozenfile)


#######################################
# frozen modules

class FrozenModule(namedtuple('FrozenModule', 'name ispkg section source')):

    def __getattr__(self, name):
        return getattr(self.source, name)

    @property
    def modname(self):
        return self.name

    @property
    def orig(self):
        return self.source.modname

    @property
    def isalias(self):
        orig = self.source.modname
        if not orig:
            return True
        return self.name != orig

    def summarize(self):
        source = self.source.modname
        if source:
            source = f'<{source}>'
        else:
            source = relpath_for_posix_display(self.pyfile, ROOT_DIR)
        return {
            'module': self.name,
            'ispkg': self.ispkg,
            'source': source,
            'frozen': os.path.basename(self.frozenfile),
            'checksum': _get_checksum(self.frozenfile),
        }


def _iter_sources(modules):
    seen = set()
    for mod in modules:
        if mod.source not in seen:
            yield mod.source
            seen.add(mod.source)


#######################################
# generic helpers

def _get_checksum(filename):
    with open(filename, "rb") as infile:
        contents = infile.read()
    m = sha256()
    m.update(contents)
    return m.hexdigest()


def resolve_modules(modname, pyfile=None):
    if modname.startswith('<') and modname.endswith('>'):
        if pyfile:
            assert os.path.isdir(pyfile) or os.path.basename(pyfile) == '__init__.py', pyfile
        ispkg = True
        modname = modname[1:-1]
        rawname = modname
        # For now, we only expect match patterns at the end of the name.
        _modname, sep, match = modname.rpartition('.')
        if sep:
            if _modname.endswith('.**'):
                modname = _modname[:-3]
                match = f'**.{match}'
            elif match and not match.isidentifier():
                modname = _modname
            # Otherwise it's a plain name so we leave it alone.
        else:
            match = None
    else:
        ispkg = False
        rawname = modname
        match = None

    if not check_modname(modname):
        raise ValueError(f'not a valid module name ({rawname})')

    if not pyfile:
        pyfile = _resolve_module(modname, ispkg=ispkg)
    elif os.path.isdir(pyfile):
        pyfile = _resolve_module(modname, pyfile, ispkg)
    yield modname, pyfile, ispkg

    if match:
        pkgdir = os.path.dirname(pyfile)
        yield from iter_submodules(modname, pkgdir, match)


def check_modname(modname):
    return all(n.isidentifier() for n in modname.split('.'))


def iter_submodules(pkgname, pkgdir=None, match='*'):
    if not pkgdir:
        pkgdir = os.path.join(STDLIB_DIR, *pkgname.split('.'))
    if not match:
        match = '**.*'
    match_modname = _resolve_modname_matcher(match, pkgdir)

    def _iter_submodules(pkgname, pkgdir):
        for entry in sorted(os.scandir(pkgdir), key=lambda e: e.name):
            matched, recursive = match_modname(entry.name)
            if not matched:
                continue
            modname = f'{pkgname}.{entry.name}'
            if modname.endswith('.py'):
                yield modname[:-3], entry.path, False
            elif entry.is_dir():
                pyfile = os.path.join(entry.path, '__init__.py')
                # We ignore namespace packages.
                if os.path.exists(pyfile):
                    yield modname, pyfile, True
                    if recursive:
                        yield from _iter_submodules(modname, entry.path)

    return _iter_submodules(pkgname, pkgdir)


def _resolve_modname_matcher(match, rootdir=None):
    if isinstance(match, str):
        if match.startswith('**.'):
            recursive = True
            pat = match[3:]
            assert match
        else:
            recursive = False
            pat = match

        if pat == '*':
            def match_modname(modname):
                return True, recursive
        else:
            raise NotImplementedError(match)
    elif callable(match):
        match_modname = match(rootdir)
    else:
        raise ValueError(f'unsupported matcher {match!r}')
    return match_modname


def _resolve_module(modname, pathentry=None, ispkg=False):
    if pathentry is None:
        pathentry = STDLIB_DIR
    assert pathentry, pathentry
    pathentry = os.path.normpath(pathentry)
    assert os.path.isabs(pathentry)
    if ispkg:
        return os.path.join(pathentry, *modname.split('.'), '__init__.py')
    return os.path.join(pathentry, *modname.split('.')) + '.py'


def lower_camel_case(str):
    return str[0].lower() + str[1:] if str[1:] else ""


#############################################
# write frozen files

FROZEN_MODULES_HEADER = """/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.module;

import com.oracle.graal.python.builtins.PythonOS;

public final class FrozenModules {"""


def freeze_module(src):
    with open(src.pyfile, "r", encoding="utf-8") as src_file, open(src.frozenfile, "wb") as binary_file:
        code_obj = compile(src_file.read(), f"<frozen {src.id}>", "exec")
        marshal.dump(code_obj, binary_file)


def write_frozen_modules_map(out_file, modules):
    out_file.write("    private static final class Map {\n")
    for module in modules:
        if (
            not module.isalias
            or not module.orig
            or not any(module.orig == m.orig for m in modules if m != module)
        ):
            ispkg = "true" if module.ispkg else "false"
            orig = f'"{module.orig}"' if module.orig else "null"
            out_file.write(
                f'        private static final PythonFrozenModule {module.symbol} = new PythonFrozenModule("{module.symbol}", {orig}, {ispkg});\n'
            )
    out_file.write("    }\n")


def write_frozen_lookup(out_file, modules):
    out_file.write("    public static final PythonFrozenModule lookup(String name) {\n")
    out_file.write("        switch (name) {\n")
    for module in modules:
        if module.source and (module.source.ispkg != module.ispkg):
            out_file.write(f'            case "{module.name}":\n')
            out_file.write(
                f'                return Map.{module.symbol}.asPackage({"true" if module.ispkg else "false"});\n'
            )
        elif module.name == "os.path": # Special case for os.path
            out_file.write(u'            case "os.path":\n')
            out_file.write(u'                return PythonOS.getPythonOS() != PythonOS.PLATFORM_WIN32 ? Map.POSIXPATH : Map.NTPATH;\n')
        else:
            out_file.write(f'            case "{module.name}":\n')
            out_file.write(f"                return Map.{module.symbol};\n")
    out_file.write("            default:\n")
    out_file.write("                return null;\n")
    out_file.write("        }\n")
    out_file.write("    }\n")


def write_frozen_module_file(file, modules):
    if os.path.exists(file):
        with open(file, "r", encoding="utf-8") as f:
            content = f.read()
        stat_result = os.stat(file)
        atime, mtime = stat_result.st_atime, stat_result.st_mtime
    else:
        content = None
    os.makedirs(os.path.dirname(file), exist_ok=True)
    with open(file, "w", encoding="utf-8") as out_file:
        out_file.write(FROZEN_MODULES_HEADER)
        out_file.write("\n\n")
        write_frozen_modules_map(out_file, modules)
        out_file.write("\n")
        write_frozen_lookup(out_file, modules)
        out_file.write("}\n")
    with open(file, "r", encoding="utf-8") as f:
        new_content = f.read()
    if new_content == content:
        # set mtime to the old one, if we didn't change anything
        print(f"{file} not modified")
        os.utime(file, (atime, mtime))
    else:
        print(f"{file} modified, rebuild needed!")
        sys.exit(1)


def add_tabs(str, number):
    lines = str.splitlines()
    tabbed_lines = []
    for line in lines:
        tabs = "\t" * number
        tabbed_lines.append(f"{tabs}{line}")

    return "\n".join(tabbed_lines)


def main():

    parser = argparse.ArgumentParser()
    parser.add_argument("--python-lib", required=True)
    parser.add_argument("--binary-dir", required=True)
    parser.add_argument("--sources-dir", required=True)
    parsed_args = parser.parse_args()

    global STDLIB_DIR, FROZEN_MODULES_DIR
    STDLIB_DIR = os.path.abspath(parsed_args.python_lib)
    FROZEN_MODULES_DIR = os.path.abspath(parsed_args.binary_dir)

    if __graalpython__.is_bytecode_dsl_interpreter:
        suffix = "bin_dsl"
        assert os.path.isdir(parsed_args.binary_dir), "Frozen modules for the DSL should be built after the manual bytecode interpreter."
    else:
        suffix = "bin"
        shutil.rmtree(parsed_args.binary_dir, ignore_errors=True)
        os.makedirs(parsed_args.binary_dir)

    # create module specs
    modules = list(parse_frozen_specs(suffix))

    # write frozen module binary files containing the byte code and class files
    # used for importing the binary files
    for src in _iter_sources(modules):
        freeze_module(src)

    # write frozen modules class used for storing frozen modules byte code arrays
    write_frozen_module_file(
        os.path.join(parsed_args.sources_dir, "FrozenModules.java"), modules
    )


if __name__ == '__main__':
    main()
