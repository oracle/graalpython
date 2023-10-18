/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Blake2ModuleBuiltinsClinicProviders.BlakeNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "_blake2")
public final class Blake2ModuleBuiltins extends PythonBuiltins {
    // constants taken from CPython's blake2.h
    static final int BLAKE2B_SALTBYTES = 16;
    static final int BLAKE2B_PERSONALBYTES = 16;
    static final int BLAKE2B_KEYBYTES = 64;
    static final int BLAKE2B_OUTBYTES = 64;
    static final int BLAKE2S_SALTBYTES = 8;
    static final int BLAKE2S_PERSONALBYTES = 8;
    static final int BLAKE2S_KEYBYTES = 32;
    static final int BLAKE2S_OUTBYTES = 32;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Blake2ModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("BLAKE2B_SALT_SIZE", BLAKE2B_SALTBYTES);
        addBuiltinConstant("BLAKE2B_PERSON_SIZE", BLAKE2B_PERSONALBYTES);
        addBuiltinConstant("BLAKE2B_MAX_KEY_SIZE", BLAKE2B_KEYBYTES);
        addBuiltinConstant("BLAKE2B_MAX_DIGEST_SIZE", BLAKE2B_OUTBYTES);
        addBuiltinConstant("BLAKE2S_SALT_SIZE", BLAKE2S_SALTBYTES);
        addBuiltinConstant("BLAKE2S_PERSON_SIZE", BLAKE2S_PERSONALBYTES);
        addBuiltinConstant("BLAKE2S_MAX_KEY_SIZE", BLAKE2S_KEYBYTES);
        addBuiltinConstant("BLAKE2S_MAX_DIGEST_SIZE", BLAKE2S_OUTBYTES);

        super.initialize(core);
    }

    @Builtin(name = "blake2b", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "data"}, keywordOnlyNames = {"digest_size", "key", "salt", "person", "fanout",
                    "depth", "leaf_size", "node_offset", "node_depth", "inner_size", "last_node",
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Blake2bType)
    @Builtin(name = "blake2s", declaresExplicitSelf = true, minNumOfPositionalArgs = 1, parameterNames = {"$cls", "data"}, keywordOnlyNames = {"digest_size", "key", "salt", "person", "fanout",
                    "depth", "leaf_size", "node_offset", "node_depth", "inner_size", "last_node",
                    "usedforsecurity"}, constructsClass = PythonBuiltinClassType.Blake2sType)
    @ArgumentClinic(name = "digest_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = "PNone.NONE")
    @ArgumentClinic(name = "salt", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = "PNone.NONE")
    @ArgumentClinic(name = "person", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer, defaultValue = "PNone.NONE")
    @ArgumentClinic(name = "fanout", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1")
    @ArgumentClinic(name = "depth", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1")
    @ArgumentClinic(name = "leaf_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "node_offset", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "node_depth", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "inner_size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @ArgumentClinic(name = "last_node", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "usedforsecurity", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class BlakeNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BlakeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object newDigest(VirtualFrame frame, Object type, Object data, int digestSize,
                        PNone key, PNone salt, PNone person, int fanout, int depth, int leafSize, int nodeOffset, int nodeDepth, int innerSize, boolean lastNode, boolean usedforsecurity,
                        @Bind("this") Node inliningTarget,
                        @Cached HashlibModuleBuiltins.CreateDigestNode createNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (fanout != 1 || depth != 1 || leafSize != 0 || nodeOffset != 0 || nodeDepth != 0 || innerSize != 0 || lastNode) {
                throw fail(frame, type, data, digestSize, key, salt, person, fanout, depth, leafSize, nodeOffset, nodeDepth, innerSize, lastNode, usedforsecurity, raiseNode.get(inliningTarget));
            }
            PythonBuiltinClassType resultType = null;
            if (type instanceof PythonBuiltinClass builtinType) {
                resultType = builtinType.getType();
            } else if (type instanceof PythonBuiltinClassType enumType) {
                resultType = enumType;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.WRONG_TYPE);
            }
            String javaName;
            String pythonName;
            int javaDigestSize;
            if (resultType == PythonBuiltinClassType.Blake2bType) {
                javaName = "BLAKE2B-%d";
                pythonName = "blake2b";
                javaDigestSize = digestSize == 0 ? 512 : digestSize;
            } else if (resultType == PythonBuiltinClassType.Blake2sType) {
                javaName = "BLAKE2S-%d";
                pythonName = "blake2s";
                javaDigestSize = digestSize == 0 ? 256 : digestSize;
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
            javaName = PythonUtils.formatJString(javaName, javaDigestSize);
            return createNode.execute(frame, inliningTarget, resultType, pythonName, javaName, data, this);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PException fail(VirtualFrame frame, Object type, Object data, Object digestSize,
                        Object key, Object salt, Object person, Object fanout, Object depth, Object leafSize, Object nodeOffset, Object nodeDepth, Object innerSize, Object lastNode,
                        Object usedforsecurity,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.ValueError, ErrorMessages.ONLY_DIGEST_SIZE_BLAKE_ARGUMENT);
        }
    }
}
