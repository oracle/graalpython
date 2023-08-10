/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.truffle.api.strings.TruffleString;

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
    ERROR_NO_START_LINE("NO_START_LINE", 108, PythonBuiltinClassType.SSLError),
    ERROR_NO_CERTIFICATE_OR_CRL_FOUND("NO_CERTIFICATE_OR_CRL_FOUND", 136, PythonBuiltinClassType.SSLError),
    ERROR_KEY_TYPE_MISMATCH("KEY_TYPE_MISMATCH", 115, PythonBuiltinClassType.SSLError),
    ERROR_KEY_VALUES_MISMATCH("KEY_VALUES_MISMATCH", 116, PythonBuiltinClassType.SSLError),
    ERROR_BAD_BASE64_DECODE("BAD_BASE64_DECODE", 100, PythonBuiltinClassType.SSLError),

    ERROR_NOT_ENOUGH_DATA("NOT_ENOUGH_DATA", 142, PythonBuiltinClassType.SSLError),

    ERROR_CERT_VERIFICATION("CERTIFICATE_VERIFY_FAILED", 1, PythonBuiltinClassType.SSLCertVerificationError);

    private final TruffleString mnemonic;
    private final int errno;
    private final PythonBuiltinClassType type;

    SSLErrorCode(int errno, PythonBuiltinClassType type) {
        this(null, errno, type);
    }

    SSLErrorCode(String mnemonic, int errno, PythonBuiltinClassType type) {
        this.mnemonic = toTruffleStringUncached(mnemonic);
        this.errno = errno;
        this.type = type;
    }

    public TruffleString getMnemonic() {
        return mnemonic;
    }

    public int getErrno() {
        return errno;
    }

    public PythonBuiltinClassType getType() {
        return type;
    }
}
