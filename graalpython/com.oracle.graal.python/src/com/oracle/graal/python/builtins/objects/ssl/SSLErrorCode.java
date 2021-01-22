package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;

public enum SSLErrorCode {
    ERROR_SSL(1, PythonBuiltinClassType.SSLError),
    ERROR_WANT_READ(2, PythonBuiltinClassType.SSLWantReadError),
    ERROR_WANT_WRITE(3, PythonBuiltinClassType.SSLWantWriteError),
    ERROR_WANT_X509_LOOKUP(4, PythonBuiltinClassType.SSLError),
    ERROR_SYSCALL(5, PythonBuiltinClassType.SSLSyscallError),
    ERROR_ZERO_RETURN(6, PythonBuiltinClassType.SSLZeroReturnError),
    ERROR_WANT_CONNECT(7, PythonBuiltinClassType.SSLError),
    ERROR_EOF(8, PythonBuiltinClassType.SSLEOFError),
    ERROR_NO_START_LINE(108, PythonBuiltinClassType.SSLNoStartLine);

    private final int errno;
    private final PythonBuiltinClassType type;

    SSLErrorCode(int errno, PythonBuiltinClassType type) {
        this.errno = errno;
        this.type = type;
    }

    public int getErrno() {
        return errno;
    }

    public PythonBuiltinClassType getType() {
        return type;
    }
}
