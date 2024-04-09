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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.findCodec;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.PyMultibyteCodec_CAPSULE_NAME;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.registerCodec;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.CreateCodecNode.createCodec;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CODECS_KR;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CODECS_KR;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_NAME_MUST_BE_A_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.NO_SUCH_CODEC_IS_SUPPORTED;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.DBCSMap.MappingType;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodec.CodecType;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsuleNameMatchesNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__CODECS_KR)
public final class CodecsKRModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsKRModuleBuiltinsFactory.getFactories();
    }

    private static final DBCSMap[] MAPPING_LIST = new DBCSMap[3];
    // MAPPING_DECONLY(ksx1001)
    // MAPPING_ENCONLY(cp949)
    // MAPPING_DECONLY(cp949ext)

    private static final MultibyteCodec[] CODEC_LIST = new MultibyteCodec[3];
    // CODEC_STATELESS(euc_kr)
    // CODEC_STATELESS(cp949)
    // CODEC_STATELESS(johab)

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonObjectFactory factory = core.factory();
        PythonModule codec = core.lookupBuiltinModule(T__CODECS_KR);
        registerCodec("euc_kr", 0, CodecType.STATELESS, -1, null, null, CODEC_LIST, codec, factory);
        registerCodec("cp949", 1, CodecType.STATELESS, 1, MappingType.ENCONLY, MAPPING_LIST, CODEC_LIST, codec, factory);
        registerCodec("johab", 2, CodecType.STATELESS, -1, null, null, CODEC_LIST, codec, factory);

        registerCodec("ksx1001", -1, null, 0, MappingType.DECONLY, MAPPING_LIST, null, codec, factory);
        registerCodec("cp949ext", -1, null, 2, MappingType.DECONLY, MAPPING_LIST, null, codec, factory);
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "getcodec", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetCodecNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getcodec(Object encoding,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.EqualNode isEqual,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode asUTF8Node,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {

            if (!unicodeCheckNode.execute(inliningTarget, encoding)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ENCODING_NAME_MUST_BE_A_STRING);
            }

            MultibyteCodec codec = findCodec(CODEC_LIST, asUTF8Node.execute(inliningTarget, encoding), isEqual);
            if (codec == null) {
                throw raiseNode.get(inliningTarget).raise(LookupError, NO_SUCH_CODEC_IS_SUPPORTED);
            }

            PyCapsule codecobj = factory.createCapsule(codec, PyMultibyteCodec_CAPSULE_NAME);
            return createCodec(inliningTarget, codecobj, nameMatchesNode, factory, raiseNode);
        }

    }

}
