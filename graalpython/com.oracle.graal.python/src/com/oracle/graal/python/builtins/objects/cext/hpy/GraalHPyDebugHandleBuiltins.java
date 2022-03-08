/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.DebugHandle)
public final class GraalHPyDebugHandleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalHPyDebugHandleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, isGetter = true, //
                    doc = "The object which the handle points to")
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleObjNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PDebugHandle self) {
            return self.getObj();
        }
    }

    @Builtin(name = "id", minNumOfPositionalArgs = 1, isGetter = true, //
                    doc = "A numeric identifier representing the underlying universal handle")
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleIdNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long doGeneric(PDebugHandle self) {
            return self.getId();
        }
    }

    @Builtin(name = "is_closed", minNumOfPositionalArgs = 1, isGetter = true, //
                    doc = "Self-explanatory")
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleIsClosedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(PDebugHandle self) {
            return self.isClosed();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleEqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doDebugHandle(PDebugHandle self, PDebugHandle other) {
            return self.eq(other);
        }

        @Specialization(guards = "!isDebugHandle(other)")
        @SuppressWarnings("unused")
        static Object doOther(PDebugHandle self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        static boolean isDebugHandle(Object object) {
            return object instanceof PDebugHandle;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(VirtualFrame frame, PDebugHandle self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            PythonLanguage language = getLanguage();
            PythonContext context = getContext();
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return format(self, simpleTruffleStringFormatNode, castToTruffleStringNode);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        private static Object format(PDebugHandle self, SimpleTruffleStringFormatNode simpleTruffleStringFormatNode, CastToTruffleStringNode castToTruffleStringNode) {
            long id = self.getId();
            if (self.isClosed()) {
                return simpleTruffleStringFormatNode.format("<DebugHandle 0x%s CLOSED>", toHex(id));
            } else {
                Object objRepr = LookupAndCallUnaryDynamicNode.getUncached().executeObject(self.getObj(), SpecialMethodNames.T___REPR__);
                TruffleString reprStr = castToTruffleStringNode.execute(objRepr);
                return simpleTruffleStringFormatNode.format("<DebugHandle 0x%s for %s>", toHex(id), reprStr);
            }
        }

        @TruffleBoundary
        private static String toHex(long id) {
            return Long.toHexString(id);
        }
    }

    @Builtin(name = "_force_close", minNumOfPositionalArgs = 1, doc = "Close the underlying handle. FOR TESTS ONLY.")
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleForceCloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        PNone doGeneric(PDebugHandle self) {
            self.close(getContext().getHPyDebugContext());
            return PNone.NONE;
        }
    }
}
