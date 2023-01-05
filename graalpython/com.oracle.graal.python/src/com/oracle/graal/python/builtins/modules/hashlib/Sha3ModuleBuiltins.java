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
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_sha3")
public class Sha3ModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Sha3ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "sha3_sha224", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA224Type)
    @Builtin(name = "sha3_sha256", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA256Type)
    @Builtin(name = "sha3_sha384", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA384Type)
    @Builtin(name = "sha3_sha512", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA512Type)
    @Builtin(name = "sha3_shake128", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3Shake128Type)
    @Builtin(name = "sha3_shake256", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3Shake256Type)
    @GenerateNodeFactory
    abstract static class ShaNode extends PythonBuiltinNode {
        @Specialization
        Object sha(VirtualFrame frame, Object type, Object value, @SuppressWarnings("unused") Object usedForSecurity,
                        @CachedLibrary(limit = "2") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode raise) {
            Object buffer = null;
            if (acquireLib.hasBuffer(value)) {
                buffer = acquireLib.acquireReadonly(value, frame, getContext(), getLanguage(), this);
            }
            try {
                byte[] bytes = buffer == null ? null : bufferLib.getInternalOrCopiedByteArray(buffer);
                MessageDigest digest;
                PythonBuiltinClassType resultType = null;
                if (type instanceof PythonBuiltinClass builtinType) {
                    resultType = builtinType.getType();
                } else if (type instanceof PythonBuiltinClassType enumType) {
                    resultType = enumType;
                } else {
                    throw raise.raise(PythonBuiltinClassType.UnsupportedDigestmodError, ErrorMessages.WRONG_TYPE);
                }
                try {
                    digest = createDigest(resultType, bytes);
                } catch (NoSuchAlgorithmException e) {
                    throw raise.raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
                }
                return factory().createDigestObject(resultType, digest);
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }

        @TruffleBoundary
        private static MessageDigest createDigest(PythonBuiltinClassType type, byte[] bytes) throws NoSuchAlgorithmException {
            MessageDigest digest = null;
            switch (type) {
                case Sha3SHA224Type:
                    digest = MessageDigest.getInstance("sha3-224");
                    break;
                case Sha3SHA256Type:
                    digest = MessageDigest.getInstance("sha3-256");
                    break;
                case Sha3SHA384Type:
                    digest = MessageDigest.getInstance("sha3-384");
                    break;
                case Sha3SHA512Type:
                    digest = MessageDigest.getInstance("sha3-512");
                    break;
                case Sha3Shake128Type:
                    digest = MessageDigest.getInstance("SHAKE128");
                    break;
                case Sha3Shake256Type:
                    digest = MessageDigest.getInstance("SHAKE256");
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (bytes != null) {
                digest.update(bytes);
            }
            return digest;
        }
    }
}
