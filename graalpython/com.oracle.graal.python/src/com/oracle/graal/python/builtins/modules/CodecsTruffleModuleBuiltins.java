/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = "_codecs_truffle")
public class CodecsTruffleModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    public abstract static class LookupTextEncoding extends PNodeWithRaise {
        public abstract Object execute(VirtualFrame frame, String encoding, String alternateCommand);

        @Specialization
        Object lookup(VirtualFrame frame, String encoding, String alternateCommand,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            PythonModule codecs = getContext().getCore().lookupBuiltinModule("_codecs_truffle");
            return callMethod.execute(frame, codecs, "_lookup_text_encoding", encoding, alternateCommand);
        }
    }

    public abstract static class GetPreferredEncoding extends PNodeWithRaise {
        public abstract String execute(VirtualFrame frame);

        @Specialization
        String getpreferredencoding(VirtualFrame frame,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectStrAsJavaStringNode strNode) {
            PythonModule codecs = PythonContext.get(this).getCore().lookupBuiltinModule("_codecs_truffle");
            Object e = callMethod.execute(frame, codecs, "_getpreferredencoding");
            return strNode.execute(frame, e);
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class MakeIncrementalcodecNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, Object errors, String attrName);

        @Specialization
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, @SuppressWarnings("unused") PNone errors, String attrName,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, codecInfo, attrName);
        }

        @Specialization(guards = "!isPNone(errors)")
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, Object errors, String attrName,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, codecInfo, attrName, errors);
        }
    }

    public abstract static class GetIncrementalEncoderNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, String errors);

        @Specialization
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, String errors,
                        @Cached MakeIncrementalcodecNode makeIncrementalcodecNode) {
            return makeIncrementalcodecNode.execute(frame, codecInfo, errors, "incrementalencoder");
        }
    }

    public abstract static class GetIncrementalDecoderNode extends PNodeWithRaise {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, String errors);

        @Specialization
        Object getIncEncoder(VirtualFrame frame, Object codecInfo, String errors,
                        @Cached MakeIncrementalcodecNode makeIncrementalcodecNode) {
            return makeIncrementalcodecNode.execute(frame, codecInfo, errors, "incrementaldecoder");
        }
    }
}
