/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public abstract class DigestObject extends PythonBuiltinObject {
    private byte[] finalDigest = null;

    DigestObject(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public static DigestObject create(PythonBuiltinClassType digestType, Shape instanceShape, Object digest) {
        if (digest instanceof MessageDigest md) {
            return new MessageDigestObject(digestType, instanceShape, md);
        } else if (digest instanceof Mac mac) {
            return new MacDigestObject(digestType, instanceShape, mac);
        } else {
            throw CompilerDirectives.shouldNotReachHere("unsupported digest type");
        }
    }

    public PythonBuiltinClassType getType() {
        return (PythonBuiltinClassType) getInitialPythonClass();
    }


    // The JDK does not expose the block sizes used by digests, so
    // they are hardcoded here. We use a switch over the type because
    // that is likely to fold away during compilation
    public int getBlockSize() {
        PythonBuiltinClassType mainDigestType = getMainDigestType();
        switch (mainDigestType) {
            case MD5Type:
            case SHA1Type:
            case SHA224Type:
            case SHA256Type:
                return 64;
            case SHA384Type:
            case SHA512Type:
                return 128;
            case Sha3SHA224Type:
                return 144;
            case Sha3SHA256Type:
                return 136;
            case Sha3SHA384Type:
                return 104;
            case Sha3SHA512Type:
                return 72;
            case Sha3Shake128Type:
                return 168;
            case Sha3Shake256Type:
                return 136;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private PythonBuiltinClassType getMainDigestType() {
        PythonBuiltinClassType actualType = getType();
        switch (actualType) {
            case HashlibHash:
            case HashlibHmac:
                return determineMainDigestType();
            default:
                return actualType;
        }
    }

    private PythonBuiltinClassType determineMainDigestType() {
        String algorithm = getAlgorithm();
        switch (algorithm) {
            case "md5":
            case "hmac-md5":
                return PythonBuiltinClassType.MD5Type;
            case "sha1":
            case "hmac-sha1":
                return PythonBuiltinClassType.SHA1Type;
            case "sha224":
            case "hmac-sha224":
                return PythonBuiltinClassType.SHA224Type;
            case "sha256":
            case "hmac-sha256":
                return PythonBuiltinClassType.SHA256Type;
            case "sha384":
            case "hmac-sha384":
                return PythonBuiltinClassType.SHA384Type;
            case "sha512":
            case "hmac-sha512":
                return PythonBuiltinClassType.SHA512Type;
            case "sha3-224":
            case "hmac-sha3-224":
                return PythonBuiltinClassType.Sha3SHA224Type;
            case "sha3-256":
            case "hmac-sha3-256":
                return PythonBuiltinClassType.Sha3SHA256Type;
            case "sha3-384":
            case "hmac-sha3-384":
                return PythonBuiltinClassType.Sha3SHA384Type;
            case "sha3-512":
            case "hmac-sha3-512":
                return PythonBuiltinClassType.Sha3SHA512Type;
            case "shake128":
                return PythonBuiltinClassType.Sha3Shake128Type;
            case "shake256":
                return PythonBuiltinClassType.Sha3Shake256Type;
            default:
                // default to assume the same blocksize as MD5
                return PythonBuiltinClassType.MD5Type;
        }
    }

    byte[] digest() {
        if (finalDigest == null) {
            finalDigest = finalizeDigest();
        }
        return finalDigest;
    }

    abstract DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException;

    abstract byte[] finalizeDigest();

    abstract void update(byte[] data);

    abstract int getDigestLength();

    abstract String getAlgorithm();

    private static final class MessageDigestObject extends DigestObject {
        private final MessageDigest digest;

        MessageDigestObject(PythonBuiltinClassType digestType, Shape instanceShape, MessageDigest digest) {
            super(digestType, instanceShape);
            this.digest = digest;
        }

        @Override
        @TruffleBoundary
        DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException {
            return factory.createDigestObject(getType(), digest.clone());
        }

        @Override
        @TruffleBoundary
        byte[] finalizeDigest() {
            return digest.digest();
        }

        @Override
        @TruffleBoundary
        void update(byte[] data) {
            digest.update(data);
        }

        @Override
        @TruffleBoundary
        int getDigestLength() {
            return digest.getDigestLength();
        }

        @Override
        @TruffleBoundary
        public String getAlgorithm() {
            return digest.getAlgorithm().toLowerCase();
        }
    }

    private static final class MacDigestObject extends DigestObject {
        private final Mac mac;

        MacDigestObject(PythonBuiltinClassType digestType, Shape instanceShape, Mac mac) {
            super(digestType, instanceShape);
            this.mac = mac;
        }

        @Override
        @TruffleBoundary
        DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException {
            return factory.createDigestObject(getType(), mac.clone());
        }

        @Override
        @TruffleBoundary
        byte[] finalizeDigest() {
            return mac.doFinal();
        }

        @Override
        @TruffleBoundary
        void update(byte[] data) {
            mac.update(data);
        }

        @Override
        @TruffleBoundary
        int getDigestLength() {
            return mac.getMacLength();
        }

        @Override
        @TruffleBoundary
        public String getAlgorithm() {
            String algorithmWithHmacPrefix = mac.getAlgorithm();
            return algorithmWithHmacPrefix.replace("hmac", "hmac-").toLowerCase();
        }
    }
}
