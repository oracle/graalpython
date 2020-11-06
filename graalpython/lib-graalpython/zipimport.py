# Copyright (c) 2020, Oracle and/or its affiliates.
# Copyright (C) 1996-2020 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

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