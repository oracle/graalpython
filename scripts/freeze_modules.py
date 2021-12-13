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

ROOT_DIR = os.path.dirname(os.path.dirname(__file__))
ROOT_DIR = os.path.abspath(ROOT_DIR)
print(ROOT_DIR)
#FROZEN_ONLY = os.path.join(ROOT_DIR, 'Tools', 'freeze', 'flag.py')

STDLIB_DIR = os.path.join(ROOT_DIR, 'graalpython', 'lib-python', '3')

# If FROZEN_MODULES_DIR or DEEPFROZEN_MODULES_DIR is changed then the
# .gitattributes and .gitignore files needs to be updated.
FROZEN_MODULES_DIR = os.path.join(ROOT_DIR, 'graalpython/com.oracle.graal.python.frozen/src/com/oracle/graal/python/frozen')
FROZEN_MODULES_FILE = os.path.join(FROZEN_MODULES_DIR, 'FrozenModules.java')

OS_PATH = 'ntpath' if os.name == 'nt' else 'posixpath'

FROZEN = [
    # See parse_frozen_spec() for the format.
    # In cases where the frozenid is duplicated, the first one is re-used.
    ('stdlib - startup, without site (python -S)', [
        'abc',
        #'codecs',
        # For now we do not freeze the encodings, due # to the noise all
        # those extra modules add to the text printed during the build.
        # (See https://github.com/python/cpython/pull/28398#pullrequestreview-756856469.)
        #'<encodings.*>',
        'io',
        ])
]
BOOTSTRAP = {
    'importlib._bootstrap',
    'importlib._bootstrap_external',
    'zipimport',
}

MAX_BYTES_PER_FILE = 6000 # TODO: Improve estimate here.

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

class FrozenSource(namedtuple('FrozenSource', 'id pyfile frozenfile')):

    @classmethod
    def from_id(cls, frozenid, pyfile=None):
        if not pyfile:
            pyfile = os.path.join(STDLIB_DIR, *frozenid.split('.')) + '.py'
            #assert os.path.exists(pyfile), (frozenid, pyfile)
        frozenfile = resolve_frozen_file(frozenid, FROZEN_MODULES_DIR)
        #deepfreezefile = resolve_frozen_file(frozenid, DEEPFROZEN_MODULES_DIR)
        return cls(frozenid, pyfile, frozenfile)

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
        # This matches what we do in Programs/_freeze_module.c:
        name = self.frozenid.replace('.', '_')
        return '_Py_M__' + name

    @property
    def ispkg(self):
        if not self.pyfile:
            return False
        elif self.frozenid.endswith('.__init__'):
            return False
        else:
            return os.path.basename(self.pyfile) == '__init__.py'


def resolve_frozen_file(frozenid, destdir):
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
    frozenfile = f'Frozen{frozenid.title()}.java'
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

def _write_bytes(file, bytes):
    byte_line_count = 0
    byte_total_count = 0
    file.write("    ")
    for byte in bytes:
        if byte > 127:
            byte -= 256
        file.write(f"{byte}, ")
        byte_line_count += 1
        if byte_line_count == 16:
            file.write("\n")
            file.write("    ")
            byte_line_count = 0

def write_byte_code_file(src_file, package_name, module_name, chunk_index, byte_it):
    with open(src_file, 'w') as frozen_file:
        frozen_file.write("/* Auto-generated by scipts/freeze_modules.py */\n\n")

        frozen_file.write(f"package com.oracle.graal.python.{package_name};\n\n")

        frozen_file.write(f"public final class Frozen{module_name.title()}{chunk_index}{{\n")
        frozen_file.write(f"public static final byte[] {module_name}ByteCode = {{\n")
        _write_bytes(frozen_file, byte_it)
        frozen_file.write("};\n")
        frozen_file.write(f"public static final int {module_name}ByteCodeSize = {module_name}ByteCode.length;\n")
        frozen_file.write("}")

#TODO: Improve to take and yield iterators directly?
def chunks(lst, n):
    for i in range(0, len(lst), n):
        yield lst[i:i + n]

def write_module_import_file(src_file, module_name, num_chunks):
    with open(src_file, 'w') as module_file:
        class_name = f"Frozen{module_name.title()}"
        module_package_name = f"com.oracle.graal.python.frozen.{module_name}"

        module_file.write("/* Auto-generated by scripts/freeze_modules.py */\n\n")
        module_file.write(f"package com.oracle.graal.python.frozen;\n\n")

        # import all chunks containing bytecode for the module
        for i in range(1, num_chunks):
            module_file.write(f"import {module_package_name}.{class_name}{i};\n")

        module_file.write(
        f"""public final class {class_name} {{
    public static final byte[][] {module_name}ByteCode = {{\n"""
        )

        for i in range(1, num_chunks):
            module_file.write(f"{class_name}{i}.{module_name}ByteCode,\n")

        module_file.write("};\n")

        module_file.write(f"public static final int {module_name}ByteCodeSize = {class_name}1.{module_name}ByteCodeSize")
        for i in range(2, num_chunks):
            module_file.write(f"\n+ {class_name}{i}.{module_name}ByteCodeSize")

        module_file.write("; \n }")

def freeze_module(src):
    with open(src.pyfile, 'r') as src_file:
        code_obj = compile(src_file.read(), src.id, "exec")
        bytecode = marshal.dumps(code_obj)

        if len(bytecode) < MAX_BYTES_PER_FILE:
            write_byte_code_file(src.frozenfile, "frozen", src.id, "", bytecode)
        else:
            # module byte code must be split into multiple files
            frozen_package = os.path.join(os.path.dirname(src.frozenfile), src.id)
            if not os.path.isdir(frozen_package):
                os.mkdir(frozen_package)

            num_chunks = 1
            for index, chunk in enumerate(chunks(list(bytecode), MAX_BYTES_PER_FILE), 1):
                chunk_srcfile = os.path.join(frozen_package, f"Frozen{src.id.title()}{index}.java")
                write_byte_code_file(chunk_srcfile, f"frozen.{src.id}", src.id, index, chunk)
                num_chunks += 1

            write_module_import_file(src.frozenfile, src.id, num_chunks)

def write_frozen_module_file(file, modules):
    header = """package com.oracle.graal.python.frozen;

                import java.util.HashMap;
                import java.util.Map;


                public final class FrozenModules {
                    public static Map<String, PythonFrozenModule> frozenModules = createFrozenModulesMap();

                    private static Map<String, PythonFrozenModule> createFrozenModulesMap() {
                        Map<String, PythonFrozenModule> frozenModules = new HashMap<String, PythonFrozenModule>();\n"""

    footer = """        return frozenModules;
                    }
                }"""

    with open (file, 'w') as out_file:
        out_file.write(header)
        for module in modules:
            out_file.write(f'frozenModules.put("{module.name}", new PythonFrozenModule("{module.name}", Frozen{module.name.title()}.{module.name}ByteCode, Frozen{module.name.title()}.{module.name}ByteCodeSize));\n')
        out_file.write(footer)



def main():
    # create module specs
    modules = list(parse_frozen_specs())
    print(modules)
    for src in _iter_sources(modules):
        freeze_module(src)

    write_frozen_module_file(FROZEN_MODULES_FILE, modules)


if __name__ == '__main__':
    argv = sys.argv[1:]
    if argv:
        sys.exit(f'ERROR: got unexpected args {argv}')
    main()
