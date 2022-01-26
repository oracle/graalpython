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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.LEN_OF_UNSIZED_OBJECT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PRepeat})
public final class RepeatBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RepeatBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PRepeat self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() > 0")
        static Object nextPos(PRepeat self) {
            self.setCnt(self.getCnt() - 1);
            return self.getElement();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.getCnt() == 0")
        Object nextZero(PRepeat self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = "self.getCnt() < 0")
        static Object nextNeg(PRepeat self) {
            return self.getElement();
        }
    }

    @Builtin(name = __LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() >= 0")
        static Object hintPos(PRepeat self) {
            return self.getCnt();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.getCnt() < 0")
        Object hintNeg(PRepeat self) {
            throw raise(TypeError, LEN_OF_UNSIZED_OBJECT);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() >= 0")
        Object reducePos(PRepeat self,
                        @Cached GetClassNode getClass) {
            Object type = getClass.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{self.getElement(), self.getCnt()});
            return factory().createTuple(new Object[]{type, tuple});
        }

        @Specialization(guards = "self.getCnt() < 0")
        Object reduceNeg(PRepeat self,
                        @Cached GetClassNode getClass) {
            Object type = getClass.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{self.getElement()});
            return factory().createTuple(new Object[]{type, tuple});
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() >= 0")
        static Object reprPos(VirtualFrame frame, PRepeat self,
                        @Cached GetClassNode getClass,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @Cached CastToJavaStringNode castNode) {
            Object type = getClass.execute(self);
            StringBuilder sb = new StringBuilder();
            PythonUtils.append(sb, castNode.execute(getAttrNode.execute(frame, type, __NAME__)));
            PythonUtils.append(sb, "(");
            PythonUtils.append(sb, castNode.execute(reprNode.execute(frame, self.getElement())));
            PythonUtils.append(sb, ", ");
            PythonUtils.append(sb, PInt.toString(self.getCnt()));
            PythonUtils.append(sb, ")");
            return PythonUtils.sbToString(sb);
        }

        @Specialization(guards = "self.getCnt() < 0")
        static Object reprNeg(VirtualFrame frame, PRepeat self,
                        @Cached GetClassNode getClass,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @Cached CastToJavaStringNode castNode) {
            Object type = getClass.execute(self);
            StringBuilder sb = new StringBuilder();
            PythonUtils.append(sb, castNode.execute(getAttrNode.execute(frame, type, __NAME__)));
            PythonUtils.append(sb, "(");
            PythonUtils.append(sb, castNode.execute(reprNode.execute(frame, self.getElement())));
            PythonUtils.append(sb, ")");
            return PythonUtils.sbToString(sb);
        }
    }
}
