/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import com.oracle.graal.python.builtins.objects.ssl.CertUtils.NeedsPasswordException;

public final class SSLBouncyCastleSupportProvider {
    public static final String MISSING_MESSAGE = "Encrypted legacy PEM private keys require BouncyCastle support; add the GraalPy BC support module and BouncyCastle jars to the classpath or modulepath, or convert the key to PKCS#8.";

    private SSLBouncyCastleSupportProvider() {
    }

    public static PrivateKey loadPrivateKey(char[] password, String pemText) throws IOException, NeedsPasswordException, GeneralSecurityException {
        return getSupport().loadPrivateKey(password, pemText);
    }

    private static SSLBouncyCastleSupport getSupport() throws MissingBouncyCastleException {
        Throwable failure = null;
        try {
            SSLBouncyCastleSupport support = getSupport(ServiceLoader.load(ModuleLayer.boot(), SSLBouncyCastleSupport.class));
            if (support != null) {
                return support;
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            failure = e;
        }
        try {
            SSLBouncyCastleSupport support = getSupport(ServiceLoader.load(SSLBouncyCastleSupport.class));
            if (support != null) {
                return support;
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            failure = e;
        }
        try {
            SSLBouncyCastleSupport support = getSupport(ServiceLoader.load(SSLBouncyCastleSupport.class, SSLBouncyCastleSupportProvider.class.getClassLoader()));
            if (support != null) {
                return support;
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            failure = e;
        }
        throw new MissingBouncyCastleException(failure);
    }

    private static SSLBouncyCastleSupport getSupport(ServiceLoader<SSLBouncyCastleSupport> serviceLoader) {
        Iterator<SSLBouncyCastleSupport> iterator = serviceLoader.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static final class MissingBouncyCastleException extends GeneralSecurityException {
        private static final long serialVersionUID = 1L;

        MissingBouncyCastleException(Throwable cause) {
            super(MISSING_MESSAGE, cause);
        }
    }
}
