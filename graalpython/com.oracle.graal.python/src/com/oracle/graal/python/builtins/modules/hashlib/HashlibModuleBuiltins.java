/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
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
import com.oracle.graal.python.builtins.modules.hashlib.HashlibModuleBuiltinsClinicProviders.NewNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodeRange;

@CoreFunctions(defineModule = HashlibModuleBuiltins.J_HASHLIB)
public final class HashlibModuleBuiltins extends PythonBuiltins {

    static final String J_HASHLIB = "_hashlib";
    private static final TruffleString T_HASHLIB = tsLiteral(J_HASHLIB);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return HashlibModuleBuiltinsFactory.getFactories();
    }

    private static final String OPENSSL_PREFIX = "openssl_";
    private static final Map<String, String> NAME_MAPPINGS = Map.of(
                    "sha3_224", "sha3-sha224",
                    "sha3_256", "sha3-sha256",
                    "sha3_384", "sha3-sha384",
                    "sha3_512", "sha3-sha512",
                    "shake_128", "SHAKE128",
                    "shake_256", "SHAKE256");

    private static final String CONSTRUCTORS = "_constructors";
    private static final HiddenKey ORIGINAL_CONSTRUCTORS = new HiddenKey(CONSTRUCTORS);
    private static final String _SHA3 = "_sha3";
    private static final String[] DIGEST_ALIASES = new String[]{
                    "md5", "_md5",
                    "sha1", "_sha1",
                    "sha224", "_sha256",
                    "sha256", "_sha256",
                    "sha384", "_sha512",
                    "sha512", "_sha512",
                    "sha3_224", _SHA3,
                    "sha3_256", _SHA3,
                    "sha3_384", _SHA3,
                    "sha3_512", _SHA3,
                    "shake_128", _SHA3,
                    "shake_256", _SHA3
    };
    private static final String[] DIGEST_ALGORITHMS;
    static {
        Security.addProvider(CertUtils.BOUNCYCASTLE_PROVIDER);
        ArrayList<String> digests = new ArrayList<>();
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
        EconomicMapStorage algos = EconomicMapStorage.create(DIGEST_ALGORITHMS.length);
        for (var digest : DIGEST_ALGORITHMS) {
            algos.putUncachedWithJavaEq(digest, PNone.NONE);
        }
        addBuiltinConstant("openssl_md_meth_names", core.factory().createFrozenSet(algos));

        EconomicMapStorage storage = EconomicMapStorage.create();
        addBuiltinConstant(CONSTRUCTORS, core.factory().createMappingproxy(core.factory().createDict(storage)));
        addBuiltinConstant(ORIGINAL_CONSTRUCTORS, storage);
        super.initialize(core);
    }

    private void addDigestAlias(PythonModule self, Object mod, ReadAttributeFromDynamicObjectNode readNode, EconomicMapStorage storage, String digest) {
        TruffleString tsDigest = toTruffleStringUncached(digest);
        Object function = readNode.execute(mod, tsDigest);
        if (function != PNone.NO_VALUE) {
            self.setAttribute(toTruffleStringUncached(OPENSSL_PREFIX + digest), function);
            HashingStorageNodes.HashingStorageSetItem.executeUncached(storage, function, tsDigest);
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule self = core.lookupBuiltinModule(T_HASHLIB);
        ReadAttributeFromDynamicObjectNode readNode = ReadAttributeFromDynamicObjectNode.getUncached();
        EconomicMapStorage storage = (EconomicMapStorage) readNode.execute(self, ORIGINAL_CONSTRUCTORS);
        Object sha3module = AbstractImportNode.importModule(toTruffleStringUncached(_SHA3));
        for (int i = 0; i < DIGEST_ALIASES.length; i += 2) {
            String module = DIGEST_ALIASES[i + 1];
            Object mod = module.equals(_SHA3) ? sha3module : core.lookupBuiltinModule(toTruffleStringUncached(module));
            addDigestAlias(self, mod, readNode, storage, DIGEST_ALIASES[i]);
        }
    }

    @Builtin(name = "compare_digest", parameterNames = {"a", "b"})
    @GenerateNodeFactory
    abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isString(a)", "isString(b)"})
        static Object cmpStrings(Object a, Object b,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.CopyToByteArrayNode getByteArrayNode,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached CastToTruffleStringNode castA,
                        @Cached CastToTruffleStringNode castB,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString tsA = castA.execute(inliningTarget, a);
            TruffleString tsB = castB.execute(inliningTarget, b);
            CodeRange crA = getCodeRangeNode.execute(tsA, TS_ENCODING);
            CodeRange crB = getCodeRangeNode.execute(tsB, TS_ENCODING);
            if (!(crA.isSubsetOf(CodeRange.ASCII) && crB.isSubsetOf(CodeRange.ASCII))) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.COMPARING_STRINGS_WITH_NON_ASCII);
            }
            byte[] bytesA = getByteArrayNode.execute(tsA, TS_ENCODING);
            byte[] bytesB = getByteArrayNode.execute(castB.execute(inliningTarget, b), TS_ENCODING);
            return cmp(bytesA, bytesB);
        }

        @Specialization(guards = {"!isString(a) || !isString(b)"})
        static boolean cmpBuffers(VirtualFrame frame, Object a, Object b,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary accessLib,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (acquireLib.hasBuffer(a) && acquireLib.hasBuffer(b)) {
                Object bufferA = acquireLib.acquireReadonly(a, frame, indirectCallData);
                try {
                    Object bufferB = acquireLib.acquireReadonly(b, frame, indirectCallData);
                    try {
                        byte[] bytesA = accessLib.getInternalOrCopiedByteArray(bufferA);
                        byte[] bytesB = accessLib.getInternalOrCopiedByteArray(bufferB);
                        return cmp(bytesA, bytesB);
                    } finally {
                        accessLib.release(bufferB, frame, indirectCallData);
                    }
                } finally {
                    accessLib.release(bufferA, frame, indirectCallData);
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_OR_COMBINATION_OF_TYPES, a, b);
            }
        }

        @TruffleBoundary
        static boolean cmp(byte[] a, byte[] b) {
            return MessageDigest.isEqual(a, b);
        }
    }

    @Builtin(name = "hmac_digest", declaresExplicitSelf = true, parameterNames = {"$mod", "key", "msg", "digest"})
    @GenerateNodeFactory
    abstract static class HmacDigestNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        static Object hmacDigest(VirtualFrame frame, PythonModule self, Object key, Object msg, Object digest,
                        @Bind("this") Node inliningTarget,
                        @Cached HmacNewNode newNode,
                        @Cached DigestObjectBuiltins.DigestNode digestNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (msg instanceof PNone) {
                // hmac_digest is a bit more strict
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.A_BYTES_LIKE_OBJECT_IS_REQUIRED_NOT_P, msg);
            }
            Object hmacObject = newNode.execute(frame, self, key, msg, digest);
            return digestNode.execute(frame, hmacObject);
        }
    }

    @Builtin(name = "hmac_new", declaresExplicitSelf = true, parameterNames = {"$mod", "key", "msg", "digestmod"}, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class HmacNewNode extends PythonQuaternaryBuiltinNode {
        private static final TruffleString HMAC_PREFIX = tsLiteral("hmac-");

        @SuppressWarnings("unused")
        @Specialization
        static Object hmacNewError(PythonModule self, Object key, Object msg, PNone digest,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_POS, "hmac_new", "digestmod", 3);
        }

        @Specialization(guards = "!isString(digestmod)")
        static Object hmacNewFromFunction(VirtualFrame frame, PythonModule self, Object key, Object msg, Object digestmod,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadAttributeFromDynamicObjectNode readNode,
                        @Cached HashingStorageNodes.HashingStorageGetItem getItemNode,
                        @Exclusive @Cached CastToTruffleStringNode castStr,
                        @Exclusive @Cached CastToJavaStringNode castJStr,
                        @Shared("concatStr") @Cached TruffleString.ConcatNode concatStr,
                        @Shared("acquireLib") @CachedLibrary(limit = "2") PythonBufferAcquireLibrary acquireLib,
                        @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            // cast guaranteed in our initialize
            EconomicMapStorage constructors = (EconomicMapStorage) readNode.execute(self, ORIGINAL_CONSTRUCTORS);
            Object name = getItemNode.execute(frame, inliningTarget, constructors, digestmod);
            if (name != null) {
                assert name instanceof TruffleString; // guaranteed in our initialize
                return hmacNew(self, key, msg, name, inliningTarget, castStr, castJStr, concatStr, acquireLib, bufferLib, factory, raiseNode);
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnsupportedDigestmodError);
            }
        }

        @Specialization(guards = "isString(digestmodObj)")
        static Object hmacNew(@SuppressWarnings("unused") PythonModule self, Object keyObj, Object msgObj, Object digestmodObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode castStr,
                        @Exclusive @Cached CastToJavaStringNode castJStr,
                        @Shared("concatStr") @Cached TruffleString.ConcatNode concatStr,
                        @Shared("acquireLib") @CachedLibrary(limit = "2") PythonBufferAcquireLibrary acquireLib,
                        @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString digestmod = castStr.execute(inliningTarget, digestmodObj);
            Object key;
            if (!acquireLib.hasBuffer(keyObj)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.A_BYTES_LIKE_OBJECT_IS_REQUIRED_NOT_P, keyObj);
            } else {
                key = acquireLib.acquireReadonly(keyObj);
            }
            try {
                Object msg;
                if (msgObj instanceof PNone) {
                    msg = null;
                } else if (acquireLib.hasBuffer(msgObj)) {
                    msg = acquireLib.acquireReadonly(msgObj);
                } else {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.A_BYTES_LIKE_OBJECT_IS_REQUIRED_NOT_P, msgObj);
                }
                try {
                    byte[] msgBytes = msg == null ? null : bufferLib.getInternalOrCopiedByteArray(msg);
                    int msgLen = msg == null ? 0 : bufferLib.getBufferLength(msg);
                    Mac mac = createMac(digestmod, bufferLib.getInternalOrCopiedByteArray(key), bufferLib.getBufferLength(key), msgBytes, msgLen);
                    return factory.createDigestObject(PythonBuiltinClassType.HashlibHmac, castJStr.execute(concatStr.execute(HMAC_PREFIX, digestmod, TS_ENCODING, true)), mac);
                } catch (InvalidKeyException | NoSuchAlgorithmException e) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
                } finally {
                    if (msg != null) {
                        bufferLib.release(msg);
                    }
                }
            } finally {
                bufferLib.release(key);
            }
        }
    }

    @TruffleBoundary
    static Mac createMac(TruffleString digest, byte[] key, int keyLen, byte[] msg, int msgLen) throws NoSuchAlgorithmException, InvalidKeyException {
        String inputName = digest.toJavaStringUncached().toLowerCase();
        String algorithm = "hmac" + NAME_MAPPINGS.getOrDefault(inputName, inputName);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, 0, keyLen, algorithm);
        Mac mac = Mac.getInstance(algorithm);
        mac.init(secretKeySpec);
        if (msg != null) {
            mac.update(msg, 0, msgLen);
        }
        return mac;
    }

    @GenerateUncached(false)
    @GenerateCached(false)
    @GenerateInline
    abstract static class CreateDigestNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, PythonBuiltinClassType type, String pythonName, String javaName, Object buffer);

        @Specialization
        static Object doIt(VirtualFrame frame, Node inliningTarget, PythonBuiltinClassType type, String pythonName, String javaName, Object value,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @CachedLibrary(limit = "2") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raise) {
            Object buffer;
            if (value instanceof PNone) {
                buffer = null;
            } else if (acquireLib.hasBuffer(value)) {
                buffer = acquireLib.acquireReadonly(value, frame, indirectCallData);
            } else {
                throw raise.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.A_BYTES_LIKE_OBJECT_IS_REQUIRED_NOT_P, value);
            }
            try {
                byte[] bytes = buffer == null ? null : bufferLib.getInternalOrCopiedByteArray(buffer);
                int bytesLen = buffer == null ? 0 : bufferLib.getBufferLength(buffer);
                MessageDigest digest;
                try {
                    digest = createDigest(javaName, bytes, bytesLen);
                } catch (NoSuchAlgorithmException e) {
                    throw raise.get(inliningTarget).raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
                }
                return factory.createDigestObject(type, pythonName, digest);
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, indirectCallData);
                }
            }
        }

        @TruffleBoundary
        private static MessageDigest createDigest(String name, byte[] bytes, int bytesLen) throws NoSuchAlgorithmException {
            MessageDigest digest = MessageDigest.getInstance(name);
            if (bytes != null) {
                digest.update(bytes, 0, bytesLen);
            }
            return digest;
        }
    }

    @Builtin(name = "new", minNumOfPositionalArgs = 1, parameterNames = {"name", "string"}, keywordOnlyNames = {"usedforsecurity"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "usedforsecurity", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    abstract static class NewNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NewNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object newDigest(VirtualFrame frame, TruffleString name, Object buffer, @SuppressWarnings("unused") boolean usedForSecurity,
                        @Bind("this") Node inliningTarget,
                        @Cached CreateDigestNode createNode,
                        @Cached CastToJavaStringNode castStr) {
            String pythonDigestName = getPythonName(castStr.execute(name));
            String javaDigestName = getJavaName(pythonDigestName);
            PythonBuiltinClassType digestType = getTypeFor(javaDigestName);
            return createNode.execute(frame, inliningTarget, digestType, pythonDigestName, javaDigestName, buffer);
        }

        private static PythonBuiltinClassType getTypeFor(String digestName) {
            switch (digestName) {
                case "SHAKE256":
                case "SHAKE128":
                    return PythonBuiltinClassType.HashlibHashXof;
                default:
                    return PythonBuiltinClassType.HashlibHash;
            }
        }

        @TruffleBoundary
        private static String getPythonName(String inputName) {
            return inputName.toLowerCase();
        }

        @TruffleBoundary
        private static String getJavaName(String inputName) {
            return NAME_MAPPINGS.getOrDefault(inputName, inputName);
        }
    }

    @Builtin(name = "get_fips_mode")
    @GenerateNodeFactory
    abstract static class GetFipsNode extends PythonBuiltinNode {
        @Specialization
        static int getFips() {
            return 0;
        }
    }

    @Builtin(name = "HASH", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHash, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object hash(Object args, Object kwargs,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASH");
        }
    }

    @Builtin(name = "HASHXOF", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHashXof, isPublic = false)
    @GenerateNodeFactory
    abstract static class HashXofNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object hash(Object args, Object kwargs,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HASHXOF");
        }
    }

    @Builtin(name = "HMAC", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.HashlibHmac, isPublic = false)
    @GenerateNodeFactory
    abstract static class HmacNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object hash(Object args, Object kwargs,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_hashlib.HMAC");
        }
    }
}
