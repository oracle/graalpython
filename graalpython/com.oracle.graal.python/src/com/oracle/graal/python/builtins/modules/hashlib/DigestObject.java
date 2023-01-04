/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.hashlib;

import java.security.MessageDigest;

import javax.crypto.Mac;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class DigestObject extends PythonAbstractObject {
    private final PythonBuiltinClassType digestType;
    private final MessageDigest digest;
    private final Mac mac;
    private byte[] finalDigest = null;

    DigestObject(final PythonBuiltinClassType digestType, final MessageDigest digest) {
        this.digestType = digestType;
        this.digest = digest;
        this.mac = null;
    }

    DigestObject(final PythonBuiltinClassType type, final Mac mac) {
        this.digestType = type;
        this.mac = mac;
        this.digest = null;
    }

    Mac getMac() {
        return mac;
    }

    MessageDigest getDigest() {
        return digest;
    }

    public PythonBuiltinClassType getType() {
        return digestType;
    }

    public int compareTo(final Object o) {
        return this.hashCode() - o.hashCode();
    }

    @TruffleBoundary
    public DigestObject copy() throws CloneNotSupportedException {
        if (digest != null) {
            return new DigestObject(digestType, (MessageDigest) digest.clone());
        } else {
            return new DigestObject(digestType, (Mac) mac.clone());
        }
    }

    @TruffleBoundary
    public byte[] digest() {
        if (finalDigest == null) {
            if (digest != null) {
                finalDigest = digest.digest();
            } else {
                finalDigest = mac.doFinal();
            }
        }
        return finalDigest;
    }

    @TruffleBoundary
    public void update(byte[] data) {
        if (digest != null) {
            digest.update(data);
        } else {
            mac.update(data);
        }
    }

    @TruffleBoundary
    public int getDigestLength() {
        if (digest != null) {
            return digest.getDigestLength();
        } else {
            return mac.getMacLength();
        }
    }

    @TruffleBoundary
    public int getBlockSize() {
        String algorithm = getAlgorithm().toLowerCase();
        switch (algorithm) {
            case "md5":
            case "hmac-md5":
            case "sha1":
            case "hmac-sha1":
            case "sha224":
            case "hmac-sha224":
            case "sha256":
            case "hmac-sha256":
                return 64;
            case "sha384":
            case "hmac-sha384":
            case "sha512":
            case "hmac-sha512":
                return 128;
            case "sha3-224":
            case "hmac-sha3-224":
                return 1152;
            case "sha3-256":
            case "hmac-sha3-256":
                return 1088;
            case "sha3-384":
            case "hmac-sha3-384":
                return 832;
            case "sha3-512":
            case "hmac-sha3-512":
                return 576;
            case "shake128":
                return 1344;
            case "shake256":
                return 1088;
            default:
                return 64;
        }
    }

    public String getAlgorithm() {
        if (digest != null) {
            return digest.getAlgorithm();
        } else {
            String algorithmWithHmacPrefix = mac.getAlgorithm();
            return algorithmWithHmacPrefix.replace("hmac", "hmac-");
        }
    }
}
