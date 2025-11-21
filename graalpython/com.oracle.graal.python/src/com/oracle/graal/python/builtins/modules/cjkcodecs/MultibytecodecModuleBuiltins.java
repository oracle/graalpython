/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.DBCSMap.MappingType;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodec.CodecType;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_multibytecodec")
public final class MultibytecodecModuleBuiltins extends PythonBuiltins {

    /** insufficient output buffer space */
    static final int MBERR_TOOSMALL = -1;
    /** incomplete input buffer */
    static final int MBERR_TOOFEW = -2;
    /** internal runtime error */
    static final int MBERR_INTERNAL = -3;

    static final TruffleString ERROR_STRICT = T_STRICT;
    static final TruffleString ERROR_IGNORE = T_IGNORE;
    static final TruffleString ERROR_REPLACE = T_REPLACE;

    static final int MBENC_FLUSH = 0x0001; /* encode all characters encodable */
    public static final int MBENC_MAX = MBENC_FLUSH;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    static void registerCodec(String name, int cidx, CodecType ct, int midx, MappingType mt,
                    DBCSMap[] maps, MultibyteCodec[] codecs) {
        TruffleString tsName = toTruffleStringUncached(name);
        TruffleString normalizedEncoding = CharsetMapping.normalizeUncached(tsName);
        CharsetMapping.CharsetWrapper charset = CharsetMapping.getCharsetNormalized(normalizedEncoding);
        if (charset != null) {
            if (cidx != -1) {
                codecs[cidx] = new MultibyteCodec(tsName, charset.charset(), ct);
            }
            if (midx != -1) {
                maps[midx] = new DBCSMap(name, tsName, charset.charset(), mt);
            }
        }
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    static Object createCodec(Node inliningTarget, MultibyteCodec codec) {
        codec.codecinit();
        return PFactory.createMultibyteCodecObject(PythonLanguage.get(inliningTarget), codec);
    }
}
