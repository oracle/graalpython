"""Freeze modules and regen related files (e.g. Python/frozen.c).

See the notes at the top of Python/frozen.c for more info.
"""

from collections import namedtuple
import hashlib
import marshal
import ntpath
import os
import posixpath
import sys
import textwrap

ROOT_DIR = os.path.dirname(os.path.dirname(__file__))
ROOT_DIR = os.path.abspath(ROOT_DIR)
#FROZEN_ONLY = os.path.join(ROOT_DIR, 'Tools', 'freeze', 'flag.py') TODO: Enable tests.

STDLIB_DIR = os.path.join(ROOT_DIR, 'graalpython', 'lib-python', '3')

# If FROZEN_MODULES_DIR or DEEPFROZEN_MODULES_DIR is changed then the
# .gitattributes and .gitignore files needs to be updated.
FROZEN_MODULES_DIR = os.path.join(ROOT_DIR, 'graalpython/com.oracle.graal.python.frozen/src/com/oracle/graal/python/frozen/modules')
FROZEN_MODULES_FILE = os.path.join(FROZEN_MODULES_DIR, 'FrozenModules.java')

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
        # For now we do not freeze the encodings, due # to the noise all
        # those extra modules add to the text printed during the build.
        # (See https://github.com/python/cpython/pull/28398#pullrequestreview-756856469.)
        # '<encodings.*>',
        'io',
        ]),
    ('stdlib - startup, with site', [
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
        ]),
        (TESTS_SECTION, [
                '__hello__',
#                 TODO: enable further tests.
#                 '__hello__ : __hello_alias__',
#                 '__hello__ : <__phello_alias__>',
#                 '__hello__ : __phello_alias__.spam',
#                 '<__phello__.**.*>',
#                 f'frozen_only : __hello_only__ = {FROZEN_ONLY}',
                ]),
]
BOOTSTRAP = {
    'importlib._bootstrap',
    'importlib._bootstrap_external',
    'zipimport',
}

#######################################
# specs

def parse_frozen_specs():
    seen = {}
    for section, specs in FROZEN:
        parsed = _parse_specs(specs, section, seen)
        for item in parsed:
            frozenid, pyfile, modname, ispkg, section = item
            print(frozenid, pyfile, modname, ispkg, section)
            try:
                source = seen[frozenid]
            except KeyError:
                source = FrozenSource.from_id(frozenid, pyfile)
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

class FrozenSource(namedtuple('FrozenSource', 'id pyfile frozenfile binaryfile')):

    @classmethod
    def from_id(cls, frozenid, pyfile=None):
        if not pyfile:
            pyfile = os.path.join(STDLIB_DIR, *frozenid.split('.')) + '.py'
            assert os.path.exists(pyfile), (frozenid, pyfile)
        frozenfile, binaryfile = resolve_frozen_files(frozenid, FROZEN_MODULES_DIR)
        return cls(frozenid, pyfile, frozenfile, binaryfile)

    @classmethod
    def resolve_symbol(cls, frozen_id):
        return frozen_id.replace('.', ' ').replace('_', ' ').title().replace(' ', '')

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
        #This matches the name we assign for our Java files
        return self.resolve_symbol(self.frozenid)

    @property
    def ispkg(self):
        if not self.pyfile:
            return False
        elif self.frozenid.endswith('.__init__'):
            return False
        else:
            return os.path.basename(self.pyfile) == '__init__.py'


def resolve_frozen_files(frozenid, destdir):
    """Return the filenames corresponding to the given frozen ID.

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
    frozenfile = f'Frozen{frozen_symbol}.java'
    binaryfile = f'Frozen{frozen_symbol}.bin'

    if not destdir:
        return frozenfile, binaryfile
    return os.path.join(destdir, frozenfile), os.path.join(destdir, binaryfile)




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
    m = hashlib.sha256()
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


def _resolve_module(modname, pathentry=STDLIB_DIR, ispkg=False):
    assert pathentry, pathentry
    pathentry = os.path.normpath(pathentry)
    assert os.path.isabs(pathentry)
    if ispkg:
        return os.path.join(pathentry, *modname.split('.'), '__init__.py')
    return os.path.join(pathentry, *modname.split('.')) + '.py'

def lower_camel_case(str):
    return str[0].lower() + str[1:] if str[1:] else ''

# Adapted from PEP 257: strips a uniform amount of indentation from the lines of the multiline string.
# This allows us to use multiline strings with pythonic identation which produce the same identation in the written file
def trim(str):
    if not str:
        return ''
    # Convert tabs to spaces (following the normal Python rules)
    # and split into a list of lines:
    lines = str.expandtabs().splitlines()
    # Determine minimum indentation (first line doesn't count):
    indent = sys.maxsize
    for line in lines:

        stripped = line.lstrip()
        if stripped:
            indent = min(indent, len(line) - len(stripped))
    # Remove indentation (first line is special):
    trimmed = []
    if indent < sys.maxsize:
        for line in lines:
            trimmed.append(line[indent:].rstrip())
    # Strip off trailing and leading blank lines:
    while trimmed and not trimmed[-1]:
        trimmed.pop()
    while trimmed and not trimmed[0]:
        trimmed.pop(0)
    # Return a single string:
    return '\n'.join(trimmed)

#############################################
# write frozen files

COPYRIGHT_HEADER = """
    /* Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
    """

FROZEN_MODULE_FILE_HEADER = """
    // Checkstyle: stop
    // JaCoCo Exclude
    // @formatter:off
    // Generated from scripts/freeze_modules.py by GraalPython version 21.3

    package com.oracle.graal.python.frozen.modules;

    import java.io.IOException;
    """

FROZEN_MODULES_HEADER = """
    package com.oracle.graal.python.frozen.modules;

    import com.oracle.graal.python.frozen.PythonFrozenModule;

    import java.util.HashMap;
    import java.util.Map;


    public final class FrozenModules {
        public static Map<String, PythonFrozenModule> frozenModules = createFrozenModulesMap();
        public static Map<String, String>  frozenAliases = createFrozenAliasesMap();
    """

FROZEN_MODULES_MAP_FUNCTION_HEADER = """
        private static Map<String, PythonFrozenModule> createFrozenModulesMap() {
            Map<String, PythonFrozenModule> frozenModules = new HashMap<String, PythonFrozenModule>();\n
    """

FROZEN_MODULES_FOOTER = """
            return frozenModules;
        }
    """

FROZEN_ALIASES_FUNCTION_HEADER = """
        private static Map<String, String> createFrozenAliasesMap() {
            // alias, originalName
            Map<String, String> frozenAliases = new HashMap<String, String>();\n
    """

FROZEN_ALIASES_FUNCTION_FOOTER = """
            return frozenAliases;
        }
    }
    """

def freeze_module(src):
    with open(src.pyfile, 'r') as src_file, open(src.frozenfile, 'w') as frozen_file, open(src.binaryfile, 'wb') as binary_file:
        code_obj = compile(src_file.read(), src.id, "exec")
        marshal.dump(code_obj, binary_file)
        write_frozen_file(src, frozen_file)

def write_frozen_file(src, frozen_file):
    class_name = f"Frozen{src.symbol}"
    pkg = '-' if src.ispkg else ''
    class_definition = f"""
    public final class {class_name} {{
        public static final byte[] {lower_camel_case(src.symbol)}ByteCode = getByteCode();
        public static final int {lower_camel_case(src.symbol)}ByteCodeSize = {pkg}{lower_camel_case(src.symbol)}ByteCode.length;

        private static byte[] getByteCode() {{
           byte[] byteCode;
           try {{
               byteCode = {class_name}.class.getResourceAsStream("{class_name}.bin").readAllBytes();
           }} catch (NullPointerException | IOException e) {{
               byteCode = new byte[0];
           }}
           return byteCode;
        }}
    }}
    """

    frozen_file.write(trim(COPYRIGHT_HEADER))
    frozen_file.write('\n')
    frozen_file.write(trim(FROZEN_MODULE_FILE_HEADER))
    frozen_file.write('\n\n')
    frozen_file.write(trim(class_definition))

def write_frozen_modules_map(out_file, modules):
    out_file.write(add_tabs(trim(FROZEN_MODULES_MAP_FUNCTION_HEADER), 1))
    out_file.write('\n')
    for module in modules:
        out_file.write(f'\t\tfrozenModules.put("{module.frozenid}", new PythonFrozenModule("{module.symbol}", Frozen{module.symbol}.{lower_camel_case(module.symbol)}ByteCode, Frozen{module.symbol}.{lower_camel_case(module.symbol)}ByteCodeSize));\n')
    out_file.write(add_tabs(trim(FROZEN_MODULES_FOOTER), 1))

def write_frozen_aliases_map(out_file, modules):
    out_file.write(add_tabs(trim(FROZEN_ALIASES_FUNCTION_HEADER), 1))
    out_file.write('\n'),
    for module in modules:
        if module.isalias:
            out_file.write(f'\t\tfrozenAliases.put("{module.name}", "{module.id}");\n')
    out_file.write(trim(FROZEN_ALIASES_FUNCTION_FOOTER))

def write_frozen_module_file(file, modules):
    with open (file, 'w') as out_file:
        out_file.write(trim(COPYRIGHT_HEADER))
        out_file.write('\n')
        out_file.write(trim(FROZEN_MODULES_HEADER))
        out_file.write('\n\n')
        write_frozen_modules_map(out_file, modules)
        out_file.write('\n\n')
        write_frozen_aliases_map(out_file, modules)

def clean_frozen_modules_directory():
    for file in os.listdir(FROZEN_MODULES_DIR):
        os.remove(os.path.join(FROZEN_MODULES_DIR, file))

def add_tabs(str, number):
    lines = str.splitlines()
    tabbed_lines = []
    for line in lines:
        tabs = '\t' * number
        tabbed_lines.append(f"{tabs}{line}")

    return '\n'.join(tabbed_lines)

def main():
    # create module specs
    modules = list(parse_frozen_specs())

    clean_frozen_modules_directory()
    # write frozen module binary files containing the byte code and class files used for importing the binary files
    for src in _iter_sources(modules):
        freeze_module(src)

    # write frozen modules class used for storing frozen modules byte code arrays
    write_frozen_module_file(FROZEN_MODULES_FILE, modules)


if __name__ == '__main__':
    argv = sys.argv[1:]
    if argv:
        sys.exit(f'ERROR: got unexpected args {argv}')
    main()