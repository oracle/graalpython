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


from _winapi import INFINITE, INVALID_HANDLE_VALUE, NULL


ERROR_NETNAME_DELETED = 64
ERROR_SEM_TIMEOUT = 121
ERROR_OPERATION_ABORTED = 995
ERROR_IO_PENDING = 997
ERROR_PORT_UNREACHABLE = 1234
ERROR_PIPE_BUSY = 231

SO_UPDATE_ACCEPT_CONTEXT = 0x700B
SO_UPDATE_CONNECT_CONTEXT = 0x7010

_ctypes = None
_wintypes = None
_kernel32 = None
_PostQueuedCompletionStatus = None
_UnregisterWait = None
_UnregisterWaitEx = None


def _native():
    global _ctypes, _wintypes, _kernel32
    global _PostQueuedCompletionStatus, _UnregisterWait, _UnregisterWaitEx
    if _kernel32 is not None:
        return _ctypes, _wintypes, _kernel32

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.windll.kernel32
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

    _ctypes = ctypes
    _wintypes = wintypes
    _kernel32 = kernel32
    _PostQueuedCompletionStatus = post_queued_completion_status
    _UnregisterWait = unregister_wait
    _UnregisterWaitEx = unregister_wait_ex
    return _ctypes, _wintypes, _kernel32


def _as_handle(handle):
    if handle is None:
        return NULL
    return int(handle)


def _winerror(code=None):
    ctypes, _, kernel32 = _native()
    if code is None:
        code = kernel32.GetLastError()
    if hasattr(ctypes, "WinError"):
        return ctypes.WinError(code)
    return OSError(code, f"Windows error {code}")


def CreateIoCompletionPort(file_handle, existing_completion_port, completion_key, number_of_concurrent_threads):
    ctypes, wintypes, kernel32 = _native()
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
    ctypes, wintypes, kernel32 = _native()
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
    ctypes, wintypes, _ = _native()
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
    _, wintypes, kernel32 = _native()
    handle = kernel32.CreateEventW(event_attributes, bool(manual_reset), bool(initial_state), name)
    if not handle:
        raise _winerror()
    return _as_handle(handle)


def UnregisterWait(wait_handle):
    _, wintypes, _ = _native()
    if _UnregisterWait is None:
        raise NotImplementedError("_overlapped.UnregisterWait is not available")
    if not _UnregisterWait(wintypes.HANDLE(_as_handle(wait_handle))):
        raise _winerror()


def UnregisterWaitEx(wait_handle, completion_event):
    _, wintypes, _ = _native()
    if _UnregisterWaitEx is None:
        raise NotImplementedError("_overlapped.UnregisterWaitEx is not available")
    if not _UnregisterWaitEx(wintypes.HANDLE(_as_handle(wait_handle)), wintypes.HANDLE(_as_handle(completion_event))):
        raise _winerror()


def _not_implemented(name):
    def function(*args, **kwargs):
        raise NotImplementedError(f"_overlapped.{name} is not implemented in GraalPy")

    function.__name__ = name
    return function


RegisterWaitWithQueue = _not_implemented("RegisterWaitWithQueue")
WSAConnect = _not_implemented("WSAConnect")
BindLocal = _not_implemented("BindLocal")
ConnectPipe = _not_implemented("ConnectPipe")


class Overlapped:
    def __init__(self, event):
        self.event = event
        self.pending = False
        self.address = id(self)

    def cancel(self):
        self.pending = False

    def getresult(self, wait=False):
        return 0

    GetOverlappedResult = getresult

    WSARecv = _not_implemented("Overlapped.WSARecv")
    WSARecvInto = _not_implemented("Overlapped.WSARecvInto")
    WSARecvFrom = _not_implemented("Overlapped.WSARecvFrom")
    WSARecvFromInto = _not_implemented("Overlapped.WSARecvFromInto")
    WSASend = _not_implemented("Overlapped.WSASend")
    WSASendTo = _not_implemented("Overlapped.WSASendTo")
    ReadFile = _not_implemented("Overlapped.ReadFile")
    ReadFileInto = _not_implemented("Overlapped.ReadFileInto")
    WriteFile = _not_implemented("Overlapped.WriteFile")
    AcceptEx = _not_implemented("Overlapped.AcceptEx")
    ConnectEx = _not_implemented("Overlapped.ConnectEx")
    TransmitFile = _not_implemented("Overlapped.TransmitFile")
    ConnectNamedPipe = _not_implemented("Overlapped.ConnectNamedPipe")
