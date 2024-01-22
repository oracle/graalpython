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

import static com.oracle.graal.python.nodes.BuiltinNames.J_SHA3;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = J_SHA3)
public final class Sha3ModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Sha3ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "sha3_224", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA224Type)
    @Builtin(name = "sha3_256", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA256Type)
    @Builtin(name = "sha3_384", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA384Type)
    @Builtin(name = "sha3_512", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3SHA512Type)
    @Builtin(name = "shake_128", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3Shake128Type)
    @Builtin(name = "shake_256", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "string"}, keywordOnlyNames = {
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Sha3Shake256Type)
    @GenerateNodeFactory
    abstract static class ShaNode extends PythonBuiltinNode {
        @Specialization
        static Object newDigest(VirtualFrame frame, Object type, Object buffer, @SuppressWarnings("unused") Object usedForSecurity,
                        @Bind("this") Node inliningTarget,
                        @Cached HashlibModuleBuiltins.CreateDigestNode createNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonBuiltinClassType resultType;
            if (type instanceof PythonBuiltinClass builtinType) {
                resultType = builtinType.getType();
            } else if (type instanceof PythonBuiltinClassType enumType) {
                resultType = enumType;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.WRONG_TYPE);
            }
            return createNode.execute(frame, inliningTarget, resultType, pythonNameFromType(resultType), javaNameFromType(resultType), buffer);
        }

        private static String javaNameFromType(PythonBuiltinClassType type) {
            switch (type) {
                case Sha3SHA224Type:
                    return "sha3-224";
                case Sha3SHA256Type:
                    return "sha3-256";
                case Sha3SHA384Type:
                    return "sha3-384";
                case Sha3SHA512Type:
                    return "sha3-512";
                case Sha3Shake128Type:
                    return "SHAKE128";
                case Sha3Shake256Type:
                    return "SHAKE256";
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static String pythonNameFromType(PythonBuiltinClassType type) {
            switch (type) {
                case Sha3SHA224Type:
                    return "sha3_224";
                case Sha3SHA256Type:
                    return "sha3_256";
                case Sha3SHA384Type:
                    return "sha3_384";
                case Sha3SHA512Type:
                    return "sha3_512";
                case Sha3Shake128Type:
                    return "shake_128";
                case Sha3Shake256Type:
                    return "shake_256";
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }
}
