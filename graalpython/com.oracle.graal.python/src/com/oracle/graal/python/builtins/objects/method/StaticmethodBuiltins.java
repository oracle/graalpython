/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PStaticmethod})
public final class StaticmethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StaticmethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class GetNode extends PythonTernaryBuiltinNode {
        /**
         * @see ClassmethodBuiltins.GetNode#getCached
         */
        @Specialization(guards = {"isSingleContext()", "cachedSelf == self"}, limit = "3")
        static Object getCached(@SuppressWarnings("unused") PDecoratedMethod self, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") @Cached(value = "self", weak = true) PDecoratedMethod cachedSelf,
                        @SuppressWarnings("unused") @Cached(value = "self.getCallable()", weak = true) Object cachedCallable) {
            return cachedCallable;
        }

        @Specialization(replaces = "getCached")
        static Object get(PDecoratedMethod self, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object callable = self.getCallable();
            if (callable == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.UNINITIALIZED_S_OBJECT);
            }
            return callable;
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class CallMethodNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object call(VirtualFrame frame, PDecoratedMethod self, Object[] args, PKeyword[] kwargs,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, self.getCallable(), args, kwargs);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString PREFIX = tsLiteral("<staticmethod(");
        private static final int PREFIX_LEN = PREFIX.byteLength(TS_ENCODING);
        private static final TruffleString SUFFIX = tsLiteral(")>");
        private static final int SUFFIX_LEN = SUFFIX.byteLength(TS_ENCODING);

        @Specialization
        Object repr(VirtualFrame frame, PDecoratedMethod self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached TruffleStringBuilder.AppendStringNode append,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            TruffleString callableRepr = repr.execute(frame, inliningTarget, self.getCallable());
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, PREFIX_LEN + callableRepr.byteLength(TS_ENCODING) + SUFFIX_LEN);
            append.execute(sb, PREFIX);
            append.execute(sb, callableRepr);
            append.execute(sb, SUFFIX);
            return toString.execute(sb);
        }
    }
}
