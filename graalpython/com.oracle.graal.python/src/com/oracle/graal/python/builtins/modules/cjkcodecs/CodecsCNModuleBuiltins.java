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
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.CreateCodecNode.createCodec;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_NAME_MUST_BE_A_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.NO_SUCH_CODEC_IS_SUPPORTED;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import java.nio.charset.Charset;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.DBCSMap.MappingType;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodec.CodecType;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_codecs_cn")
public class CodecsCNModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsCNModuleBuiltinsFactory.getFactories();
    }

    private static final DBCSMap[] MAPPING_LIST = new DBCSMap[4];
    // MAPPING_DECONLY(gb2312)
    // MAPPING_DECONLY(gbkext)
    // MAPPING_ENCONLY(gbcommon)
    // MAPPING_ENCDEC(gb18030ext)

    private static final MultibyteCodec[] CODEC_LIST = new MultibyteCodec[4];
    // CODEC_STATELESS(gb2312)
    // CODEC_STATELESS(gbk)
    // CODEC_STATELESS(gb18030)
    // CODEC_STATEFUL(hz)

    @TruffleBoundary
    protected static boolean setCodecs(String name, TruffleString tsName, Charset charset) {
        if (name.contentEquals("gb2312")) {
            CODEC_LIST[0] = new MultibyteCodec(tsName, charset, CodecType.STATELESS);
            MAPPING_LIST[0] = new DBCSMap(name, tsName, charset, MappingType.DECONLY);
            return true;
        }
        if (name.contentEquals("gbk")) {
            CODEC_LIST[1] = new MultibyteCodec(tsName, charset, CodecType.STATELESS);
            return true;
        }
        if (name.contentEquals("gb18030")) {
            CODEC_LIST[2] = new MultibyteCodec(tsName, charset, CodecType.STATELESS);
            return true;
        }
        if (name.contentEquals("hz")) {
            CODEC_LIST[3] = new MultibyteCodec(tsName, charset, CodecType.STATEFUL);
            return true;
        }
        if (name.contentEquals("gbkext")) {
            MAPPING_LIST[1] = new DBCSMap(name, tsName, charset, MappingType.DECONLY);
            return true;
        }
        if (name.contentEquals("gbcommon")) {
            MAPPING_LIST[2] = new DBCSMap(name, tsName, charset, MappingType.ENCONLY);
            return true;
        }
        if (name.contentEquals("gb18030ext")) {
            MAPPING_LIST[3] = new DBCSMap(name, tsName, charset, MappingType.ENCDEC);
            return true;
        }
        return false;
    }

    @Override
    public void initialize(Python3Core core) {
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        for (DBCSMap h : MAPPING_LIST) {
            if (h == null) {
                continue;
            }
            addBuiltinConstant(h.charsetMapName,
                            factory.createCapsule(h, PyMultibyteCodec_CAPSULE_NAME, null));
        }
        super.initialize(core);
    }

    @Builtin(name = "getcodec", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetCodecNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getcodec(Object encoding,
                        @Cached TruffleString.EqualNode isEqual,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode asUTF8Node,
                        @Cached PythonCextCapsuleBuiltins.NameMatchesNode nameMatchesNode) {

            if (!unicodeCheckNode.execute(encoding)) {
                throw raise(TypeError, ENCODING_NAME_MUST_BE_A_STRING);
            }

            MultibyteCodec codec = findCodec(CODEC_LIST, asUTF8Node.execute(encoding), isEqual);
            if (codec == null) {
                throw raise(LookupError, NO_SUCH_CODEC_IS_SUPPORTED);
            }

            PyCapsule codecobj = factory().createCapsule(codec, PyMultibyteCodec_CAPSULE_NAME, null);
            return createCodec(codecobj, nameMatchesNode, factory(), getRaiseNode());
        }
    }

}
