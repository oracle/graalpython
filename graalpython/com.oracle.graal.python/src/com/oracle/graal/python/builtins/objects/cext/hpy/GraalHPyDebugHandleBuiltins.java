/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

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
            return self.getHandle().getDelegate();
        }
    }

    @Builtin(name = "id", minNumOfPositionalArgs = 1, isGetter = true, //
                    doc = "A numeric identifier representing the underlying universal handle")
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleIdNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PDebugHandle self,
                        @Cached ConditionProfile profile) {
            return self.getHandle().getId(PythonContext.get(null).getHPyDebugContext(), profile);
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleEqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doDebugHandle(PDebugHandle self, PDebugHandle other) {
            return self.getHandle() == other.getHandle();
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

    @Builtin(name = SpecialMethodNames.__REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HPyDebugHandleReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(VirtualFrame frame, PDebugHandle self) {
            PythonLanguage language = getLanguage();
            PythonContext context = getContext();
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return format(context.getHPyDebugContext(), self);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private static Object format(GraalHPyDebugContext hpyDebugContext, PDebugHandle self) {
            int id = self.getHandle().getId(hpyDebugContext, ConditionProfile.getUncached());
            Object objRepr = LookupAndCallUnaryDynamicNode.getUncached().executeObject(self.getHandle().getDelegate(), SpecialMethodNames.__REPR__);
            String reprStr = CastToJavaStringNode.getUncached().execute(objRepr);
            return String.format("<DebugHandle 0x%s for %s>", Integer.toHexString(id), reprStr);
        }
    }
}
