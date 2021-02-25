package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;

public enum SSLErrorCode {
    ERROR_UNKNOWN(0, PythonBuiltinClassType.SSLError),
    ERROR_SSL(1, PythonBuiltinClassType.SSLError),
    ERROR_WANT_READ(2, PythonBuiltinClassType.SSLWantReadError),
    ERROR_WANT_WRITE(3, PythonBuiltinClassType.SSLWantWriteError),
    ERROR_WANT_X509_LOOKUP(4, PythonBuiltinClassType.SSLError),
    ERROR_SYSCALL(5, PythonBuiltinClassType.SSLSyscallError),
    ERROR_ZERO_RETURN(6, PythonBuiltinClassType.SSLZeroReturnError),
    ERROR_WANT_CONNECT(7, PythonBuiltinClassType.SSLError),
    ERROR_EOF(8, PythonBuiltinClassType.SSLEOFError),
    ERROR_SSL_PEM_LIB(9, PythonBuiltinClassType.SSLError),
    ERROR_NO_START_LINE(108, PythonBuiltinClassType.SSLError),
    ERROR_NO_CERTIFICATE_OR_CRL_FOUND(136, PythonBuiltinClassType.SSLError),
    ERROR_KEY_TYPE_MISMATCH(115, PythonBuiltinClassType.SSLError),
    ERROR_KEY_VALUES_MISMATCH(116, PythonBuiltinClassType.SSLError),
    ERROR_BAD_BASE64_DECODE(100, PythonBuiltinClassType.SSLError),

    ERROR_NOT_ENOUGH_DATA(142, PythonBuiltinClassType.SSLError),

    ERROR_CERT_VERIFICATION(1, PythonBuiltinClassType.SSLCertVerificationError);

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
