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

import subprocess
import sys
import unittest


@unittest.skipUnless(sys.platform == "win32", "Windows only")
class WindowsVersionTests(unittest.TestCase):
    def test_getwindowsversion_matches_os(self):
        if sys.implementation.name == "graalpy" and __graalpython__.posix_module_backend() == "java":
            self.skipTest("The Java backend uses the Java OS version property")
        import ctypes
        from ctypes import wintypes

        class VersionInfo(ctypes.Structure):
            _fields_ = [
                ("size", wintypes.DWORD),
                ("major", wintypes.DWORD),
                ("minor", wintypes.DWORD),
                ("build", wintypes.DWORD),
                ("platform", wintypes.DWORD),
                ("service_pack", wintypes.WCHAR * 128),
                ("service_pack_major", wintypes.WORD),
                ("service_pack_minor", wintypes.WORD),
                ("suite_mask", wintypes.WORD),
                ("product_type", wintypes.BYTE),
                ("reserved", wintypes.BYTE),
            ]

        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        get_version = kernel32.GetVersionExW
        get_version.argtypes = [ctypes.POINTER(VersionInfo)]
        get_version.restype = wintypes.BOOL
        native_version = VersionInfo()
        native_version.size = ctypes.sizeof(native_version)
        self.assertTrue(get_version(ctypes.byref(native_version)))

        version = sys.getwindowsversion()
        expected = (native_version.major, native_version.minor, native_version.build)
        self.assertEqual(version[:3], expected)
        self.assertEqual(version.platform, native_version.platform)
        self.assertEqual(version.service_pack, native_version.service_pack)
        self.assertEqual(version.service_pack_major, native_version.service_pack_major)
        self.assertEqual(version.service_pack_minor, native_version.service_pack_minor)
        self.assertEqual(version.suite_mask, native_version.suite_mask)
        self.assertEqual(version.product_type, native_version.product_type)
        self.assertGreater(version.build, 0)

        class FixedFileInfo(ctypes.Structure):
            _fields_ = [(name, wintypes.DWORD) for name in (
                "signature", "struc_version", "file_version_ms", "file_version_ls",
                "product_version_ms", "product_version_ls", "file_flags_mask", "file_flags",
                "file_os", "file_type", "file_subtype", "file_date_ms", "file_date_ls",
            )]

        get_module_handle = kernel32.GetModuleHandleW
        get_module_handle.argtypes = [wintypes.LPCWSTR]
        get_module_handle.restype = wintypes.HMODULE
        get_module_filename = kernel32.GetModuleFileNameW
        get_module_filename.argtypes = [wintypes.HMODULE, wintypes.LPWSTR, wintypes.DWORD]
        get_module_filename.restype = wintypes.DWORD
        path = ctypes.create_unicode_buffer(260)
        self.assertTrue(get_module_filename(get_module_handle("kernel32.dll"), path, len(path)))
        version_dll = ctypes.WinDLL("version", use_last_error=True)
        get_size = version_dll.GetFileVersionInfoSizeW
        get_size.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(wintypes.DWORD)]
        get_size.restype = wintypes.DWORD
        size = get_size(path.value, None)
        self.assertGreater(size, 0)
        block = ctypes.create_string_buffer(size)
        get_info = version_dll.GetFileVersionInfoW
        get_info.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, wintypes.LPVOID]
        get_info.restype = wintypes.BOOL
        self.assertTrue(get_info(path.value, 0, size, block))
        query_value = version_dll.VerQueryValueW
        query_value.argtypes = [wintypes.LPCVOID, wintypes.LPCWSTR, ctypes.POINTER(ctypes.c_void_p),
                               ctypes.POINTER(wintypes.UINT)]
        query_value.restype = wintypes.BOOL
        value = ctypes.c_void_p()
        length = wintypes.UINT()
        self.assertTrue(query_value(block, "", ctypes.byref(value), ctypes.byref(length)))
        info = ctypes.cast(value, ctypes.POINTER(FixedFileInfo)).contents
        self.assertEqual(version.platform_version, (
            info.product_version_ms >> 16, info.product_version_ms & 0xffff, info.product_version_ls >> 16,
        ))

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy backend selection")
    def test_getwindowsversion_java_backend(self):
        for java_version, expected in [("10.0", (10, 0, 0)), ("10.0.12345", (10, 0, 12345))]:
            with self.subTest(java_version=java_version):
                result = subprocess.run(
                    [sys.executable, "--experimental-options", "--python.PosixModuleBackend=java",
                     "--vm.Dos.version=" + java_version, "-c",
                     "import sys; print(sys.getwindowsversion()[:3]); print(sys.getwindowsversion().platform_version)"],
                    capture_output=True, text=True, check=True,
                )
                self.assertEqual(result.stdout.splitlines(), [str(expected), str(expected)])
