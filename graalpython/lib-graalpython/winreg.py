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
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, STRICT LIABILITY, OR OTHERWISE,
# ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.

import sys


if sys.platform != "win32":
    raise ImportError("win32 only")


error = OSError

ERROR_SUCCESS = 0
ERROR_MORE_DATA = 234

KEY_QUERY_VALUE = 0x0001
KEY_SET_VALUE = 0x0002
KEY_CREATE_SUB_KEY = 0x0004
KEY_ENUMERATE_SUB_KEYS = 0x0008
KEY_NOTIFY = 0x0010
KEY_CREATE_LINK = 0x0020
KEY_WOW64_64KEY = 0x0100
KEY_WOW64_32KEY = 0x0200
KEY_WOW64_RES = 0x0300
READ_CONTROL = 0x00020000
STANDARD_RIGHTS_READ = READ_CONTROL
STANDARD_RIGHTS_WRITE = READ_CONTROL
STANDARD_RIGHTS_ALL = 0x001F0000
KEY_READ = STANDARD_RIGHTS_READ | KEY_QUERY_VALUE | KEY_ENUMERATE_SUB_KEYS | KEY_NOTIFY
KEY_WRITE = STANDARD_RIGHTS_WRITE | KEY_SET_VALUE | KEY_CREATE_SUB_KEY
KEY_EXECUTE = KEY_READ
KEY_ALL_ACCESS = (
    STANDARD_RIGHTS_ALL | KEY_QUERY_VALUE | KEY_SET_VALUE | KEY_CREATE_SUB_KEY | KEY_ENUMERATE_SUB_KEYS | KEY_NOTIFY |
    KEY_CREATE_LINK
)

REG_NONE = 0
REG_SZ = 1
REG_EXPAND_SZ = 2
REG_BINARY = 3
REG_DWORD = 4
REG_DWORD_LITTLE_ENDIAN = 4
REG_DWORD_BIG_ENDIAN = 5
REG_LINK = 6
REG_MULTI_SZ = 7
REG_RESOURCE_LIST = 8
REG_FULL_RESOURCE_DESCRIPTOR = 9
REG_RESOURCE_REQUIREMENTS_LIST = 10
REG_QWORD = 11
REG_QWORD_LITTLE_ENDIAN = 11

_POINTER_BITS = 64 if sys.maxsize > 2**32 else 32


def _predefined_hkey(value):
    if value & 0x80000000:
        value -= 1 << 32
    return value % (1 << _POINTER_BITS)


HKEY_CLASSES_ROOT = _predefined_hkey(0x80000000)
HKEY_CURRENT_USER = _predefined_hkey(0x80000001)
HKEY_LOCAL_MACHINE = _predefined_hkey(0x80000002)
HKEY_USERS = _predefined_hkey(0x80000003)
HKEY_PERFORMANCE_DATA = _predefined_hkey(0x80000004)
HKEY_CURRENT_CONFIG = _predefined_hkey(0x80000005)
HKEY_DYN_DATA = _predefined_hkey(0x80000006)

_ctypes = None
_wintypes = None
_advapi32 = None


def _native():
    global _ctypes, _wintypes, _advapi32
    if _advapi32 is not None:
        return _ctypes, _wintypes, _advapi32

    import ctypes
    from ctypes import wintypes

    advapi32 = ctypes.windll.advapi32
    advapi32.RegCloseKey.argtypes = [wintypes.HANDLE]
    advapi32.RegCloseKey.restype = wintypes.LONG
    advapi32.RegOpenKeyExW.argtypes = [
        wintypes.HANDLE,
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.HANDLE),
    ]
    advapi32.RegOpenKeyExW.restype = wintypes.LONG
    advapi32.RegConnectRegistryW.argtypes = [wintypes.LPCWSTR, wintypes.HANDLE, ctypes.POINTER(wintypes.HANDLE)]
    advapi32.RegConnectRegistryW.restype = wintypes.LONG
    advapi32.RegEnumKeyExW.argtypes = [
        wintypes.HANDLE,
        wintypes.DWORD,
        wintypes.LPWSTR,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.LPDWORD,
        wintypes.LPWSTR,
        wintypes.LPDWORD,
        ctypes.c_void_p,
    ]
    advapi32.RegEnumKeyExW.restype = wintypes.LONG
    advapi32.RegQueryValueExW.argtypes = [
        wintypes.HANDLE,
        wintypes.LPCWSTR,
        wintypes.LPDWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
        ctypes.POINTER(wintypes.DWORD),
    ]
    advapi32.RegQueryValueExW.restype = wintypes.LONG
    advapi32.RegEnumValueW.argtypes = [
        wintypes.HANDLE,
        wintypes.DWORD,
        wintypes.LPWSTR,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.LPDWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
        ctypes.POINTER(wintypes.DWORD),
    ]
    advapi32.RegEnumValueW.restype = wintypes.LONG
    advapi32.RegQueryInfoKeyW.restype = wintypes.LONG
    advapi32.RegFlushKey.argtypes = [wintypes.HANDLE]
    advapi32.RegFlushKey.restype = wintypes.LONG

    _ctypes = ctypes
    _wintypes = wintypes
    _advapi32 = advapi32
    return _ctypes, _wintypes, _advapi32


class HKEYType:
    def __init__(self, handle):
        self.handle = int(handle)
        self.closed = False

    def Close(self):
        CloseKey(self)

    def Detach(self):
        self._check_closed()
        handle = self.handle
        self.closed = True
        self.handle = 0
        return handle

    def _check_closed(self):
        if self.closed:
            raise ValueError("The object has been closed")

    def __enter__(self):
        self._check_closed()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.Close()

    def __int__(self):
        self._check_closed()
        return self.handle

    __index__ = __int__

    def __repr__(self):
        if self.closed:
            return "<PyHKEY:0x0>"
        return f"<PyHKEY:0x{self.handle:x}>"

    def __bool__(self):
        return not self.closed and self.handle != 0

    def __del__(self):
        if not self.closed and self.handle:
            try:
                CloseKey(self)
            except OSError:
                pass


def _winerror(code):
    ctypes, _, _ = _native()
    if hasattr(ctypes, "WinError"):
        return ctypes.WinError(code)
    return OSError(code, f"Windows error {code}")


def _raise_if_error(code):
    if code != ERROR_SUCCESS:
        raise _winerror(code)


def _handle(key):
    if isinstance(key, HKEYType):
        key._check_closed()
        return key.handle
    return int(key)


def _hkey_pointer(key):
    _, wintypes, _ = _native()
    return wintypes.HANDLE(_handle(key))


def CloseKey(key):
    _, wintypes, advapi32 = _native()
    if isinstance(key, HKEYType):
        if key.closed:
            return
        handle = key.handle
        key.closed = True
        key.handle = 0
    else:
        handle = _handle(key)
    _raise_if_error(advapi32.RegCloseKey(wintypes.HANDLE(handle)))


def OpenKey(key, sub_key, reserved=0, access=KEY_READ):
    ctypes, wintypes, advapi32 = _native()
    result = wintypes.HANDLE()
    sub_key = "" if sub_key is None else str(sub_key)
    code = advapi32.RegOpenKeyExW(
        _hkey_pointer(key),
        sub_key,
        wintypes.DWORD(reserved),
        wintypes.DWORD(access),
        ctypes.byref(result),
    )
    _raise_if_error(code)
    return HKEYType(result.value)


OpenKeyEx = OpenKey


def ConnectRegistry(computer_name, key):
    ctypes, wintypes, advapi32 = _native()
    result = wintypes.HANDLE()
    code = advapi32.RegConnectRegistryW(computer_name, _hkey_pointer(key), ctypes.byref(result))
    _raise_if_error(code)
    return HKEYType(result.value)


def EnumKey(key, index):
    ctypes, wintypes, advapi32 = _native()
    size = 256
    while True:
        name = ctypes.create_unicode_buffer(size + 1)
        name_size = wintypes.DWORD(size + 1)
        code = advapi32.RegEnumKeyExW(
            _hkey_pointer(key),
            wintypes.DWORD(index),
            name,
            ctypes.byref(name_size),
            None,
            None,
            None,
            None,
        )
        if code == ERROR_MORE_DATA:
            size *= 2
            continue
        _raise_if_error(code)
        return name.value


def _convert_value(regtype, data):
    if regtype in (REG_SZ, REG_EXPAND_SZ, REG_LINK):
        return data.decode("utf-16le", "surrogatepass").rstrip("\0")
    if regtype == REG_MULTI_SZ:
        value = data.decode("utf-16le", "surrogatepass").rstrip("\0")
        if not value:
            return []
        return value.split("\0")
    if regtype == REG_DWORD:
        return int.from_bytes(data[:4].ljust(4, b"\0"), "little")
    if regtype == REG_DWORD_BIG_ENDIAN:
        return int.from_bytes(data[:4].ljust(4, b"\0"), "big")
    if regtype == REG_QWORD:
        return int.from_bytes(data[:8].ljust(8, b"\0"), "little")
    return data


def _query_value(key, value_name):
    ctypes, wintypes, advapi32 = _native()
    value_name = None if value_name is None else str(value_name)
    regtype = wintypes.DWORD()
    size = wintypes.DWORD()
    code = advapi32.RegQueryValueExW(
        _hkey_pointer(key),
        value_name,
        None,
        ctypes.byref(regtype),
        None,
        ctypes.byref(size),
    )
    _raise_if_error(code)
    if size.value:
        buffer = (ctypes.c_ubyte * size.value)()
    else:
        buffer = (ctypes.c_ubyte * 1)()
    code = advapi32.RegQueryValueExW(
        _hkey_pointer(key),
        value_name,
        None,
        ctypes.byref(regtype),
        buffer,
        ctypes.byref(size),
    )
    _raise_if_error(code)
    return _convert_value(regtype.value, bytes(buffer[:size.value])), regtype.value


def QueryValueEx(key, value_name):
    return _query_value(key, value_name)


def QueryValue(key, sub_key):
    if sub_key is None or sub_key == "":
        value, _ = _query_value(key, None)
        return value
    with OpenKey(key, sub_key) as subkey:
        value, _ = _query_value(subkey, None)
        return value


def EnumValue(key, index):
    ctypes, wintypes, advapi32 = _native()
    name_size = 256
    data_size = 256
    while True:
        name = ctypes.create_unicode_buffer(name_size + 1)
        name_len = wintypes.DWORD(name_size + 1)
        regtype = wintypes.DWORD()
        data = (ctypes.c_ubyte * data_size)()
        data_len = wintypes.DWORD(data_size)
        code = advapi32.RegEnumValueW(
            _hkey_pointer(key),
            wintypes.DWORD(index),
            name,
            ctypes.byref(name_len),
            None,
            ctypes.byref(regtype),
            data,
            ctypes.byref(data_len),
        )
        if code == ERROR_MORE_DATA:
            name_size *= 2
            data_size = max(data_size * 2, data_len.value + 1)
            continue
        _raise_if_error(code)
        return name.value, _convert_value(regtype.value, bytes(data[:data_len.value])), regtype.value


def QueryInfoKey(key):
    ctypes, wintypes, advapi32 = _native()

    class FILETIME(ctypes.Structure):
        _fields_ = [("dwLowDateTime", wintypes.DWORD), ("dwHighDateTime", wintypes.DWORD)]

    advapi32.RegQueryInfoKeyW.argtypes = [
        wintypes.HANDLE,
        wintypes.LPWSTR,
        wintypes.LPDWORD,
        wintypes.LPDWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(FILETIME),
    ]
    subkeys = wintypes.DWORD()
    values = wintypes.DWORD()
    last_write = FILETIME()
    code = advapi32.RegQueryInfoKeyW(
        _hkey_pointer(key),
        None,
        None,
        None,
        ctypes.byref(subkeys),
        None,
        None,
        ctypes.byref(values),
        None,
        None,
        None,
        ctypes.byref(last_write),
    )
    _raise_if_error(code)
    timestamp = (last_write.dwHighDateTime << 32) | last_write.dwLowDateTime
    return subkeys.value, values.value, timestamp


def FlushKey(key):
    _, _, advapi32 = _native()
    _raise_if_error(advapi32.RegFlushKey(_hkey_pointer(key)))


_module = sys.modules.get(__name__)
if _module is not None:
    sys.modules.setdefault("_winreg", _module)


__all__ = [name for name in globals() if not name.startswith("_")]
