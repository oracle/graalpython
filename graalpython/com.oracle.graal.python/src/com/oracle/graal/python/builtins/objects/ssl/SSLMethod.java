/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.ExplodeLoop;

public enum SSLMethod {
    SSL3(1, SSLProtocol.SSLv3),
    TLS(2),
    TLS1(3, SSLProtocol.TLSv1),
    TLS1_1(4, SSLProtocol.TLSv1_1),
    TLS1_2(5, SSLProtocol.TLSv1_2),
    TLS_CLIENT(0x10),
    TLS_SERVER(0x11);

    private final int pythonId;
    private final SSLProtocol singleVersion;

    SSLMethod(int pythonId, SSLProtocol singleVersion) {
        this.pythonId = pythonId;
        this.singleVersion = singleVersion;
    }

    SSLMethod(int pythonId) {
        this.pythonId = pythonId;
        this.singleVersion = null;
    }

    public int getPythonId() {
        return pythonId;
    }

    public boolean allowsProtocol(SSLProtocol protocol) {
        return singleVersion == null || singleVersion == protocol;
    }

    public boolean isSingleVersion() {
        return singleVersion != null;
    }

    @ExplodeLoop
    public static SSLMethod fromPythonId(int pythonId) {
        for (SSLMethod method : SSLMethod.values()) {
            if (method.getPythonId() == pythonId) {
                return method;
            }
        }
        return null;
    }
}
