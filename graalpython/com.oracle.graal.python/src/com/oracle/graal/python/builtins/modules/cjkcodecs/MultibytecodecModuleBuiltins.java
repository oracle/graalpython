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

import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENT_TYPE_INVALID;
import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.Charset;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltins.PyCapsule_IsValid;
import com.oracle.graal.python.builtins.modules.cjkcodecs.DBCSMap.MappingType;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodec.CodecType;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsuleNameMatchesNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_multibytecodec")
public final class MultibytecodecModuleBuiltins extends PythonBuiltins {

    static final TruffleString PyMultibyteCodec_CAPSULE_NAME = tsLiteral("multibytecodec.__map_*");
    /** insufficient output buffer space */
    protected static final int MBERR_TOOSMALL = -1;
    /** incomplete input buffer */
    protected static final int MBERR_TOOFEW = -2;
    /** internal runtime error */
    protected static final int MBERR_INTERNAL = -3;

    protected static final TruffleString ERROR_STRICT = T_STRICT;
    protected static final TruffleString ERROR_IGNORE = T_IGNORE;
    protected static final TruffleString ERROR_REPLACE = T_REPLACE;

    static final int MBENC_FLUSH = 0x0001; /* encode all characters encodable */
    public static final int MBENC_MAX = MBENC_FLUSH;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibytecodecModuleBuiltinsFactory.getFactories();
    }

    protected static void registerCodec(String name, int cidx, CodecType ct, int midx, MappingType mt,
                    DBCSMap[] maps, MultibyteCodec[] codecs,
                    PythonModule codec, PythonObjectFactory factory) {
        TruffleString tsName = toTruffleStringUncached(name);
        TruffleString normalizedEncoding = CharsetMapping.normalizeUncached(tsName);
        Charset charset = CharsetMapping.getCharsetNormalized(normalizedEncoding);
        if (charset != null) {
            if (cidx != -1) {
                codecs[cidx] = new MultibyteCodec(tsName, charset, ct);
            }
            if (midx != -1) {
                DBCSMap h = maps[midx] = new DBCSMap(name, tsName, charset, mt);
                codec.setAttribute(toTruffleStringUncached(h.charsetMapName),
                                factory.createCapsule(h, PyMultibyteCodec_CAPSULE_NAME, null));
            }
        }
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "__create_codec", minNumOfPositionalArgs = 1, doc = "__create_codec($module, arg, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class CreateCodecNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object createCodec(PyCapsule arg,
                        @Cached PyCapsuleNameMatchesNode nameMatchesNode) {
            return createCodec(this, arg, nameMatchesNode, factory(), getRaiseNode());
        }

        static Object createCodec(Node inliningTarget, PyCapsule arg,
                        PyCapsuleNameMatchesNode nameMatchesNode,
                        PythonObjectFactory factory,
                        PRaiseNode raiseNode) {
            if (PyCapsule_IsValid.doCapsule(arg, PyMultibyteCodec_CAPSULE_NAME, inliningTarget, nameMatchesNode) == 0) {
                throw raiseNode.raise(ValueError, ARGUMENT_TYPE_INVALID);
            }
            MultibyteCodec codec;
            codec = (MultibyteCodec) arg.getPointer();
            codec.codecinit();
            return factory.createMultibyteCodecObject(PythonBuiltinClassType.MultibyteCodec, codec);
        }

        @Fallback
        Object createCodec(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, ARGUMENT_TYPE_INVALID);
        }

    }

}
