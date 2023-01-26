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

/**
 * Implementation class for all types coming out of the hashlib modules.
 *
 * We only use the JDK classes to implement these, expecting appropriate providers to be registered.
 * CPython is more flexible and implements each hashing strategy separately, and thus expose some
 * more custom state such as the block size or generating digests of varying lengths for SHAKE. For
 * now we only emulate this as far as possible with base JDK interfaces in favor of giving embedders
 * the flexibility to replace Security providers and not forcing dependencies on e.g. BouncyCastle
 * for hashing.
 */
public abstract class DigestObject extends PythonBuiltinObject {
    private final String name;

    DigestObject(Object cls, Shape instanceShape, String name) {
        super(cls, instanceShape);
        this.name = name;
    }

    public static DigestObject create(PythonBuiltinClassType digestType, Shape instanceShape, String name, Object digest) {
        if (digest instanceof MessageDigest md) {
            return new MessageDigestObject(digestType, instanceShape, name, md);
        } else if (digest instanceof Mac mac) {
            return new MacDigestObject(digestType, instanceShape, name, mac);
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
            case Blake2bType:
                return 128;
            case Blake2sType:
                return 64;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private PythonBuiltinClassType getMainDigestType() {
        PythonBuiltinClassType actualType = getType();
        switch (actualType) {
            case HashlibHash:
            case HashlibHashXof:
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
            case "sha3_224":
            case "hmac-sha3_224":
                return PythonBuiltinClassType.Sha3SHA224Type;
            case "sha3_256":
            case "hmac-sha3_256":
                return PythonBuiltinClassType.Sha3SHA256Type;
            case "sha3_384":
            case "hmac-sha3_384":
                return PythonBuiltinClassType.Sha3SHA384Type;
            case "sha3_512":
            case "hmac-sha3_512":
                return PythonBuiltinClassType.Sha3SHA512Type;
            case "shake_128":
                return PythonBuiltinClassType.Sha3Shake128Type;
            case "shake_256":
                return PythonBuiltinClassType.Sha3Shake256Type;
            case "blake2s":
                return PythonBuiltinClassType.Blake2sType;
            case "blake2b":
                return PythonBuiltinClassType.Blake2bType;
            default:
                // default to assume the same small blocksize as MD5
                return PythonBuiltinClassType.MD5Type;
        }
    }

    /**
     * CPython supports updating after retrieving the digest but the JDK does not. We have to
     * calculate the digest on a clone, but that does not need to be supported. If it is not, then
     * we calculate the digest normally, but we must prevent any further updates.
     *
     * @see #wasReset(), {@link #update(byte[])}
     */
    abstract byte[] digest();

    /**
     * @return true if the digest has already been calculated and the underlying implementation does
     *         not support cloning, in which case this object can no longer be
     *         {@linkplain #update(byte[]) updated}
     */
    abstract boolean wasReset();

    /**
     * Must not be called if {@link #wasReset()} returns true.
     */
    abstract void update(byte[] data);

    abstract DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException;

    abstract int getDigestLength();

    final String getAlgorithm() {
        return name;
    }

    /**
     * Ensures that {@link #update(byte[])} is not called after {@link #digest()} if cloning is not
     * supported. Also caches the digest and ensures that the cache is cleared on update.
     */
    private abstract static class DigestObjectBase extends DigestObject {
        private byte[] cachedDigest = null;
        private boolean wasReset;

        DigestObjectBase(Object cls, Shape instanceShape, String name) {
            super(cls, instanceShape, name);
        }

        @Override
        final boolean wasReset() {
            return wasReset;
        }

        @Override
        final byte[] digest() {
            if (cachedDigest == null) {
                try {
                    cachedDigest = calculateDigestOnClone();
                } catch (CloneNotSupportedException e) {
                    wasReset = true;
                    cachedDigest = calculateDigest();
                }
            }
            return cachedDigest;
        }

        @Override
        final void update(byte[] data) {
            if (wasReset) {
                throw CompilerDirectives.shouldNotReachHere("update() called after digest() on an implementation the does not support clone()");
            }
            cachedDigest = null;
            doUpdate(data);
        }

        abstract byte[] calculateDigestOnClone() throws CloneNotSupportedException;

        abstract byte[] calculateDigest();

        abstract void doUpdate(byte[] data);
    }

    private static final class MessageDigestObject extends DigestObjectBase {
        private final MessageDigest digest;

        MessageDigestObject(PythonBuiltinClassType digestType, Shape instanceShape, String name, MessageDigest digest) {
            super(digestType, instanceShape, name);
            this.digest = digest;
        }

        @Override
        @TruffleBoundary
        DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException {
            return factory.createDigestObject(getType(), getAlgorithm(), digest.clone());
        }

        @Override
        @TruffleBoundary
        byte[] calculateDigestOnClone() throws CloneNotSupportedException {
            return ((MessageDigest) digest.clone()).digest();
        }

        @Override
        @TruffleBoundary
        byte[] calculateDigest() {
            return digest.digest();
        }

        @Override
        @TruffleBoundary
        void doUpdate(byte[] data) {
            digest.update(data);
        }

        @Override
        @TruffleBoundary
        int getDigestLength() {
            return digest.getDigestLength();
        }
    }

    private static final class MacDigestObject extends DigestObjectBase {
        private final Mac mac;

        MacDigestObject(PythonBuiltinClassType digestType, Shape instanceShape, String name, Mac mac) {
            super(digestType, instanceShape, name);
            this.mac = mac;
        }

        @Override
        @TruffleBoundary
        DigestObject copy(PythonObjectFactory factory) throws CloneNotSupportedException {
            return factory.createDigestObject(getType(), getAlgorithm(), mac.clone());
        }

        @Override
        @TruffleBoundary
        byte[] calculateDigestOnClone() throws CloneNotSupportedException {
            return ((Mac) mac.clone()).doFinal();
        }

        @Override
        @TruffleBoundary
        byte[] calculateDigest() {
            return mac.doFinal();
        }

        @Override
        @TruffleBoundary
        void doUpdate(byte[] data) {
            mac.update(data);
        }

        @Override
        @TruffleBoundary
        int getDigestLength() {
            return mac.getMacLength();
        }
    }
}
