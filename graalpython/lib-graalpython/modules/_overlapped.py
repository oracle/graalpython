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


from _winapi import INFINITE, INVALID_HANDLE_VALUE, NULL


ERROR_SUCCESS = 0
ERROR_FILE_NOT_FOUND = 2
ERROR_PATH_NOT_FOUND = 3
ERROR_INVALID_HANDLE = 6
ERROR_BROKEN_PIPE = 109
ERROR_NETNAME_DELETED = 64
ERROR_SEM_TIMEOUT = 121
ERROR_MORE_DATA = 234
ERROR_NOT_FOUND = 1168
ERROR_CONNECTION_REFUSED = 1225
ERROR_CONNECTION_ABORTED = 1236
ERROR_OPERATION_ABORTED = 995
ERROR_IO_INCOMPLETE = 996
ERROR_IO_PENDING = 997
ERROR_PORT_UNREACHABLE = 1234
ERROR_PIPE_BUSY = 231
ERROR_NO_DATA = 232

WSA_ERROR = -1
EBADF = 9
ENOTSOCK = 88

_TYPE_NONE = 0
_TYPE_NOT_STARTED = 1
_TYPE_READ = 2
_TYPE_READINTO = 3
_TYPE_WRITE = 4
_TYPE_CONNECT = 6
_TYPE_ACCEPT = 7
_TYPE_TRANSMIT_FILE = 8
_TYPE_CONNECT_NAMED_PIPE = 9
_TYPE_READ_FROM = 11
_TYPE_WRITE_TO = 12
_TYPE_READ_FROM_INTO = 13

SIO_GET_EXTENSION_FUNCTION_POINTER = 0xC8000006

SO_UPDATE_ACCEPT_CONTEXT = 0x700B
SO_UPDATE_CONNECT_CONTEXT = 0x7010

_ctypes = None
_wintypes = None
_kernel32 = None
_ws2_32 = None
_posix = None
_PostQueuedCompletionStatus = None
_UnregisterWait = None
_UnregisterWaitEx = None
_CancelIoEx = None
_RegisterWaitForSingleObject = None
_OVERLAPPED = None
_WSABUF = None
_SOCKADDR_IN = None
_SOCKADDR_IN6 = None
_GUID = None
_AcceptEx = None
_ConnectEx = None
_TransmitFile = None
_wait_callbacks = {}


def _native():
    global _ctypes, _wintypes, _kernel32, _ws2_32
    global _PostQueuedCompletionStatus, _UnregisterWait, _UnregisterWaitEx
    global _CancelIoEx, _RegisterWaitForSingleObject
    if _kernel32 is not None:
        return _ctypes, _wintypes, _kernel32, _ws2_32

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    ws2_32 = ctypes.WinDLL("ws2_32", use_last_error=True)
    kernel32.GetLastError.argtypes = []
    kernel32.GetLastError.restype = wintypes.DWORD
    kernel32.CreateIoCompletionPort.argtypes = [wintypes.HANDLE, wintypes.HANDLE, ctypes.c_size_t, wintypes.DWORD]
    kernel32.CreateIoCompletionPort.restype = wintypes.HANDLE
    kernel32.GetQueuedCompletionStatus.argtypes = [
        wintypes.HANDLE,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(ctypes.c_size_t),
        ctypes.POINTER(ctypes.c_void_p),
        wintypes.DWORD,
    ]
    kernel32.GetQueuedCompletionStatus.restype = wintypes.BOOL
    kernel32.CreateEventW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.BOOL, wintypes.LPCWSTR]
    kernel32.CreateEventW.restype = wintypes.HANDLE
    kernel32.ReadFile.argtypes = [
        wintypes.HANDLE,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
    ]
    kernel32.ReadFile.restype = wintypes.BOOL
    kernel32.WriteFile.argtypes = [
        wintypes.HANDLE,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
    ]
    kernel32.WriteFile.restype = wintypes.BOOL
    kernel32.GetOverlappedResult.argtypes = [
        wintypes.HANDLE,
        ctypes.c_void_p,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.BOOL,
    ]
    kernel32.GetOverlappedResult.restype = wintypes.BOOL
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.CloseHandle.restype = wintypes.BOOL
    kernel32.SetEvent.argtypes = [wintypes.HANDLE]
    kernel32.SetEvent.restype = wintypes.BOOL
    kernel32.ConnectNamedPipe.argtypes = [wintypes.HANDLE, ctypes.c_void_p]
    kernel32.ConnectNamedPipe.restype = wintypes.BOOL
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

    ws2_32.WSAGetLastError.argtypes = []
    ws2_32.WSAGetLastError.restype = ctypes.c_int
    ws2_32.WSASetLastError.argtypes = [ctypes.c_int]
    ws2_32.WSASetLastError.restype = None

    try:
        post_queued_completion_status = kernel32.PostQueuedCompletionStatus
    except AttributeError:
        post_queued_completion_status = None
    else:
        post_queued_completion_status.argtypes = [wintypes.HANDLE, wintypes.DWORD, ctypes.c_size_t, ctypes.c_void_p]
        post_queued_completion_status.restype = wintypes.BOOL

    try:
        unregister_wait = kernel32.UnregisterWait
        unregister_wait_ex = kernel32.UnregisterWaitEx
    except AttributeError:
        unregister_wait = None
        unregister_wait_ex = None
    else:
        unregister_wait.argtypes = [wintypes.HANDLE]
        unregister_wait.restype = wintypes.BOOL
        unregister_wait_ex.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
        unregister_wait_ex.restype = wintypes.BOOL

    try:
        register_wait = kernel32.RegisterWaitForSingleObject
    except AttributeError:
        register_wait = None
    else:
        register_wait.argtypes = [
            ctypes.POINTER(wintypes.HANDLE),
            wintypes.HANDLE,
            ctypes.c_void_p,
            ctypes.c_void_p,
            wintypes.ULONG,
            wintypes.ULONG,
        ]
        register_wait.restype = wintypes.BOOL

    try:
        cancel_io_ex = kernel32.CancelIoEx
    except AttributeError:
        cancel_io_ex = None
    else:
        cancel_io_ex.argtypes = [wintypes.HANDLE, ctypes.c_void_p]
        cancel_io_ex.restype = wintypes.BOOL

    _ctypes = ctypes
    _wintypes = wintypes
    _kernel32 = kernel32
    _ws2_32 = ws2_32
    _PostQueuedCompletionStatus = post_queued_completion_status
    _UnregisterWait = unregister_wait
    _UnregisterWaitEx = unregister_wait_ex
    _RegisterWaitForSingleObject = register_wait
    _CancelIoEx = cancel_io_ex
    _configure_ws2_32()
    return _ctypes, _wintypes, _kernel32, _ws2_32


def _structures():
    global _OVERLAPPED, _WSABUF, _SOCKADDR_IN, _SOCKADDR_IN6, _GUID
    if _OVERLAPPED is not None:
        return _OVERLAPPED, _WSABUF, _SOCKADDR_IN, _SOCKADDR_IN6, _GUID

    ctypes, wintypes, _, _ = _native()

    class OVERLAPPED(ctypes.Structure):
        _fields_ = [
            ("Internal", ctypes.c_size_t),
            ("InternalHigh", ctypes.c_size_t),
            ("Offset", wintypes.DWORD),
            ("OffsetHigh", wintypes.DWORD),
            ("hEvent", wintypes.HANDLE),
        ]

    class WSABUF(ctypes.Structure):
        _fields_ = [
            ("len", wintypes.ULONG),
            ("buf", ctypes.c_void_p),
        ]

    class SOCKADDR_IN(ctypes.Structure):
        _fields_ = [
            ("sin_family", ctypes.c_ushort),
            ("sin_port", ctypes.c_ushort),
            ("sin_addr", ctypes.c_ubyte * 4),
            ("sin_zero", ctypes.c_char * 8),
        ]

    class SOCKADDR_IN6(ctypes.Structure):
        _fields_ = [
            ("sin6_family", ctypes.c_ushort),
            ("sin6_port", ctypes.c_ushort),
            ("sin6_flowinfo", ctypes.c_ulong),
            ("sin6_addr", ctypes.c_ubyte * 16),
            ("sin6_scope_id", ctypes.c_ulong),
        ]

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", ctypes.c_ulong),
            ("Data2", ctypes.c_ushort),
            ("Data3", ctypes.c_ushort),
            ("Data4", ctypes.c_ubyte * 8),
        ]

    _OVERLAPPED = OVERLAPPED
    _WSABUF = WSABUF
    _SOCKADDR_IN = SOCKADDR_IN
    _SOCKADDR_IN6 = SOCKADDR_IN6
    _GUID = GUID
    return _OVERLAPPED, _WSABUF, _SOCKADDR_IN, _SOCKADDR_IN6, _GUID


def _configure_ws2_32():
    ctypes, wintypes, _, ws2_32 = _ctypes, _wintypes, _kernel32, _ws2_32
    OVERLAPPED, WSABUF, _, _, _ = _structures()
    socket_t = ctypes.c_size_t
    ws2_32.bind.argtypes = [socket_t, ctypes.c_void_p, ctypes.c_int]
    ws2_32.bind.restype = ctypes.c_int
    ws2_32.WSAConnect.argtypes = [
        socket_t,
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.c_void_p,
    ]
    ws2_32.WSAConnect.restype = ctypes.c_int
    ws2_32.WSAIoctl.argtypes = [
        socket_t,
        wintypes.DWORD,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
        ctypes.c_void_p,
    ]
    ws2_32.WSAIoctl.restype = ctypes.c_int
    ws2_32.WSARecv.argtypes = [
        socket_t,
        ctypes.POINTER(WSABUF),
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(OVERLAPPED),
        ctypes.c_void_p,
    ]
    ws2_32.WSARecv.restype = ctypes.c_int
    ws2_32.WSASend.argtypes = [
        socket_t,
        ctypes.POINTER(WSABUF),
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.DWORD,
        ctypes.POINTER(OVERLAPPED),
        ctypes.c_void_p,
    ]
    ws2_32.WSASend.restype = ctypes.c_int
    ws2_32.WSARecvFrom.argtypes = [
        socket_t,
        ctypes.POINTER(WSABUF),
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(wintypes.DWORD),
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_int),
        ctypes.POINTER(OVERLAPPED),
        ctypes.c_void_p,
    ]
    ws2_32.WSARecvFrom.restype = ctypes.c_int
    ws2_32.WSASendTo.argtypes = [
        socket_t,
        ctypes.POINTER(WSABUF),
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        wintypes.DWORD,
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.POINTER(OVERLAPPED),
        ctypes.c_void_p,
    ]
    ws2_32.WSASendTo.restype = ctypes.c_int


def _as_handle(handle):
    if handle is None:
        return NULL
    return int(handle)


def _winerror(code=None):
    ctypes, _, kernel32, _ = _native()
    if code is None:
        code = kernel32.GetLastError()
    if hasattr(ctypes, "WinError"):
        error = ctypes.WinError(code)
        if code in (ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND):
            return FileNotFoundError(error.errno, error.strerror, error.filename, code)
        if code in (ERROR_BROKEN_PIPE, ERROR_NO_DATA):
            return BrokenPipeError(error.errno, error.strerror, error.filename, code)
        if code == ERROR_CONNECTION_REFUSED:
            return ConnectionRefusedError(error.errno, error.strerror, error.filename, code)
        if code == ERROR_CONNECTION_ABORTED:
            return ConnectionAbortedError(error.errno, error.strerror, error.filename, code)
        return error
    if code in (ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND):
        return FileNotFoundError(code, f"Windows error {code}")
    if code in (ERROR_BROKEN_PIPE, ERROR_NO_DATA):
        return BrokenPipeError(code, f"Windows error {code}")
    return OSError(code, f"Windows error {code}")


def _posix_native():
    global _posix
    if _posix is not None:
        return _posix
    if __graalpython__.posix_module_backend() != "native":
        raise RuntimeError("_overlapped socket operations require --python.PosixModuleBackend=native")
    if not __graalpython__.native_access_is_available():
        raise RuntimeError("_overlapped socket operations require native access")

    ctypes, _, _, _ = _native()
    import os

    posix_path = os.path.join(__graalpython__.core_home, "posix.dll")
    posix = ctypes.WinDLL(posix_path, use_errno=True)
    posix.graalpy_get_socket_handle.argtypes = [ctypes.c_int32, ctypes.POINTER(ctypes.c_int64)]
    posix.graalpy_get_socket_handle.restype = ctypes.c_int32
    posix.get_errno.argtypes = []
    posix.get_errno.restype = ctypes.c_int32
    _posix = posix
    return _posix


def _socket_handle(fd, required=False):
    ctypes, _, _, _ = _native()
    handle = ctypes.c_int64()
    posix = _posix_native()
    if posix.graalpy_get_socket_handle(int(fd), ctypes.byref(handle)) == 0:
        return handle.value
    errno = posix.get_errno()
    if not required and errno in (EBADF, ENOTSOCK):
        return int(fd)
    if errno == EBADF:
        raise OSError(errno, "Bad file descriptor")
    if errno == ENOTSOCK:
        raise OSError(errno, "Socket operation on non-socket")
    raise OSError(errno, "Could not get native socket handle")


def _pack_address(address):
    import socket

    ctypes, _, _, _ = _native()
    _, _, SOCKADDR_IN, SOCKADDR_IN6, _ = _structures()
    if len(address) == 2:
        host, port = address
        packed_host = socket.inet_pton(socket.AF_INET, host)
        sockaddr = SOCKADDR_IN()
        sockaddr.sin_family = socket.AF_INET
        sockaddr.sin_port = socket.htons(port)
        sockaddr.sin_addr[:] = packed_host
        return sockaddr, ctypes.sizeof(sockaddr)
    if len(address) == 4:
        host, port, flowinfo, scope_id = address
        packed_host = socket.inet_pton(socket.AF_INET6, host)
        sockaddr = SOCKADDR_IN6()
        sockaddr.sin6_family = socket.AF_INET6
        sockaddr.sin6_port = socket.htons(port)
        sockaddr.sin6_flowinfo = flowinfo
        sockaddr.sin6_addr[:] = packed_host
        sockaddr.sin6_scope_id = scope_id
        return sockaddr, ctypes.sizeof(sockaddr)
    raise ValueError("illegal address_as_bytes argument")


def _unpack_address(address_buffer):
    import socket

    ctypes, _, _, _ = _native()
    _, _, SOCKADDR_IN, SOCKADDR_IN6, _ = _structures()
    family = ctypes.c_ushort.from_buffer(address_buffer).value
    if family == socket.AF_INET:
        sockaddr = SOCKADDR_IN.from_buffer(address_buffer)
        host = socket.inet_ntop(socket.AF_INET, bytes(sockaddr.sin_addr))
        return host, socket.ntohs(sockaddr.sin_port)
    if family == socket.AF_INET6:
        sockaddr = SOCKADDR_IN6.from_buffer(address_buffer)
        host = socket.inet_ntop(socket.AF_INET6, bytes(sockaddr.sin6_addr))
        return host, socket.ntohs(sockaddr.sin6_port), socket.ntohl(sockaddr.sin6_flowinfo), sockaddr.sin6_scope_id
    raise ValueError("recvfrom returned unsupported address family")


def _writable_buffer(buffer):
    ctypes, _, _, _ = _native()
    view = memoryview(buffer)
    if view.readonly:
        raise TypeError("underlying buffer is not writable")
    if not view.contiguous:
        raise BufferError("memoryview: underlying buffer is not contiguous")
    size = view.nbytes
    if size == 0:
        return view, None, NULL, 0
    c_buffer = (ctypes.c_char * size).from_buffer(view)
    return view, c_buffer, ctypes.addressof(c_buffer), size


def _connect_ex(socket_handle):
    global _ConnectEx
    if _ConnectEx is not None:
        return _ConnectEx
    _load_mswsock_functions(socket_handle)
    return _ConnectEx


def _accept_ex(socket_handle):
    global _AcceptEx
    if _AcceptEx is not None:
        return _AcceptEx
    _load_mswsock_functions(socket_handle)
    return _AcceptEx


def _transmit_file(socket_handle):
    global _TransmitFile
    if _TransmitFile is not None:
        return _TransmitFile
    _load_mswsock_functions(socket_handle)
    return _TransmitFile


def _load_mswsock_functions(socket_handle):
    global _AcceptEx, _ConnectEx, _TransmitFile
    if _AcceptEx is not None and _ConnectEx is not None and _TransmitFile is not None:
        return

    ctypes, wintypes, _, ws2_32 = _native()
    OVERLAPPED, _, _, _, GUID = _structures()

    def load(guid):
        function_pointer = ctypes.c_void_p()
        bytes_returned = wintypes.DWORD()
        result = ws2_32.WSAIoctl(
            ctypes.c_size_t(socket_handle),
            wintypes.DWORD(SIO_GET_EXTENSION_FUNCTION_POINTER),
            ctypes.byref(guid),
            wintypes.DWORD(ctypes.sizeof(guid)),
            ctypes.byref(function_pointer),
            wintypes.DWORD(ctypes.sizeof(function_pointer)),
            ctypes.byref(bytes_returned),
            None,
            None,
        )
        if result == WSA_ERROR:
            raise _winerror(ws2_32.WSAGetLastError())
        return function_pointer.value

    guid_accept_ex = GUID(
        0xB5367DF1,
        0xCBAC,
        0x11CF,
        (ctypes.c_ubyte * 8)(0x95, 0xCA, 0x00, 0x80, 0x5F, 0x48, 0xA1, 0x92),
    )
    guid_connect_ex = GUID(
        0x25A207B9,
        0xDDF3,
        0x4660,
        (ctypes.c_ubyte * 8)(0x8E, 0xE9, 0x76, 0xE5, 0x8C, 0x74, 0x06, 0x3E),
    )
    guid_transmit_file = GUID(
        0xB5367DF0,
        0xCBAC,
        0x11CF,
        (ctypes.c_ubyte * 8)(0x95, 0xCA, 0x00, 0x80, 0x5F, 0x48, 0xA1, 0x92),
    )

    accept_ex_prototype = ctypes.WINFUNCTYPE(
        wintypes.BOOL,
        ctypes.c_size_t,
        ctypes.c_size_t,
        ctypes.c_void_p,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(OVERLAPPED),
    )

    connect_ex_prototype = ctypes.WINFUNCTYPE(
        wintypes.BOOL,
        ctypes.c_size_t,
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(wintypes.DWORD),
        ctypes.POINTER(OVERLAPPED),
    )

    transmit_file_prototype = ctypes.WINFUNCTYPE(
        wintypes.BOOL,
        ctypes.c_size_t,
        wintypes.HANDLE,
        wintypes.DWORD,
        wintypes.DWORD,
        ctypes.POINTER(OVERLAPPED),
        ctypes.c_void_p,
        wintypes.DWORD,
    )

    _AcceptEx = accept_ex_prototype(load(guid_accept_ex))
    _ConnectEx = connect_ex_prototype(load(guid_connect_ex))
    _TransmitFile = transmit_file_prototype(load(guid_transmit_file))


def CreateIoCompletionPort(file_handle, existing_completion_port, completion_key, number_of_concurrent_threads):
    ctypes, wintypes, kernel32, _ = _native()
    if _as_handle(file_handle) != INVALID_HANDLE_VALUE:
        file_handle = _socket_handle(file_handle)
    handle = kernel32.CreateIoCompletionPort(
        wintypes.HANDLE(_as_handle(file_handle)),
        wintypes.HANDLE(_as_handle(existing_completion_port)),
        ctypes.c_size_t(completion_key),
        wintypes.DWORD(number_of_concurrent_threads),
    )
    if not handle:
        raise _winerror()
    return _as_handle(handle)


def GetQueuedCompletionStatus(completion_port, milliseconds=INFINITE):
    ctypes, wintypes, kernel32, _ = _native()
    transferred = wintypes.DWORD()
    key = ctypes.c_size_t()
    overlapped = ctypes.c_void_p()
    result = kernel32.GetQueuedCompletionStatus(
        wintypes.HANDLE(_as_handle(completion_port)),
        ctypes.byref(transferred),
        ctypes.byref(key),
        ctypes.byref(overlapped),
        wintypes.DWORD(milliseconds),
    )
    if not result and not overlapped.value:
        code = kernel32.GetLastError()
        if code == 258:
            return None
        raise _winerror(code)
    return 0 if result else kernel32.GetLastError(), transferred.value, key.value, overlapped.value


def PostQueuedCompletionStatus(completion_port, transferred, completion_key, overlapped):
    ctypes, wintypes, _, _ = _native()
    if _PostQueuedCompletionStatus is None:
        raise NotImplementedError("_overlapped.PostQueuedCompletionStatus is not available")
    result = _PostQueuedCompletionStatus(
        wintypes.HANDLE(_as_handle(completion_port)),
        wintypes.DWORD(transferred),
        ctypes.c_size_t(completion_key),
        ctypes.c_void_p(overlapped),
    )
    if not result:
        raise _winerror()


def CreateEvent(event_attributes, manual_reset, initial_state, name):
    _, wintypes, kernel32, _ = _native()
    handle = kernel32.CreateEventW(event_attributes, bool(manual_reset), bool(initial_state), name)
    if not handle:
        raise _winerror()
    return _as_handle(handle)


def UnregisterWait(wait_handle):
    _, wintypes, _, _ = _native()
    if _UnregisterWait is None:
        raise NotImplementedError("_overlapped.UnregisterWait is not available")
    if not _UnregisterWait(wintypes.HANDLE(_as_handle(wait_handle))):
        raise _winerror()
    _wait_callbacks.pop(_as_handle(wait_handle), None)


def UnregisterWaitEx(wait_handle, completion_event):
    _, wintypes, _, _ = _native()
    if _UnregisterWaitEx is None:
        raise NotImplementedError("_overlapped.UnregisterWaitEx is not available")
    if not _UnregisterWaitEx(wintypes.HANDLE(_as_handle(wait_handle)), wintypes.HANDLE(_as_handle(completion_event))):
        raise _winerror()
    _wait_callbacks.pop(_as_handle(wait_handle), None)


def _not_implemented(name):
    def function(*args, **kwargs):
        raise NotImplementedError(f"_overlapped.{name} is not implemented in GraalPy")

    function.__name__ = name
    return function


def RegisterWaitWithQueue(handle, completion_port, overlapped, milliseconds):
    ctypes, wintypes, _, _ = _native()
    if _RegisterWaitForSingleObject is None:
        raise NotImplementedError("_overlapped.RegisterWaitWithQueue is not available")
    if _PostQueuedCompletionStatus is None:
        raise NotImplementedError("_overlapped.PostQueuedCompletionStatus is not available")

    callback_type = ctypes.WINFUNCTYPE(None, ctypes.c_void_p, wintypes.BOOLEAN)
    data = (ctypes.c_size_t(_as_handle(completion_port)), ctypes.c_void_p(_as_handle(overlapped)))

    def post_to_queue(parameter, timer_or_wait_fired):
        port, address = data
        _PostQueuedCompletionStatus(
            wintypes.HANDLE(port.value),
            wintypes.DWORD(1 if timer_or_wait_fired else 0),
            ctypes.c_size_t(0),
            address,
        )

    callback = callback_type(post_to_queue)
    wait_handle = wintypes.HANDLE()
    WT_EXECUTEINWAITTHREAD = 0x00000004
    WT_EXECUTEONLYONCE = 0x00000008
    result = _RegisterWaitForSingleObject(
        ctypes.byref(wait_handle),
        wintypes.HANDLE(_as_handle(handle)),
        callback,
        None,
        wintypes.ULONG(milliseconds),
        wintypes.ULONG(WT_EXECUTEINWAITTHREAD | WT_EXECUTEONLYONCE),
    )
    if not result:
        raise _winerror()
    wait_handle_value = _as_handle(wait_handle.value)
    _wait_callbacks[wait_handle_value] = callback, data
    return wait_handle_value


def ConnectPipe(address):
    _, wintypes, kernel32, _ = _native()
    GENERIC_READ = 0x80000000
    GENERIC_WRITE = 0x40000000
    OPEN_EXISTING = 3
    FILE_FLAG_OVERLAPPED = 0x40000000
    handle = _as_handle(
        kernel32.CreateFileW(
            address,
            wintypes.DWORD(GENERIC_READ | GENERIC_WRITE),
            wintypes.DWORD(0),
            None,
            wintypes.DWORD(OPEN_EXISTING),
            wintypes.DWORD(FILE_FLAG_OVERLAPPED),
            wintypes.HANDLE(NULL),
        )
    )
    if handle == INVALID_HANDLE_VALUE:
        raise _winerror()
    return handle


def BindLocal(handle, family):
    import socket

    ctypes, _, _, ws2_32 = _native()
    if family == socket.AF_INET:
        sockaddr, sockaddr_len = _pack_address(("0.0.0.0", 0))
    elif family == socket.AF_INET6:
        sockaddr, sockaddr_len = _pack_address(("::", 0, 0, 0))
    else:
        raise ValueError("Only AF_INET and AF_INET6 families are supported")
    result = ws2_32.bind(
        ctypes.c_size_t(_socket_handle(handle, required=True)),
        ctypes.byref(sockaddr),
        ctypes.c_int(sockaddr_len),
    )
    if result == WSA_ERROR:
        raise _winerror(ws2_32.WSAGetLastError())


def WSAConnect(handle, address):
    ctypes, _, _, ws2_32 = _native()
    sockaddr, sockaddr_len = _pack_address(address)
    result = ws2_32.WSAConnect(
        ctypes.c_size_t(_socket_handle(handle, required=True)),
        ctypes.byref(sockaddr),
        ctypes.c_int(sockaddr_len),
        None,
        None,
        None,
        None,
    )
    if result == WSA_ERROR:
        raise _winerror(ws2_32.WSAGetLastError())


class Overlapped:
    def __init__(self, event=INVALID_HANDLE_VALUE):
        ctypes, wintypes, kernel32, _ = _native()
        OVERLAPPED, _, _, _, _ = _structures()
        if event == INVALID_HANDLE_VALUE:
            event = kernel32.CreateEventW(None, True, False, None)
            if not event:
                raise _winerror()
        self.event = _as_handle(event)
        self._pending = False
        self.error = ERROR_SUCCESS
        self._type = _TYPE_NONE
        self._handle = NULL
        self._buffer = None
        self._user_buffer = None
        self._user_buffer_view = None
        self._address_buffer = None
        self._address_length = None
        self._last_transferred = 0
        self._overlapped = OVERLAPPED()
        if self.event:
            self._overlapped.hEvent = wintypes.HANDLE(self.event)
        self.address = ctypes.addressof(self._overlapped)

    def cancel(self):
        ctypes, wintypes, _, _ = _native()
        if self._type in (_TYPE_NONE, _TYPE_NOT_STARTED):
            return
        if self.pending and _CancelIoEx is not None:
            result = _CancelIoEx(wintypes.HANDLE(_as_handle(self._handle)), ctypes.byref(self._overlapped))
            if not result:
                code = _kernel32.GetLastError()
                if code != ERROR_NOT_FOUND:
                    raise _winerror(code)

    def getresult(self, wait=False):
        ctypes, wintypes, kernel32, _ = _native()
        if self._type == _TYPE_NONE:
            raise ValueError("operation not yet attempted")
        if self._type == _TYPE_NOT_STARTED:
            raise ValueError("operation failed to start")
        transferred = wintypes.DWORD()
        result = kernel32.GetOverlappedResult(
            wintypes.HANDLE(_as_handle(self._handle)),
            ctypes.byref(self._overlapped),
            ctypes.byref(transferred),
            bool(wait),
        )
        self._last_transferred = transferred.value
        self.error = ERROR_SUCCESS if result else kernel32.GetLastError()
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA):
            self.pending = False
        elif self.error == ERROR_BROKEN_PIPE and self._type in (_TYPE_READ, _TYPE_READINTO):
            self.pending = False
        else:
            if self.error != ERROR_IO_INCOMPLETE:
                self.pending = False
            raise _winerror(self.error)

        if self._type == _TYPE_READ:
            return self._buffer.raw[: transferred.value]
        if self._type == _TYPE_READ_FROM:
            return self._buffer.raw[: transferred.value], _unpack_address(self._address_buffer)
        if self._type == _TYPE_READ_FROM_INTO:
            return transferred.value, _unpack_address(self._address_buffer)
        return transferred.value

    GetOverlappedResult = getresult

    @property
    def pending(self):
        if not self._pending:
            return False
        if self._type in (_TYPE_NONE, _TYPE_NOT_STARTED):
            self._pending = False
            return False
        ctypes, wintypes, kernel32, _ = _native()
        transferred = wintypes.DWORD()
        result = kernel32.GetOverlappedResult(
            wintypes.HANDLE(_as_handle(self._handle)),
            ctypes.byref(self._overlapped),
            ctypes.byref(transferred),
            False,
        )
        if result:
            self._last_transferred = transferred.value
            self._pending = False
            return False
        if kernel32.GetLastError() != ERROR_IO_INCOMPLETE:
            self._pending = False
            return False
        return True

    @pending.setter
    def pending(self, value):
        self._pending = bool(value)

    def _start_socket_operation(self, handle, operation_type):
        if self._type != _TYPE_NONE:
            raise ValueError("operation already attempted")
        self._type = operation_type
        self._handle = _socket_handle(handle, required=True)
        self.pending = False

    def _start_handle_operation(self, handle, operation_type):
        if self._type != _TYPE_NONE:
            raise ValueError("operation already attempted")
        self._type = operation_type
        self._handle = _as_handle(handle)
        self.pending = False

    def ReadFile(self, handle, size):
        ctypes, wintypes, kernel32, _ = _native()
        size = int(size)
        self._start_handle_operation(handle, _TYPE_READ)
        self._buffer = ctypes.create_string_buffer(max(size, 1))
        transferred = wintypes.DWORD()
        result = kernel32.ReadFile(
            wintypes.HANDLE(_as_handle(self._handle)),
            ctypes.cast(self._buffer, ctypes.c_void_p),
            wintypes.DWORD(size),
            ctypes.byref(transferred),
            ctypes.byref(self._overlapped),
        )
        self.error = ERROR_SUCCESS if result else kernel32.GetLastError()
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def ReadFileInto(self, handle, buffer):
        ctypes, wintypes, kernel32, _ = _native()
        self._user_buffer_view, self._user_buffer, address, size = _writable_buffer(buffer)
        self._start_handle_operation(handle, _TYPE_READINTO)
        transferred = wintypes.DWORD()
        result = kernel32.ReadFile(
            wintypes.HANDLE(_as_handle(self._handle)),
            ctypes.c_void_p(address),
            wintypes.DWORD(size),
            ctypes.byref(transferred),
            ctypes.byref(self._overlapped),
        )
        self.error = ERROR_SUCCESS if result else kernel32.GetLastError()
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WriteFile(self, handle, buffer):
        ctypes, wintypes, kernel32, _ = _native()
        data = bytes(buffer)
        self._start_handle_operation(handle, _TYPE_WRITE)
        self._buffer = ctypes.create_string_buffer(data, len(data))
        transferred = wintypes.DWORD()
        result = kernel32.WriteFile(
            wintypes.HANDLE(_as_handle(self._handle)),
            ctypes.cast(self._buffer, ctypes.c_void_p),
            wintypes.DWORD(len(data)),
            ctypes.byref(transferred),
            ctypes.byref(self._overlapped),
        )
        self.error = ERROR_SUCCESS if result else kernel32.GetLastError()
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WSARecv(self, handle, size, flags=0):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, _, _ = _structures()
        size = int(size)
        self._start_socket_operation(handle, _TYPE_READ)
        self._buffer = ctypes.create_string_buffer(max(size, 1))
        transferred = wintypes.DWORD()
        flags_value = wintypes.DWORD(flags)
        wsabuf = WSABUF(wintypes.ULONG(size), ctypes.cast(self._buffer, ctypes.c_void_p))
        result = ws2_32.WSARecv(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            ctypes.byref(flags_value),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WSARecvInto(self, handle, buffer, flags=0):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, _, _ = _structures()
        self._start_socket_operation(handle, _TYPE_READINTO)
        self._user_buffer_view, self._user_buffer, address, size = _writable_buffer(buffer)
        transferred = wintypes.DWORD()
        flags_value = wintypes.DWORD(flags)
        wsabuf = WSABUF(wintypes.ULONG(size), ctypes.c_void_p(address))
        result = ws2_32.WSARecv(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            ctypes.byref(flags_value),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WSARecvFrom(self, handle, size, flags=0):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, SOCKADDR_IN6, _ = _structures()
        size = int(size)
        self._start_socket_operation(handle, _TYPE_READ_FROM)
        self._buffer = ctypes.create_string_buffer(max(size, 1))
        self._address_buffer = ctypes.create_string_buffer(ctypes.sizeof(SOCKADDR_IN6))
        self._address_length = ctypes.c_int(ctypes.sizeof(self._address_buffer))
        transferred = wintypes.DWORD()
        flags_value = wintypes.DWORD(flags)
        wsabuf = WSABUF(wintypes.ULONG(size), ctypes.cast(self._buffer, ctypes.c_void_p))
        result = ws2_32.WSARecvFrom(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            ctypes.byref(flags_value),
            ctypes.cast(self._address_buffer, ctypes.c_void_p),
            ctypes.byref(self._address_length),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WSARecvFromInto(self, handle, buffer, size=0, flags=0):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, SOCKADDR_IN6, _ = _structures()
        self._user_buffer_view, self._user_buffer, address, buffer_size = _writable_buffer(buffer)
        size = int(size) if size else buffer_size
        if size > buffer_size:
            raise ValueError("nbytes is greater than the length of the buffer")
        self._start_socket_operation(handle, _TYPE_READ_FROM_INTO)
        self._address_buffer = ctypes.create_string_buffer(ctypes.sizeof(SOCKADDR_IN6))
        self._address_length = ctypes.c_int(ctypes.sizeof(self._address_buffer))
        transferred = wintypes.DWORD()
        flags_value = wintypes.DWORD(flags)
        wsabuf = WSABUF(wintypes.ULONG(size), ctypes.c_void_p(address))
        result = ws2_32.WSARecvFrom(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            ctypes.byref(flags_value),
            ctypes.cast(self._address_buffer, ctypes.c_void_p),
            ctypes.byref(self._address_length),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_MORE_DATA, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)


    def WSASend(self, handle, buffer, flags=0):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, _, _ = _structures()
        data = bytes(buffer)
        self._start_socket_operation(handle, _TYPE_WRITE)
        self._buffer = ctypes.create_string_buffer(data, len(data))
        transferred = wintypes.DWORD()
        wsabuf = WSABUF(wintypes.ULONG(len(data)), ctypes.cast(self._buffer, ctypes.c_void_p))
        result = ws2_32.WSASend(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            wintypes.DWORD(flags),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def WSASendTo(self, handle, buffer, flags, address):
        ctypes, wintypes, _, ws2_32 = _native()
        _, WSABUF, _, _, _ = _structures()
        data = bytes(buffer)
        sockaddr, sockaddr_len = _pack_address(address)
        self._start_socket_operation(handle, _TYPE_WRITE_TO)
        self._buffer = ctypes.create_string_buffer(data, len(data))
        self._address_buffer = sockaddr
        transferred = wintypes.DWORD()
        wsabuf = WSABUF(wintypes.ULONG(len(data)), ctypes.cast(self._buffer, ctypes.c_void_p))
        result = ws2_32.WSASendTo(
            ctypes.c_size_t(self._handle),
            ctypes.byref(wsabuf),
            wintypes.DWORD(1),
            ctypes.byref(transferred),
            wintypes.DWORD(flags),
            ctypes.byref(sockaddr),
            ctypes.c_int(sockaddr_len),
            ctypes.byref(self._overlapped),
            None,
        )
        self.error = ws2_32.WSAGetLastError() if result == WSA_ERROR else ERROR_SUCCESS
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def AcceptEx(self, listen_handle, accept_handle):
        ctypes, wintypes, _, ws2_32 = _native()
        _, _, _, SOCKADDR_IN6, _ = _structures()
        if self._type != _TYPE_NONE:
            raise ValueError("operation already attempted")
        listen_socket = _socket_handle(listen_handle, required=True)
        accept_socket = _socket_handle(accept_handle, required=True)
        address_size = ctypes.sizeof(SOCKADDR_IN6) + 16
        self._start_socket_operation(listen_handle, _TYPE_ACCEPT)
        self._buffer = ctypes.create_string_buffer(address_size * 2)
        accept_ex = _accept_ex(listen_socket)
        transferred = wintypes.DWORD()
        ws2_32.WSASetLastError(ERROR_SUCCESS)
        result = accept_ex(
            ctypes.c_size_t(listen_socket),
            ctypes.c_size_t(accept_socket),
            ctypes.cast(self._buffer, ctypes.c_void_p),
            wintypes.DWORD(0),
            wintypes.DWORD(address_size),
            wintypes.DWORD(address_size),
            ctypes.byref(transferred),
            ctypes.byref(self._overlapped),
        )
        self.error = ERROR_SUCCESS if result else ws2_32.WSAGetLastError()
        if self.error == ERROR_SUCCESS and not result:
            self.error = ERROR_IO_PENDING
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            self._last_transferred = transferred.value
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def ConnectEx(self, handle, address):
        ctypes, wintypes, _, ws2_32 = _native()
        if self._type != _TYPE_NONE:
            raise ValueError("operation already attempted")
        sockaddr, sockaddr_len = _pack_address(address)
        self._type = _TYPE_CONNECT
        self._handle = _socket_handle(handle, required=True)
        self._address_buffer = sockaddr
        connect_ex = _connect_ex(self._handle)
        bytes_sent = wintypes.DWORD()
        result = connect_ex(
            ctypes.c_size_t(self._handle),
            ctypes.byref(sockaddr),
            ctypes.c_int(sockaddr_len),
            None,
            wintypes.DWORD(0),
            ctypes.byref(bytes_sent),
            ctypes.byref(self._overlapped),
        )
        self.error = ERROR_SUCCESS if result else ws2_32.WSAGetLastError()
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def TransmitFile(self, socket, file, offset, offset_high, count_to_write, count_per_send, flags):
        ctypes, wintypes, _, ws2_32 = _native()
        self._start_socket_operation(socket, _TYPE_TRANSMIT_FILE)
        self._overlapped.Offset = wintypes.DWORD(offset)
        self._overlapped.OffsetHigh = wintypes.DWORD(offset_high)
        transmit_file = _transmit_file(self._handle)
        ws2_32.WSASetLastError(ERROR_SUCCESS)
        result = transmit_file(
            ctypes.c_size_t(self._handle),
            wintypes.HANDLE(_as_handle(file)),
            wintypes.DWORD(count_to_write),
            wintypes.DWORD(count_per_send),
            ctypes.byref(self._overlapped),
            None,
            wintypes.DWORD(flags),
        )
        self.error = ERROR_SUCCESS if result else ws2_32.WSAGetLastError()
        if self.error == ERROR_SUCCESS and not result:
            self.error = ERROR_IO_PENDING
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            return None
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def ConnectNamedPipe(self, handle):
        ctypes, wintypes, kernel32, _ = _native()
        self._start_handle_operation(handle, _TYPE_CONNECT_NAMED_PIPE)
        result = kernel32.ConnectNamedPipe(wintypes.HANDLE(_as_handle(self._handle)), ctypes.byref(self._overlapped))
        self.error = ERROR_SUCCESS if result else kernel32.GetLastError()
        if self.error == 535:  # ERROR_PIPE_CONNECTED
            self.pending = False
            return True
        if self.error in (ERROR_SUCCESS, ERROR_IO_PENDING):
            self.pending = True
            return False
        self._type = _TYPE_NOT_STARTED
        self.pending = False
        raise _winerror(self.error)

    def __del__(self):
        try:
            if self.event:
                _, wintypes, kernel32, _ = _native()
                kernel32.CloseHandle(wintypes.HANDLE(self.event))
                self.event = NULL
        except Exception:
            pass
