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


_POINTER_BITS = 64 if sys.maxsize > 2**32 else 32


def _unsigned_pointer(value):
    return value % (1 << _POINTER_BITS)


NULL = 0
INVALID_HANDLE_VALUE = _unsigned_pointer(-1)
INFINITE = 0xFFFFFFFF
WAIT_OBJECT_0 = 0
WAIT_ABANDONED_0 = 0x80
WAIT_TIMEOUT = 0x102
WAIT_FAILED = 0xFFFFFFFF
STILL_ACTIVE = 259

STD_INPUT_HANDLE = -10
STD_OUTPUT_HANDLE = -11
STD_ERROR_HANDLE = -12

SW_HIDE = 0
STARTF_USESHOWWINDOW = 0x00000001
STARTF_USESTDHANDLES = 0x00000100

CREATE_NEW_CONSOLE = 0x00000010
CREATE_NEW_PROCESS_GROUP = 0x00000200
CREATE_NO_WINDOW = 0x08000000
DETACHED_PROCESS = 0x00000008
CREATE_DEFAULT_ERROR_MODE = 0x04000000
CREATE_BREAKAWAY_FROM_JOB = 0x01000000

ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000
BELOW_NORMAL_PRIORITY_CLASS = 0x00004000
HIGH_PRIORITY_CLASS = 0x00000080
IDLE_PRIORITY_CLASS = 0x00000040
NORMAL_PRIORITY_CLASS = 0x00000020
REALTIME_PRIORITY_CLASS = 0x00000100

DUPLICATE_SAME_ACCESS = 0x00000002

FILE_TYPE_UNKNOWN = 0x0000
FILE_TYPE_DISK = 0x0001
FILE_TYPE_CHAR = 0x0002
FILE_TYPE_PIPE = 0x0003

GENERIC_READ = 0x80000000
GENERIC_WRITE = 0x40000000
OPEN_EXISTING = 3
FILE_ATTRIBUTE_NORMAL = 0x00000080
FILE_FLAG_FIRST_PIPE_INSTANCE = 0x00080000
FILE_FLAG_OVERLAPPED = 0x40000000

PIPE_ACCESS_INBOUND = 0x00000001
PIPE_ACCESS_OUTBOUND = 0x00000002
PIPE_ACCESS_DUPLEX = 0x00000003
PIPE_TYPE_MESSAGE = 0x00000004
PIPE_READMODE_MESSAGE = 0x00000002
PIPE_WAIT = 0x00000000
PIPE_UNLIMITED_INSTANCES = 255
NMPWAIT_WAIT_FOREVER = 0xFFFFFFFF

ERROR_FILE_NOT_FOUND = 2
ERROR_PATH_NOT_FOUND = 3
ERROR_ACCESS_DENIED = 5
ERROR_INVALID_HANDLE = 6
ERROR_BROKEN_PIPE = 109
ERROR_ALREADY_EXISTS = 183
ERROR_PIPE_BUSY = 231
ERROR_NO_DATA = 232
ERROR_MORE_DATA = 234
ERROR_NO_MORE_ITEMS = 259
ERROR_OPERATION_ABORTED = 995
ERROR_IO_PENDING = 997
ERROR_PRIVILEGE_NOT_HELD = 1314

LOCALE_NAME_INVARIANT = ""
LCMAP_LOWERCASE = 0x00000100

_ctypes = None
_wintypes = None
_kernel32 = None
_NeedCurrentDirectoryForExePathW = None


def _native():
    global _ctypes, _wintypes, _kernel32, _NeedCurrentDirectoryForExePathW
    if _kernel32 is not None:
        return _ctypes, _wintypes, _kernel32

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.windll.kernel32

    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.CloseHandle.restype = wintypes.BOOL
    kernel32.GetCurrentProcess.argtypes = []
    kernel32.GetCurrentProcess.restype = wintypes.HANDLE
    kernel32.GetStdHandle.argtypes = [wintypes.DWORD]
    kernel32.GetStdHandle.restype = wintypes.HANDLE
    kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel32.WaitForSingleObject.restype = wintypes.DWORD
    kernel32.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel32.GetExitCodeProcess.restype = wintypes.BOOL
    kernel32.TerminateProcess.argtypes = [wintypes.HANDLE, ctypes.c_uint]
    kernel32.TerminateProcess.restype = wintypes.BOOL
    kernel32.DuplicateHandle.argtypes = [
        wintypes.HANDLE,
        wintypes.HANDLE,
        wintypes.HANDLE,
        ctypes.POINTER(wintypes.HANDLE),
        wintypes.DWORD,
        wintypes.BOOL,
        wintypes.DWORD,
    ]
    kernel32.DuplicateHandle.restype = wintypes.BOOL
    kernel32.GetFileType.argtypes = [wintypes.HANDLE]
    kernel32.GetFileType.restype = wintypes.DWORD
    kernel32.GetLongPathNameW.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.DWORD]
    kernel32.GetLongPathNameW.restype = wintypes.DWORD
    kernel32.GetShortPathNameW.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR, wintypes.DWORD]
    kernel32.GetShortPathNameW.restype = wintypes.DWORD
    kernel32.GetModuleFileNameW.argtypes = [wintypes.HMODULE, wintypes.LPWSTR, wintypes.DWORD]
    kernel32.GetModuleFileNameW.restype = wintypes.DWORD
    kernel32.LCMapStringEx.argtypes = [
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.LPCWSTR,
        ctypes.c_int,
        wintypes.LPWSTR,
        ctypes.c_int,
        wintypes.LPVOID,
        wintypes.LPVOID,
        wintypes.LPARAM,
    ]
    kernel32.LCMapStringEx.restype = ctypes.c_int

    try:
        need_current_directory = kernel32.NeedCurrentDirectoryForExePathW
    except AttributeError:
        need_current_directory = None
    else:
        need_current_directory.argtypes = [wintypes.LPCWSTR]
        need_current_directory.restype = wintypes.BOOL

    _ctypes = ctypes
    _wintypes = wintypes
    _kernel32 = kernel32
    _NeedCurrentDirectoryForExePathW = need_current_directory
    return _ctypes, _wintypes, _kernel32


def _winerror(code=None):
    ctypes, _, kernel32 = _native()
    if code is None:
        code = kernel32.GetLastError()
    if hasattr(ctypes, "WinError"):
        return ctypes.WinError(code)
    return OSError(code, f"Windows error {code}")


def _raise_last_error():
    raise _winerror()


def _raise_if_zero(result):
    if result == 0:
        _raise_last_error()
    return result


def _as_handle(handle):
    if handle is None:
        return NULL
    return int(handle)


def _fsdecode(path):
    if isinstance(path, bytes):
        return path.decode(sys.getfilesystemencoding(), "surrogateescape")
    return str(path)


def CloseHandle(handle):
    _, wintypes, kernel32 = _native()
    _raise_if_zero(kernel32.CloseHandle(wintypes.HANDLE(_as_handle(handle))))


def GetCurrentProcess():
    _, _, kernel32 = _native()
    return _as_handle(kernel32.GetCurrentProcess())


def GetStdHandle(std_handle):
    _, wintypes, kernel32 = _native()
    handle = _as_handle(kernel32.GetStdHandle(wintypes.DWORD(std_handle)))
    if handle == INVALID_HANDLE_VALUE:
        _raise_last_error()
    return handle


def WaitForSingleObject(handle, milliseconds):
    _, wintypes, kernel32 = _native()
    result = kernel32.WaitForSingleObject(wintypes.HANDLE(_as_handle(handle)), wintypes.DWORD(milliseconds))
    if result == WAIT_FAILED:
        _raise_last_error()
    return result


def GetExitCodeProcess(handle):
    ctypes, wintypes, kernel32 = _native()
    exit_code = wintypes.DWORD()
    _raise_if_zero(kernel32.GetExitCodeProcess(wintypes.HANDLE(_as_handle(handle)), ctypes.byref(exit_code)))
    return exit_code.value


def TerminateProcess(handle, exit_code):
    _, wintypes, kernel32 = _native()
    _raise_if_zero(kernel32.TerminateProcess(wintypes.HANDLE(_as_handle(handle)), wintypes.DWORD(exit_code)))


def DuplicateHandle(
    source_process_handle,
    source_handle,
    target_process_handle,
    desired_access,
    inherit_handle,
    options,
):
    ctypes, wintypes, kernel32 = _native()
    target_handle = wintypes.HANDLE()
    _raise_if_zero(
        kernel32.DuplicateHandle(
            wintypes.HANDLE(_as_handle(source_process_handle)),
            wintypes.HANDLE(_as_handle(source_handle)),
            wintypes.HANDLE(_as_handle(target_process_handle)),
            ctypes.byref(target_handle),
            wintypes.DWORD(desired_access),
            wintypes.BOOL(inherit_handle),
            wintypes.DWORD(options),
        )
    )
    return _as_handle(target_handle.value)


def GetFileType(handle):
    _, wintypes, kernel32 = _native()
    kernel32.SetLastError(0)
    result = kernel32.GetFileType(wintypes.HANDLE(_as_handle(handle)))
    if result == FILE_TYPE_UNKNOWN and kernel32.GetLastError() != 0:
        _raise_last_error()
    return result


def _get_path_name(function, path):
    ctypes, _, _ = _native()
    path = _fsdecode(path)
    size = 260
    while True:
        buffer = ctypes.create_unicode_buffer(size)
        result = function(path, buffer, size)
        if result == 0:
            _raise_last_error()
        if result < size:
            return buffer.value
        size = result + 1


def GetLongPathName(path):
    _, _, kernel32 = _native()
    return _get_path_name(kernel32.GetLongPathNameW, path)


def GetShortPathName(path):
    _, _, kernel32 = _native()
    return _get_path_name(kernel32.GetShortPathNameW, path)


def GetModuleFileName(module_handle):
    ctypes, wintypes, kernel32 = _native()
    size = 260
    while True:
        buffer = ctypes.create_unicode_buffer(size)
        result = kernel32.GetModuleFileNameW(wintypes.HMODULE(_as_handle(module_handle)), buffer, size)
        if result == 0:
            _raise_last_error()
        if result < size - 1:
            return buffer.value
        size *= 2


def LCMapStringEx(locale_name, flags, src):
    ctypes, _, kernel32 = _native()
    if not isinstance(src, str):
        raise TypeError("src must be str")
    if locale_name is None:
        locale_name = LOCALE_NAME_INVARIANT
    buffer = ctypes.create_unicode_buffer(len(src) + 1)
    result = kernel32.LCMapStringEx(locale_name, flags, src, len(src), buffer, len(buffer), None, None, 0)
    if result == 0:
        _raise_last_error()
    return buffer.value[:result]


def NeedCurrentDirectoryForExePath(exe_name):
    _native()
    if _NeedCurrentDirectoryForExePathW is None:
        return not any(sep in _fsdecode(exe_name) for sep in ("\\", "/"))
    return bool(_NeedCurrentDirectoryForExePathW(_fsdecode(exe_name)))


def _mimetypes_read_windows_registry(add_type):
    import winreg

    try:
        mimedb = winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, "")
    except OSError:
        return
    with mimedb:
        index = 0
        while True:
            try:
                subkeyname = winreg.EnumKey(mimedb, index)
            except OSError:
                break
            index += 1
            if "\0" in subkeyname or not subkeyname.startswith("."):
                continue
            try:
                with winreg.OpenKey(mimedb, subkeyname) as subkey:
                    mimetype, datatype = winreg.QueryValueEx(subkey, "Content Type")
            except OSError:
                continue
            if datatype == winreg.REG_SZ:
                add_type(mimetype, subkeyname)


def _not_implemented(name):
    def function(*args, **kwargs):
        raise NotImplementedError(f"_winapi.{name} is not implemented in GraalPy")

    function.__name__ = name
    return function


CreatePipe = _not_implemented("CreatePipe")
CreateProcess = _not_implemented("CreateProcess")
CreateFile = _not_implemented("CreateFile")
CreateNamedPipe = _not_implemented("CreateNamedPipe")
ConnectNamedPipe = _not_implemented("ConnectNamedPipe")
