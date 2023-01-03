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
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha1ModuleBuiltinsClinicProviders.Sha1FunctionNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_sha1")
public class Sha1ModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Sha1ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "sha1", minNumOfPositionalArgs = 0, parameterNames = {"string"}, keywordOnlyNames = {"usedforsecurity"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "usedforsecurity", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    abstract static class Sha1FunctionNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return Sha1FunctionNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!isPNone(buffer)", limit = "1")
        Object sha1(VirtualFrame frame, Object buffer, @SuppressWarnings("unused") boolean usedForSecurity,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Shared("raise") @Cached PRaiseNode raise) {
            try {
                return createSha1(factory(), raise, bufferLib.getInternalOrCopiedByteArray(buffer));
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isPNone(buffer)")
        Object sha1Default(Object buffer, boolean usedForSecurity,
                        @Shared("raise") @Cached PRaiseNode raise) {
            return createSha1(factory(), raise, null);
        }

        static Object createSha1(PythonObjectFactory factory, PRaiseNode raise, byte[] bytes) {
            MessageDigest digest;
            try {
                digest = createDigest(bytes);
            } catch (NoSuchAlgorithmException e) {
                throw raise.raise(PythonBuiltinClassType.UnsupportedDigestmodError, e);
            }
            return factory.trace(new DigestObject(PythonBuiltinClassType.SHA1Type, digest));
        }

        @TruffleBoundary
        private static MessageDigest createDigest(byte[] bytes) throws NoSuchAlgorithmException {
            MessageDigest digest;
            digest = MessageDigest.getInstance("sha1");
            if (bytes != null) {
                digest.update(bytes);
            }
            return digest;
        }
    }

    @Builtin(name = "sha1", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.SHA1Type, isPublic = false)
    @GenerateNodeFactory
    abstract static class Sha1Node extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object sha1(Object args, Object kwargs) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "_sha1.sha1");
        }
    }
}
