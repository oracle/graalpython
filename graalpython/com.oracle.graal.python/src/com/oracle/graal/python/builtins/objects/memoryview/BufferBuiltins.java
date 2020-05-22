/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBuffer)
public class BufferBuiltins extends PythonBuiltins {

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BufferBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object repr(VirtualFrame frame, PBuffer self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode repr) {
            return createReprString(repr.executeObject(frame, self.getDelegate()));
        }

        @TruffleBoundary
        private static String createReprString(Object reprObj) {
            return "buffer(" + reprObj + ")";
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object iter(VirtualFrame frame, PBuffer self, boolean key,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode) {
            return callGetItemNode.executeObject(frame, self.getDelegate(), key);
        }

        @Specialization
        public Object iter(VirtualFrame frame, PBuffer self, int key,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode) {
            return callGetItemNode.executeObject(frame, self.getDelegate(), key);
        }

        @Specialization
        public Object iter(VirtualFrame frame, PBuffer self, long key,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode) {
            return callGetItemNode.executeObject(frame, self.getDelegate(), key);
        }

        @Specialization
        public Object iter(VirtualFrame frame, PBuffer self, PInt key,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItemNode) {
            return callGetItemNode.executeObject(frame, self.getDelegate(), key);
        }

        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            if (!PGuards.isInteger(idx)) {
                throw raise(TypeError, ErrorMessages.BUFFER_INDICES_MUST_BE_INTS, idx);
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__getitem__", "buffer", self);
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object len(VirtualFrame frame, PBuffer self,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLenNode) {
            return callLenNode.executeObject(frame, self.getDelegate());
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonBuiltinNode {

        @Specialization
        public Object iter(VirtualFrame frame, PBuffer self,
                        @Cached("create(__ITER__)") LookupAndCallUnaryNode callIterNode) {
            return callIterNode.executeObject(frame, self.getDelegate());
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }
}
