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

import sys


if sys.platform != "win32":
    raise ImportError("win32 only")


if not __graalpython__.native_access_is_available():
    raise ImportError("needs native access")


_POINTER_BITS = 64


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
CREATE_UNICODE_ENVIRONMENT = 0x00000400
EXTENDED_STARTUPINFO_PRESENT = 0x00080000
PROC_THREAD_ATTRIBUTE_HANDLE_LIST = 0x00020002

SYNCHRONIZE = 0x00100000
PROCESS_DUP_HANDLE = 0x00000040

ABOVE_NORMAL_PRIORITY_CLASS = 0x00008000
BELOW_NORMAL_PRIORITY_CLASS = 0x00004000
HIGH_PRIORITY_CLASS = 0x00000080
IDLE_PRIORITY_CLASS = 0x00000040
NORMAL_PRIORITY_CLASS = 0x00000020
REALTIME_PRIORITY_CLASS = 0x00000100

DUPLICATE_CLOSE_SOURCE = 0x00000001
DUPLICATE_SAME_ACCESS = 0x00000002

FILE_TYPE_UNKNOWN = 0x0000
FILE_TYPE_DISK = 0x0001
FILE_TYPE_CHAR = 0x0002
FILE_TYPE_PIPE = 0x0003

GENERIC_READ = 0x80000000
GENERIC_WRITE = 0x40000000
FILE_GENERIC_READ = 0x00120089
FILE_GENERIC_WRITE = 0x00120116
FILE_SHARE_READ = 0x00000001
FILE_SHARE_WRITE = 0x00000002
FILE_SHARE_DELETE = 0x00000004
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
ERROR_NETNAME_DELETED = 64
ERROR_BROKEN_PIPE = 109
ERROR_SEM_TIMEOUT = 121
ERROR_ALREADY_EXISTS = 183
ERROR_PIPE_BUSY = 231
ERROR_NO_DATA = 232
ERROR_MORE_DATA = 234
ERROR_NO_MORE_ITEMS = 259
ERROR_PIPE_CONNECTED = 535
ERROR_OPERATION_ABORTED = 995
ERROR_IO_INCOMPLETE = 996
ERROR_IO_PENDING = 997
ERROR_PRIVILEGE_NOT_HELD = 1314

LOCALE_NAME_INVARIANT = ""
LCMAP_LOWERCASE = 0x00000100

_ctypes = None
_wintypes = None
_kernel32 = None
_NeedCurrentDirectoryForExePathW = None
_CancelIoEx = None
_SECURITY_ATTRIBUTES = None
_STARTUPINFOW = None
_PROCESS_INFORMATION = None
_OVERLAPPED = None


def _native():
    global _ctypes, _wintypes, _kernel32, _NeedCurrentDirectoryForExePathW, _CancelIoEx
    if _kernel32 is not None:
        return _ctypes, _wintypes, _kernel32

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)

    kernel32.GetLastError.argtypes = []
    kernel32.GetLastError.restype = wintypes.DWORD
    kernel32.SetLastError.argtypes = [wintypes.DWORD]
    kernel32.SetLastError.restype = None
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.CloseHandle.restype = wintypes.BOOL
    kernel32.GetCurrentProcess.argtypes = []
    kernel32.GetCurrentProcess.restype = wintypes.HANDLE
    kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel32.OpenProcess.restype = wintypes.HANDLE
    kernel32.ExitProcess.argtypes = [ctypes.c_uint]
    kernel32.ExitProcess.restype = None
    kernel32.GetStdHandle.argtypes = [wintypes.DWORD]
    kernel32.GetStdHandle.restype = wintypes.HANDLE
    kernel32.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel32.WaitForSingleObject.restype = wintypes.DWORD
    kernel32.WaitForMultipleObjects.argtypes = [
        wintypes.DWORD,
        ctypes.POINTER(wintypes.HANDLE),
        wintypes.BOOL,
        wintypes.DWORD,
    ]
    kernel32.WaitForMultipleObjects.restype = wintypes.DWORD
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
    kernel32.GetACP.argtypes = []
    kernel32.GetACP.restype = wintypes.UINT
    kernel32.CreatePipe.argtypes = [
        ctypes.POINTER(wintypes.HANDLE),
        ctypes.POINTER(wintypes.HANDLE),
        wintypes.LPVOID,
        wintypes.DWORD,
    ]
    kernel32.CreatePipe.restype = wintypes.BOOL
    kernel32.CreateProcessW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.LPWSTR,
        wintypes.LPVOID,
        wintypes.LPVOID,
        wintypes.BOOL,
        wintypes.DWORD,
        wintypes.LPVOID,
        wintypes.LPCWSTR,
        wintypes.LPVOID,
        wintypes.LPVOID,
    ]
    kernel32.CreateProcessW.restype = wintypes.BOOL
    kernel32.InitializeProcThreadAttributeList.argtypes = [
        wintypes.LPVOID,
        wintypes.DWORD,
        wintypes.DWORD,
        ctypes.POINTER(ctypes.c_size_t),
    ]
    kernel32.InitializeProcThreadAttributeList.restype = wintypes.BOOL
    kernel32.UpdateProcThreadAttribute.argtypes = [
        wintypes.LPVOID,
        wintypes.DWORD,
        ctypes.c_size_t,
        wintypes.LPVOID,
        ctypes.c_size_t,
        wintypes.LPVOID,
        wintypes.LPVOID,
    ]
    kernel32.UpdateProcThreadAttribute.restype = wintypes.BOOL
    kernel32.DeleteProcThreadAttributeList.argtypes = [wintypes.LPVOID]
    kernel32.DeleteProcThreadAttributeList.restype = None
    kernel32.CreateFileW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.LPVOID,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.HANDLE,
    ]
    kernel32.CreateFileW.restype = wintypes.HANDLE
    kernel32.CreateNamedPipeW.argtypes = [
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.LPVOID,
    ]
    kernel32.CreateNamedPipeW.restype = wintypes.HANDLE
    kernel32.ConnectNamedPipe.argtypes = [wintypes.HANDLE, wintypes.LPVOID]
    kernel32.ConnectNamedPipe.restype = wintypes.BOOL
    kernel32.WaitNamedPipeW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD]
    kernel32.WaitNamedPipeW.restype = wintypes.BOOL
    kernel32.ReadFile.argtypes = [
        wintypes.HANDLE,
        wintypes.LPVOID,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.LPVOID,
    ]
    kernel32.ReadFile.restype = wintypes.BOOL
    kernel32.WriteFile.argtypes = [
        wintypes.HANDLE,
        wintypes.LPCVOID,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.LPVOID,
    ]
    kernel32.WriteFile.restype = wintypes.BOOL
    kernel32.PeekNamedPipe.argtypes = [
        wintypes.HANDLE,
        wintypes.LPVOID,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
    ]
    kernel32.PeekNamedPipe.restype = wintypes.BOOL
    kernel32.SetNamedPipeHandleState.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
    ]
    kernel32.SetNamedPipeHandleState.restype = wintypes.BOOL
    kernel32.GetOverlappedResult.argtypes = [
        wintypes.HANDLE,
        wintypes.LPVOID,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.BOOL,
    ]
    kernel32.GetOverlappedResult.restype = wintypes.BOOL
    kernel32.CreateEventW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.BOOL, wintypes.LPCWSTR]
    kernel32.CreateEventW.restype = wintypes.HANDLE
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

    try:
        cancel_io_ex = kernel32.CancelIoEx
    except AttributeError:
        cancel_io_ex = None
    else:
        cancel_io_ex.argtypes = [wintypes.HANDLE, wintypes.LPVOID]
        cancel_io_ex.restype = wintypes.BOOL

    _ctypes = ctypes
    _wintypes = wintypes
    _kernel32 = kernel32
    _NeedCurrentDirectoryForExePathW = need_current_directory
    _CancelIoEx = cancel_io_ex
    return _ctypes, _wintypes, _kernel32


def _structures():
    global _SECURITY_ATTRIBUTES, _STARTUPINFOW, _PROCESS_INFORMATION, _OVERLAPPED
    if _SECURITY_ATTRIBUTES is not None:
        return _SECURITY_ATTRIBUTES, _STARTUPINFOW, _PROCESS_INFORMATION, _OVERLAPPED

    ctypes, wintypes, _ = _native()
    ulong_ptr = ctypes.c_size_t

    class SECURITY_ATTRIBUTES(ctypes.Structure):
        _fields_ = [
            ("nLength", wintypes.DWORD),
            ("lpSecurityDescriptor", wintypes.LPVOID),
            ("bInheritHandle", wintypes.BOOL),
        ]

    class STARTUPINFOW(ctypes.Structure):
        _fields_ = [
            ("cb", wintypes.DWORD),
            ("lpReserved", wintypes.LPWSTR),
            ("lpDesktop", wintypes.LPWSTR),
            ("lpTitle", wintypes.LPWSTR),
            ("dwX", wintypes.DWORD),
            ("dwY", wintypes.DWORD),
            ("dwXSize", wintypes.DWORD),
            ("dwYSize", wintypes.DWORD),
            ("dwXCountChars", wintypes.DWORD),
            ("dwYCountChars", wintypes.DWORD),
            ("dwFillAttribute", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("wShowWindow", ctypes.c_ushort),
            ("cbReserved2", ctypes.c_ushort),
            ("lpReserved2", ctypes.POINTER(ctypes.c_byte)),
            ("hStdInput", wintypes.HANDLE),
            ("hStdOutput", wintypes.HANDLE),
            ("hStdError", wintypes.HANDLE),
        ]

    class PROCESS_INFORMATION(ctypes.Structure):
        _fields_ = [
            ("hProcess", wintypes.HANDLE),
            ("hThread", wintypes.HANDLE),
            ("dwProcessId", wintypes.DWORD),
            ("dwThreadId", wintypes.DWORD),
        ]

    class OVERLAPPED(ctypes.Structure):
        _fields_ = [
            ("Internal", ulong_ptr),
            ("InternalHigh", ulong_ptr),
            ("Offset", wintypes.DWORD),
            ("OffsetHigh", wintypes.DWORD),
            ("hEvent", wintypes.HANDLE),
        ]

    _SECURITY_ATTRIBUTES = SECURITY_ATTRIBUTES
    _STARTUPINFOW = STARTUPINFOW
    _PROCESS_INFORMATION = PROCESS_INFORMATION
    _OVERLAPPED = OVERLAPPED
    return _SECURITY_ATTRIBUTES, _STARTUPINFOW, _PROCESS_INFORMATION, _OVERLAPPED


def _winerror(code=None):
    ctypes, _, kernel32 = _native()
    if code is None:
        code = kernel32.GetLastError()
    if hasattr(ctypes, "WinError"):
        error = ctypes.WinError(code)
        if code in (ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND):
            return FileNotFoundError(error.errno, error.strerror, error.filename, code)
        if code == ERROR_ACCESS_DENIED:
            return PermissionError(error.errno, error.strerror, error.filename, code)
        return error
    if code in (ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND):
        return FileNotFoundError(code, f"Windows error {code}")
    if code == ERROR_ACCESS_DENIED:
        return PermissionError(code, f"Windows error {code}")
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


def _string(path):
    if not isinstance(path, str):
        raise TypeError("expected str")
    return path


def _check_nul(value, name):
    if value is not None and "\0" in value:
        raise ValueError(f"embedded null character in {name}")
    return value


def _optional_path(value, name):
    if value is None:
        return None
    return _check_nul(_fsdecode(value), name)


def _optional_pointer(value):
    if value in (None, NULL):
        return None
    raise NotImplementedError("custom Windows security attributes are not implemented in GraalPy")


def _dword_pointer(value):
    ctypes, wintypes, _ = _native()
    if value is None:
        return None
    return ctypes.byref(wintypes.DWORD(value))


def _make_environment(env_mapping):
    if env_mapping is None:
        return None
    if isinstance(env_mapping, str):
        return _check_nul(env_mapping, "environment")

    normalized = {}
    for key, value in env_mapping.items():
        key = _fsdecode(key)
        value = _fsdecode(value)
        if not key:
            raise ValueError("empty environment variable name")
        if "\0" in key or "\0" in value:
            raise ValueError("embedded null character in environment")
        if "=" in key:
            raise ValueError("illegal environment variable name")
        normalized[key.upper()] = (key, value)
    return "\0".join(f"{key}={value}" for _, (key, value) in sorted(normalized.items())) + "\0\0"


class _WinapiOverlapped:
    def __init__(self, handle):
        ctypes, wintypes, kernel32 = _native()
        _, _, _, OVERLAPPED = _structures()
        self.handle = _as_handle(handle)
        self.event = _as_handle(kernel32.CreateEventW(None, True, False, None))
        if not self.event:
            _raise_last_error()
        self.pending = False
        self._completed_result = None
        self._last_result = None
        self._buffer = None
        self._overlapped = OVERLAPPED()
        self._overlapped.hEvent = wintypes.HANDLE(self.event)
        self.address = ctypes.addressof(self._overlapped)

    def cancel(self):
        ctypes, wintypes, _ = _native()
        if self.pending and _CancelIoEx is not None:
            _CancelIoEx(wintypes.HANDLE(self.handle), ctypes.byref(self._overlapped))
        self.pending = False

    def GetOverlappedResult(self, wait=False):
        ctypes, wintypes, kernel32 = _native()
        if self._completed_result is not None:
            self._last_result = self._completed_result
            self.pending = False
            return self._last_result
        transferred = wintypes.DWORD()
        result = kernel32.GetOverlappedResult(
            wintypes.HANDLE(self.handle),
            ctypes.byref(self._overlapped),
            ctypes.byref(transferred),
            bool(wait),
        )
        if result:
            code = 0
            self.pending = False
        else:
            code = kernel32.GetLastError()
            if code != ERROR_IO_INCOMPLETE:
                self.pending = False
        self._last_result = (transferred.value, code)
        return self._last_result

    getresult = GetOverlappedResult

    def getbuffer(self):
        if self._buffer is None:
            return b""
        if self._last_result is None:
            self.GetOverlappedResult(False)
        return self._buffer.raw[: self._last_result[0]]

    def __del__(self):
        try:
            if self.event:
                CloseHandle(self.event)
        except Exception:
            pass


def CloseHandle(handle):
    _, wintypes, kernel32 = _native()
    _raise_if_zero(kernel32.CloseHandle(wintypes.HANDLE(_as_handle(handle))))


def GetLastError():
    _, _, kernel32 = _native()
    return kernel32.GetLastError()


def GetCurrentProcess():
    _, _, kernel32 = _native()
    return _as_handle(kernel32.GetCurrentProcess())


def OpenProcess(desired_access, inherit_handle, process_id):
    _, wintypes, kernel32 = _native()
    handle = _as_handle(
        kernel32.OpenProcess(wintypes.DWORD(desired_access), wintypes.BOOL(inherit_handle), wintypes.DWORD(process_id))
    )
    if not handle:
        _raise_last_error()
    return handle


def ExitProcess(exit_code):
    _, _, kernel32 = _native()
    kernel32.ExitProcess(exit_code)


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


def WaitForMultipleObjects(handle_seq, wait_flag, milliseconds=INFINITE):
    ctypes, wintypes, kernel32 = _native()
    handles = tuple(handle_seq)
    if not handles:
        raise ValueError("handle_seq must not be empty")
    handle_array = (wintypes.HANDLE * len(handles))(*(_as_handle(handle) for handle in handles))
    result = kernel32.WaitForMultipleObjects(
        wintypes.DWORD(len(handles)),
        handle_array,
        wintypes.BOOL(wait_flag),
        wintypes.DWORD(milliseconds),
    )
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


def GetACP():
    _, _, kernel32 = _native()
    return kernel32.GetACP()


def CreatePipe(pipe_attrs, size):
    ctypes, wintypes, kernel32 = _native()
    _optional_pointer(pipe_attrs)
    read_pipe = wintypes.HANDLE()
    write_pipe = wintypes.HANDLE()
    _raise_if_zero(
        kernel32.CreatePipe(
            ctypes.byref(read_pipe),
            ctypes.byref(write_pipe),
            None,
            wintypes.DWORD(size),
        )
    )
    return _as_handle(read_pipe.value), _as_handle(write_pipe.value)


def CreateProcess(
    application_name,
    command_line,
    proc_attrs,
    thread_attrs,
    inherit_handles,
    creation_flags,
    env_mapping,
    current_directory,
    startup_info,
):
    ctypes, wintypes, kernel32 = _native()
    _, STARTUPINFOW, PROCESS_INFORMATION, _ = _structures()
    _optional_pointer(proc_attrs)
    _optional_pointer(thread_attrs)

    application_name = _optional_path(application_name, "application_name")
    command_line = _optional_path(command_line, "command_line")
    current_directory = _optional_path(current_directory, "current_directory")

    class STARTUPINFOEXW(ctypes.Structure):
        _fields_ = [
            ("StartupInfo", STARTUPINFOW),
            ("lpAttributeList", wintypes.LPVOID),
        ]

    attribute_list = None
    handle_list = []
    if startup_info is not None:
        attribute_list = getattr(startup_info, "lpAttributeList", None)
        if attribute_list is not None:
            handle_list = list(attribute_list.get("handle_list", []))

    startup_ex = STARTUPINFOEXW() if handle_list else None
    startup = startup_ex.StartupInfo if startup_ex is not None else STARTUPINFOW()
    startup.cb = ctypes.sizeof(startup_ex) if startup_ex is not None else ctypes.sizeof(startup)
    if startup_info is not None:
        startup.dwFlags = getattr(startup_info, "dwFlags", 0)
        startup.wShowWindow = getattr(startup_info, "wShowWindow", 0)
        startup.hStdInput = wintypes.HANDLE(_as_handle(getattr(startup_info, "hStdInput", NULL)))
        startup.hStdOutput = wintypes.HANDLE(_as_handle(getattr(startup_info, "hStdOutput", NULL)))
        startup.hStdError = wintypes.HANDLE(_as_handle(getattr(startup_info, "hStdError", NULL)))

    command_line_buffer = ctypes.create_unicode_buffer(command_line) if command_line is not None else None
    environment = _make_environment(env_mapping)
    environment_buffer = ctypes.create_unicode_buffer(environment) if environment is not None else None
    if environment_buffer is not None:
        creation_flags |= CREATE_UNICODE_ENVIRONMENT

    process_information = PROCESS_INFORMATION()
    handle_array = None
    attribute_buffer = None
    if handle_list:
        handle_array = (wintypes.HANDLE * len(handle_list))(*(_as_handle(handle) for handle in handle_list))
        attribute_list_size = ctypes.c_size_t()
        kernel32.InitializeProcThreadAttributeList(None, 1, 0, ctypes.byref(attribute_list_size))
        attribute_buffer = ctypes.create_string_buffer(attribute_list_size.value)
        _raise_if_zero(
            kernel32.InitializeProcThreadAttributeList(attribute_buffer, 1, 0, ctypes.byref(attribute_list_size))
        )
        try:
            _raise_if_zero(
                kernel32.UpdateProcThreadAttribute(
                    attribute_buffer,
                    0,
                    PROC_THREAD_ATTRIBUTE_HANDLE_LIST,
                    handle_array,
                    ctypes.sizeof(handle_array),
                    None,
                    None,
                )
            )
        except BaseException:
            kernel32.DeleteProcThreadAttributeList(attribute_buffer)
            raise
        startup_ex.lpAttributeList = ctypes.cast(attribute_buffer, wintypes.LPVOID)
        creation_flags |= EXTENDED_STARTUPINFO_PRESENT

    try:
        result = kernel32.CreateProcessW(
            application_name,
            command_line_buffer,
            None,
            None,
            wintypes.BOOL(inherit_handles),
            wintypes.DWORD(creation_flags),
            environment_buffer,
            current_directory,
            ctypes.byref(startup_ex) if startup_ex is not None else ctypes.byref(startup),
            ctypes.byref(process_information),
        )
        if not result:
            _raise_last_error()
    finally:
        if attribute_buffer is not None:
            kernel32.DeleteProcThreadAttributeList(attribute_buffer)
    return (
        _as_handle(process_information.hProcess),
        _as_handle(process_information.hThread),
        process_information.dwProcessId,
        process_information.dwThreadId,
    )


def CreateFile(
    file_name,
    desired_access,
    share_mode,
    security_attributes,
    creation_disposition,
    flags_and_attributes,
    template_file,
):
    _, wintypes, kernel32 = _native()
    _optional_pointer(security_attributes)
    file_name = _optional_path(file_name, "file_name")
    handle = _as_handle(
        kernel32.CreateFileW(
            file_name,
            wintypes.DWORD(desired_access),
            wintypes.DWORD(share_mode),
            None,
            wintypes.DWORD(creation_disposition),
            wintypes.DWORD(flags_and_attributes),
            wintypes.HANDLE(_as_handle(template_file)),
        )
    )
    if handle == INVALID_HANDLE_VALUE:
        _raise_last_error()
    return handle


def CreateNamedPipe(
    name,
    open_mode,
    pipe_mode,
    max_instances,
    out_buffer_size,
    in_buffer_size,
    default_timeout,
    security_attributes,
):
    sys.audit("_winapi.CreateNamedPipe", name, open_mode, pipe_mode)
    _, wintypes, kernel32 = _native()
    _optional_pointer(security_attributes)
    name = _optional_path(name, "name")
    handle = _as_handle(
        kernel32.CreateNamedPipeW(
            name,
            wintypes.DWORD(open_mode),
            wintypes.DWORD(pipe_mode),
            wintypes.DWORD(max_instances),
            wintypes.DWORD(out_buffer_size),
            wintypes.DWORD(in_buffer_size),
            wintypes.DWORD(default_timeout),
            None,
        )
    )
    if handle == INVALID_HANDLE_VALUE:
        _raise_last_error()
    return handle


def ConnectNamedPipe(handle, overlapped=False):
    ctypes, wintypes, kernel32 = _native()
    if overlapped:
        ov = _WinapiOverlapped(handle)
        result = kernel32.ConnectNamedPipe(wintypes.HANDLE(_as_handle(handle)), ctypes.byref(ov._overlapped))
        if result:
            ov._completed_result = (0, 0)
            return ov
        code = kernel32.GetLastError()
        if code == ERROR_IO_PENDING:
            ov.pending = True
            return ov
        if code == ERROR_PIPE_CONNECTED:
            ov._completed_result = (0, 0)
            return ov
        raise _winerror(code)

    result = kernel32.ConnectNamedPipe(wintypes.HANDLE(_as_handle(handle)), None)
    if result:
        return None
    code = kernel32.GetLastError()
    if code == ERROR_PIPE_CONNECTED:
        return None
    raise _winerror(code)


def WaitNamedPipe(name, timeout):
    _, wintypes, kernel32 = _native()
    name = _optional_path(name, "name")
    result = kernel32.WaitNamedPipeW(name, wintypes.DWORD(timeout))
    if not result:
        _raise_last_error()


def ReadFile(handle, size, overlapped=False):
    ctypes, wintypes, kernel32 = _native()
    size = int(size)
    buffer = ctypes.create_string_buffer(size)
    transferred = wintypes.DWORD()
    if overlapped:
        ov = _WinapiOverlapped(handle)
        ov._buffer = buffer
        result = kernel32.ReadFile(
            wintypes.HANDLE(_as_handle(handle)),
            buffer,
            wintypes.DWORD(size),
            ctypes.byref(transferred),
            ctypes.byref(ov._overlapped),
        )
        if result:
            ov._completed_result = (transferred.value, 0)
            ov._last_result = ov._completed_result
            return ov, 0
        code = kernel32.GetLastError()
        if code == ERROR_IO_PENDING:
            ov.pending = True
            return ov, code
        if code == ERROR_MORE_DATA:
            ov._completed_result = (transferred.value, code)
            ov._last_result = ov._completed_result
            return ov, code
        raise _winerror(code)

    result = kernel32.ReadFile(
        wintypes.HANDLE(_as_handle(handle)),
        buffer,
        wintypes.DWORD(size),
        ctypes.byref(transferred),
        None,
    )
    if not result:
        code = kernel32.GetLastError()
        if code == ERROR_MORE_DATA:
            return buffer.raw[: transferred.value], code
        raise _winerror(code)
    return buffer.raw[: transferred.value], 0


def WriteFile(handle, buffer, overlapped=False):
    ctypes, wintypes, kernel32 = _native()
    data = bytes(buffer)
    write_buffer = ctypes.create_string_buffer(data, len(data))
    transferred = wintypes.DWORD()
    if overlapped:
        ov = _WinapiOverlapped(handle)
        ov._buffer = write_buffer
        result = kernel32.WriteFile(
            wintypes.HANDLE(_as_handle(handle)),
            write_buffer,
            wintypes.DWORD(len(data)),
            ctypes.byref(transferred),
            ctypes.byref(ov._overlapped),
        )
        if result:
            ov._completed_result = (transferred.value, 0)
            ov._last_result = ov._completed_result
            return ov, 0
        code = kernel32.GetLastError()
        if code == ERROR_IO_PENDING:
            ov.pending = True
            return ov, code
        raise _winerror(code)

    result = kernel32.WriteFile(
        wintypes.HANDLE(_as_handle(handle)),
        write_buffer,
        wintypes.DWORD(len(data)),
        ctypes.byref(transferred),
        None,
    )
    if not result:
        _raise_last_error()
    return transferred.value, 0


def PeekNamedPipe(handle, size=0):
    ctypes, wintypes, kernel32 = _native()
    size = int(size)
    buffer = ctypes.create_string_buffer(size) if size else None
    bytes_read = wintypes.DWORD()
    total_available = wintypes.DWORD()
    bytes_left = wintypes.DWORD()
    _raise_if_zero(
        kernel32.PeekNamedPipe(
            wintypes.HANDLE(_as_handle(handle)),
            buffer,
            wintypes.DWORD(size),
            ctypes.byref(bytes_read),
            ctypes.byref(total_available),
            ctypes.byref(bytes_left),
        )
    )
    return (buffer.raw[: bytes_read.value] if buffer is not None else b""), total_available.value, bytes_left.value


def SetNamedPipeHandleState(named_pipe, mode, max_collection_count, collect_data_timeout):
    _, wintypes, kernel32 = _native()
    _raise_if_zero(
        kernel32.SetNamedPipeHandleState(
            wintypes.HANDLE(_as_handle(named_pipe)),
            _dword_pointer(mode),
            _dword_pointer(max_collection_count),
            _dword_pointer(collect_data_timeout),
        )
    )


def _get_path_name(function, path):
    ctypes, _, _ = _native()
    path = _string(path)
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
    if not isinstance(src, str):
        raise TypeError("src must be str")
    if locale_name is None:
        locale_name = LOCALE_NAME_INVARIANT
    if locale_name == LOCALE_NAME_INVARIANT and flags == LCMAP_LOWERCASE:
        return src.lower()
    ctypes, _, kernel32 = _native()
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
