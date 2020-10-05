# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
sys.path_hooks.append(zipimporter)


zipimporter.create_module = lambda self, spec: None
zipimporter.exec_module = lambda self, module: exec(self.get_code(module.__name__), module.__dict__)


class _ZipImportResourceReader:
    """Private class used to support ZipImport.get_resource_reader().

    This class is allowed to reference all the innards and private parts of
    the zipimporter.
    """
    _registered = False

    def __init__(self, zipimporter, fullname):
        self.zipimporter = zipimporter
        self.fullname = fullname

    def open_resource(self, resource):
        fullname_as_path = self.fullname.replace('.', '/')
        path = f'{fullname_as_path}/{resource}'
        from io import BytesIO
        try:
            return BytesIO(self.zipimporter.get_data(path))
        except OSError:
            raise FileNotFoundError(path)

    def resource_path(self, resource):
        # All resources are in the zip file, so there is no path to the file.
        # Raising FileNotFoundError tells the higher level API to extract the
        # binary data and create a temporary file.
        raise FileNotFoundError

    def is_resource(self, name):
        # Maybe we could do better, but if we can get the data, it's a
        # resource.  Otherwise it isn't.
        fullname_as_path = self.fullname.replace('.', '/')
        path = f'{fullname_as_path}/{name}'
        try:
            self.zipimporter.get_data(path)
        except OSError:
            return False
        return True

    def contents(self):
        # This is a bit convoluted, because fullname will be a module path,
        # but _files is a list of file names relative to the top of the
        # archive's namespace.  We want to compare file paths to find all the
        # names of things inside the module represented by fullname.  So we
        # turn the module path of fullname into a file path relative to the
        # top of the archive, and then we iterate through _files looking for
        # names inside that "directory".
        from pathlib import Path
        fullname_path = Path(self.zipimporter.get_filename(self.fullname))
        relative_path = fullname_path.relative_to(self.zipimporter.archive)
        # Don't forget that fullname names a package, so its path will include
        # __init__.py, which we want to ignore.
        assert relative_path.name == '__init__.py'
        package_path = relative_path.parent
        subdirs_seen = set()
        for filename in self.zipimporter._files:
            try:
                relative = Path(filename).relative_to(package_path)
            except ValueError:
                continue
            # If the path of the file (which is relative to the top of the zip
            # namespace), relative to the package given when the resource
            # reader was created, has a parent, then it's a name in a
            # subdirectory and thus we skip it.
            parent_name = relative.parent.name
            if len(parent_name) == 0:
                yield relative.name
            elif parent_name not in subdirs_seen:
                subdirs_seen.add(parent_name)
                yield parent_name

def _get_resource_reader(self, fullname):
    """Return the ResourceReader for a package in a zip file.

    If 'fullname' is a package within the zip file, return the
    'ResourceReader' object for the package.  Otherwise return None.
    """
    try:
        if not self.is_package(fullname):
            return None
    except ZipImportError:
        return None
    if not _ZipImportResourceReader._registered:
        from importlib.abc import ResourceReader
        ResourceReader.register(_ZipImportResourceReader)
        _ZipImportResourceReader._registered = True
    return _ZipImportResourceReader(self, fullname)

zipimporter.get_resource_reader = _get_resource_reader