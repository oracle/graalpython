# Copyright (c) 2018, 2019, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


import sys
import os
import unittest
import time
import importlib

import zipimport
from zipimport import ZipImportError

from test import support
from zipfile import ZipFile, ZipInfo, ZIP_STORED, ZIP_DEFLATED

test_src = """\
def get_name():
    return __name__
def get_file():
    return __file__
"""

ZIP_FILE_NAME = 'testzipfile.zip'
EGG_FILE_NAME = 'testeggfile.egg'
GR15813_FILE_NAME = 'testGR15813.zip'
DIR_PATH = os.path.dirname(os.path.realpath(__file__))
ZIP_PATH = os.path.join(DIR_PATH, ZIP_FILE_NAME)
ZIP_ABS_PATH = os.path.abspath(ZIP_PATH);
EGG_PATH = os.path.join(DIR_PATH, EGG_FILE_NAME)
EGG_ABS_PATH = os.path.abspath(EGG_PATH);
GR15813_PATH = os.path.join(DIR_PATH, GR15813_FILE_NAME)

class ZipImportBaseTestCase(unittest.TestCase):

    def setUp(self):
        zipimport._zip_directory_cache.clear()
        self.path = sys.path[:]
        self.meta_path = sys.meta_path[:]
        self.path_hooks = sys.path_hooks[:]
        sys.path_importer_cache.clear()
        self.modules_before = support.modules_setup()

    def tearDown(self):
        sys.path[:] = self.path
        sys.meta_path[:] = self.meta_path
        sys.path_hooks[:] = self.path_hooks
        sys.path_importer_cache.clear()
        support.modules_cleanup(*self.modules_before)

class BasicZipImportTests(ZipImportBaseTestCase):

    def setUp(self):
        ZipImportBaseTestCase.setUp(self)
        self.z = zipimport.zipimporter(ZIP_PATH)

    def test_zipimporter_attribute(self):
        self.assertTrue(self.z.prefix == "")
        self.assertTrue(self.z.archive == ZIP_ABS_PATH)
        self.assertTrue(type(self.z._files) is dict)
        self.assertTrue(self.z._files["MyTestModule.py"] is not None)
        self.assertTrue(self.z._files["empty.txt"] is not None)
        self.assertTrue(self.z._files["packageA/moduleC.py"] is not None)
        self.assertTrue(self.z._files["cesta/moduleA.py"] is not None)

    def test_create_zipimport_from_string(self):
        zipimport._zip_directory_cache.clear()
        z = zipimport.zipimporter(ZIP_PATH)
        self.assertTrue(zipimport._zip_directory_cache[ZIP_ABS_PATH] is not None)

    def test_create_zipimport_from_bytes(self):
        zipimport._zip_directory_cache.clear()
        a = bytes(ZIP_PATH, 'UTF-8')
        z = zipimport.zipimporter(a)
        self.assertTrue(zipimport._zip_directory_cache[os.path.abspath(ZIP_PATH)] is not None)

    def test_create_zipimport_from_pathlike(self):
        class MyPath():
            def __init__(self, path):
                self.value = path
            def __fspath__(self):
                return self.value

        zipimport._zip_directory_cache.clear()
        mp = MyPath(ZIP_PATH)
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            z = zipimport.zipimporter(mp)
            self.assertTrue(zipimport._zip_directory_cache[os.path.abspath(ZIP_PATH)] is not None)

    def test_zipimporter_find_module(self):
        self.assertTrue(self.z is self.z.find_module("MyTestModule"))
        self.assertTrue(self.z is self.z.find_module("packageA"))
        self.assertTrue(self.z is self.z.find_module("packageA/moduleC"))
        self.assertTrue(None is self.z.find_module("packageA.moduleC"))
        self.assertTrue(self.z is self.z.find_module("cesta/moduleA"))

    def test_zipimporter_get_code(self):
        self.assertTrue(self.z.get_code("MyTestModule").co_filename.endswith("MyTestModule.py"))
        self.assertTrue(self.z.get_code("packageA").co_filename.endswith("packageA/__init__.py"))
        self.assertTrue(self.z.get_code("packageA/moduleC").co_filename.endswith("packageA/moduleC.py"))
        self.assertTrue(self.z.get_code("cesta/moduleA").co_filename.endswith("cesta/moduleA.py"))
        self.assertRaises(ZipImportError, self.z.get_code, "wrongname")
        self.assertRaises(ZipImportError, self.z.get_code, "")

    def test_zipimporter_get_data(self):
        self.assertTrue(type(self.z.get_data("MyTestModule.py")) is bytes)
        self.assertTrue(type(self.z.get_data("packageA/moduleC.py")) is bytes)
        self.assertTrue(type(self.z.get_data("cesta/moduleA.py")) is bytes)
        self.assertRaises(OSError, self.z.get_data, "")
        self.assertRaises(OSError, self.z.get_data, "MyTestModule")
        self.assertRaises(OSError, self.z.get_data, "packageA")
        self.assertTrue(type(self.z.get_data(ZIP_ABS_PATH + "/MyTestModule.py")) is bytes)
        self.assertTrue(type(self.z.get_data(ZIP_ABS_PATH + "/packageA/moduleC.py")) is bytes)
        self.assertTrue(type(self.z.get_data(ZIP_ABS_PATH + "/cesta/moduleA.py")) is bytes)
        self.assertTrue(type(self.z.get_data(ZIP_ABS_PATH + "/read.me")) is bytes)
        self.assertTrue(type(self.z.get_data("empty.txt")) is bytes)
        self.assertRaises(OSError, self.z.get_data, "/empty.txt")

    def test_zipimporter_get_filename(self):
        self.assertEqual(self.z.get_filename("packageA"), ZIP_ABS_PATH + "/packageA/__init__.py")
        self.assertEqual(self.z.get_filename("MyTestModule"), ZIP_ABS_PATH + "/MyTestModule.py")
        self.assertRaises(ZipImportError, self.z.get_filename, "empty.txt")
        self.assertRaises(ZipImportError, self.z.get_filename, "empty")

    def test_zipimporter_get_source(self):
        self.assertTrue(type(self.z.get_source("MyTestModule")) is str)
        self.assertTrue(type(self.z.get_source("packageA")) is str)
        self.assertTrue(type(self.z.get_source("packageA/moduleC")) is str)
        self.assertRaises(ZipImportError, self.z.get_source, "packageA.moduleC")

    def test_zipimporter_is_package(self):
        self.assertTrue(self.z.is_package("packageA"))
        self.assertFalse(self.z.is_package("MyTestModule"))
        self.assertFalse(self.z.is_package("packageA/moduleC"))
        self.assertRaises(ZipImportError, self.z.is_package, "empty")
        self.assertRaises(ZipImportError, self.z.is_package, "cesta")
        self.assertRaises(ZipImportError, self.z.is_package, "packageA.moduleC")

    def test_zipimporter_load_module(self):
        self.assertTrue(self.z.load_module("MyTestModule").__loader__ is self.z)
        self.assertTrue(self.z.load_module("packageA").__loader__ is self.z)
        self.assertTrue(self.z.load_module("packageA/moduleC").__loader__ is self.z)
        self.assertTrue(self.z.load_module("cesta/moduleA").__loader__ is self.z)
        self.assertRaises(ZipImportError, self.z.load_module, "packageA.moduleC")

class ZipImportWithPrefixTests(ZipImportBaseTestCase):

    def setUp(self):
        ZipImportBaseTestCase.setUp(self)
        self.z = zipimport.zipimporter(ZIP_PATH + "/cesta")

    def tearDown(self):
        zipimport._zip_directory_cache.clear()

    def test_zipimporter_with_prefix_attribute(self):
        self.assertTrue(self.z.prefix == "cesta/")
        self.assertTrue(self.z.archive == ZIP_ABS_PATH)
        self.assertTrue(type(self.z._files) is dict)
        self.assertTrue(self.z._files["MyTestModule.py"] is not None)
        self.assertTrue(self.z._files["empty.txt"] is not None)
        self.assertTrue(self.z._files["packageA/moduleC.py"] is not None)
        self.assertTrue(self.z._files["cesta/moduleA.py"] is not None)

    def test_zipimporter_with_prefix_find_module(self):
        self.assertTrue(None is self.z.find_module("MyTestModule"))
        self.assertTrue(None is self.z.find_module("packageA"))
        self.assertTrue(None is self.z.find_module("packageA/moduleC"))
        self.assertTrue(None is self.z.find_module("packageA.moduleC"))
        self.assertTrue(self.z is self.z.find_module("moduleA"))

class ImportTests(ZipImportBaseTestCase):

    def setUp(self):
        ZipImportBaseTestCase.setUp(self)
        sys.path.insert(0, ZIP_PATH)

    def test_module_import(self):
        m = importlib.import_module("MyTestModule")
        self.assertTrue (m.get_file() == ZIP_ABS_PATH + "/MyTestModule.py")
        p = importlib.import_module("packageA.moduleC")
        self.assertTrue (p.get_file() == ZIP_ABS_PATH + "/packageA/moduleC.py")

class BasicEggImportTests(ZipImportBaseTestCase):

    def setUp(self):
        ZipImportBaseTestCase.setUp(self)
        self.z = zipimport.zipimporter(EGG_PATH)

    def test_zipimporter_egg(self):
        self.assertTrue(self.z.prefix == "")
        self.assertTrue(self.z.archive == EGG_ABS_PATH)
        self.assertTrue(type(self.z._files) is dict)
        self.assertTrue(self.z._files["data.bin"] is not None)
        self.assertTrue(self.z._files["read.me"] is not None)
        
    def test_egg_get_data(self):
        data = self.z.get_data("data.bin")
        self.assertTrue(type(data) is bytes)
        self.assertEqual(bytes(b'ahojPK\003\004ahoj'), data)
    
    def test_egg_get_readme(self):
        data = self.z.get_data("read.me")
        self.assertTrue(type(data) is bytes)
        self.assertEqual(bytes(b'Pokus\n'), data)

class GR15813ImportTests(ZipImportBaseTestCase):

    # don't edit the zip file !!!
    def setUp(self):
        ZipImportBaseTestCase.setUp(self)
        self.z = zipimport.zipimporter(GR15813_PATH)

    def test_zipimporter_gr_18813(self):
        self.assertTrue(self.z.prefix == "")
        self.assertTrue(type(self.z._files) is dict)
        self.assertTrue(6, len(self.z._files))
