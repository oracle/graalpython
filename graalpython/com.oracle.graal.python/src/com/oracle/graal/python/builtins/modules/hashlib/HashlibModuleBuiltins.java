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

import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltinsClinicProviders.HmacDigestNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltinsClinicProviders.HmacNewNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltinsClinicProviders.NewNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

@CoreFunctions(defineModule = "_hashlib")
public class HashlibModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return HashlibModuleBuiltinsFactory.getFactories();
    }

    private static final Map<String, String> NAME_MAPPINGS = Map.of(
                    "sha3_sha224", "sha3-sha224",
                    "sha3_sha256", "sha3-sha256",
                    "sha3_sha384", "sha3-sha384",
                    "sha3_sha512", "sha3-sha512",
                    "shake_128", "SHAKE128",
                    "shake_256", "SHAKE256"
    );

    private static final String[] DIGEST_ALGORITHMS;
    static {
        Security.addProvider(CertUtils.BOUNCYCASTLE_PROVIDER);
        var digests = new ArrayList<String>();
        for (var provider : Security.getProviders()) {
            for (var service : provider.getServices()) {
                if (service.getType().equalsIgnoreCase(MessageDigest.class.getSimpleName())) {
                    digests.add(service.getAlgorithm());
                }
            }
        }
        DIGEST_ALGORITHMS = digests.toArray(new String[digests.size()]);
    }

    @Override
    public void initialize(Python3Core core) {
        var algos = new LinkedHashMap<String, Object>();
        for (var digest : DIGEST_ALGORITHMS) {
            algos.put(digest, PNone.NONE);
        }
        addBuiltinConstant("openssl_md_meth_names", core.factory().createFrozenSet(EconomicMapStorage.create(algos)));

        // we do not use openssl, but rely on the Java providers for everything, so we just alias these
        var dylib = DynamicObjectLibrary.getUncached();
        addDigestAlias(core, dylib, "md5", "_md5");
        addDigestAlias(core, dylib, "sha1", "_sha1");
        addDigestAlias(core, dylib, "sha224", "_sha256");
        addDigestAlias(core, dylib, "sha256", "_sha256");
        addDigestAlias(core, dylib, "sha384", "_sha512");
        addDigestAlias(core, dylib, "sha512", "_sha512");
        addDigestAlias(core, dylib, "sha3_sha224", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha256", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha384", "_sha3");
        addDigestAlias(core, dylib, "sha3_sha512", "_sha3");
        addDigestAlias(core, dylib, "shake_128", "_sha3");
        addDigestAlias(core, dylib, "shake_256", "_sha3");

        super.initialize(core);
    }

    private final void addDigestAlias(Python3Core core, DynamicObjectLibrary dylib, String digest, String module) {
        addBuiltinConstant("openssl_" + digest, dylib.getOrDefault(core.lookupBuiltinModule(toTruffleStringUncached(module)).getStorage(), toTruffleStringUncached(digest), PNone.NO_VALUE));
    }

    @Builtin(name = "compare_digest", parameterNames = {"a", "b"})
    @GenerateNodeFactory
    abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isString(a)", "isString(b)"})
        Object cmpStrings(Object a, Object b,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached CastToTruffleStringNode castA,
                        @Cached CastToTruffleStringNode castB) {
            InternalByteArray bytesA = getInternalByteArrayNode.execute(castA.execute(a), Encoding.US_ASCII);
            InternalByteArray bytesB = getInternalByteArrayNode.execute(castB.execute(b), Encoding.US_ASCII);
            return cmp(bytesA.getArray(), bytesA.getOffset(), bytesA.getLength(), bytesB.getArray(), bytesB.getOffset(), bytesB.getLength());
        }

        @Specialization
        boolean cmpBuffers(VirtualFrame frame, Object a, Object b,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary accessLib) {
            if (acquireLib.hasBuffer(a) && acquireLib.hasBuffer(b)) {
                Object bufferA = acquireLib.acquireReadonly(a, frame, this);
                try {
                    Object bufferB = acquireLib.acquireReadonly(b, frame, this);
                    try {
                        byte[] bytesA = accessLib.getInternalOrCopiedByteArray(bufferA);
                        byte[] bytesB = accessLib.getInternalOrCopiedByteArray(bufferB);
                        return cmp(bytesA, 0, bytesA.length, bytesB, 0, bytesB.length);
                    } finally {
                        accessLib.release(bufferB);
                    }
                } finally {
                    accessLib.release(bufferA);
                }
            } else {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_OR_COMBINATION_OF_TYPES, a, b);
            }
        }

        @TruffleBoundary
        boolean cmp(byte[] a, int offA, int lenA, byte[] b, int offB, int lenB) {
            MessageDigest mda, mdb;
            try {
                mda = MessageDigest.getInstance("sha256");
                mdb = MessageDigest.getInstance("sha256");
            } catch (NoSuchAlgorithmException e) {
                return false;
            }
            mda.update(a, offA, lenA);
            byte[] da = mda.digest();
            mdb.update(b, offB, lenB);
            byte[] db = mdb.digest();
            int res = 0;
            for (int i = 0; i < da.length; i++) {
                res |= da[i] ^ db[i];
            }
            return res == 0;
        }
    }

    @Builtin(name = "hmac_digest", parameterNames = {"key", "msg", "digest"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "msg", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "digest", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class HmacDigestNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return HmacDigestNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        Object hmacDigest(Object key, Object msg, TruffleString digest,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            try {
                String algorithm = "hmac" + digest.toJavaStringUncached().toLowerCase();
                SecretKeySpec secretKeySpec = new SecretKeySpec(bufferLib.getInternalOrCopiedByteArray(key), algorithm);
                Mac mac = Mac.getInstance(algorithm);
                mac.init(secretKeySpec);
                byte[] result = mac.doFinal(bufferLib.getInternalOrCopiedByteArray(msg));
                return factory().createBytes(result);
            } catch (InvalidKeyException | NoSuchAlgorithmException e) {
                throw raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
            } finally {
                bufferLib.release(key);
                bufferLib.release(msg);
            }
        }
    }

    @Builtin(name = "hmac_new", parameterNames = {"key", "msg", "digest"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "msg", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "digest", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class HmacNewNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return HmacNewNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        Object hmacDigest(Object key, Object msg, TruffleString digest,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            try {
                String algorithm = digest.toJavaStringUncached();
                SecretKeySpec secretKeySpec = new SecretKeySpec(bufferLib.getInternalOrCopiedByteArray(key), algorithm);
                Mac mac = Mac.getInstance(algorithm);
                mac.init(secretKeySpec);
                mac.update(bufferLib.getInternalOrCopiedByteArray(msg));
                return factory().trace(new DigestObject(PythonBuiltinClassType.HashlibHmac, mac));
            } catch (InvalidKeyException | NoSuchAlgorithmException e) {
                throw raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
            } finally {
                bufferLib.release(key);
                bufferLib.release(msg);
            }
        }
    }

    @Builtin(name = "new", minNumOfPositionalArgs = 1, parameterNames = {"name", "string"}, keywordOnlyNames = {"usedforsecurity"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "usedforsecurity", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    abstract static class NewNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object newDigest(VirtualFrame frame, TruffleString name, Object buffer, @SuppressWarnings("unused") boolean usedForSecurity,
                        @Cached CastToJavaStringNode castStr,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            try {
                byte[] bytes;
                if (buffer != PNone.NO_VALUE) {
                    bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                } else {
                    bytes = null;
                }
                return factory().trace(new DigestObject(PythonBuiltinClassType.HashlibHash, createDigest(castStr.execute(name), bytes)));
            } catch (NoSuchAlgorithmException e) {
                throw raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @TruffleBoundary
        private static MessageDigest createDigest(String inputName, byte[] bytes) throws NoSuchAlgorithmException {
            MessageDigest digest;
            String name = NAME_MAPPINGS.getOrDefault(inputName, inputName);
            digest = MessageDigest.getInstance(name);
            if (bytes != null) {
                digest.update(bytes);
            }
            return digest;
        }

    }

    @Builtin(name = "HASH", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }

    @Builtin(name = "HASHXOF", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashxofNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }

    @Builtin(name = "HMAC", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HmacNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object hash(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }
}
