/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PRepeat})
public final class RepeatBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RepeatBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PRepeat self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() > 0")
        static Object nextPos(PRepeat self) {
            self.setCnt(self.getCnt() - 1);
            return self.getElement();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.getCnt() == 0")
        static Object nextZero(PRepeat self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raiseStopIteration();
        }

        @Specialization(guards = "self.getCnt() < 0")
        static Object nextNeg(PRepeat self) {
            return self.getElement();
        }
    }

    @Builtin(name = J___LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() >= 0")
        static Object hintPos(PRepeat self) {
            return self.getCnt();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "self.getCnt() < 0")
        static Object hintNeg(PRepeat self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, LEN_OF_UNSIZED_OBJECT);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PRepeat self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile negativeCountProfile,
                        @Cached GetClassNode getClass,
                        @Cached PythonObjectFactory factory) {
            Object type = getClass.execute(inliningTarget, self);
            Object[] tupleElements;
            if (negativeCountProfile.profile(inliningTarget, self.getCnt() < 0)) {
                tupleElements = new Object[]{self.getElement()};
            } else {
                tupleElements = new Object[]{self.getElement(), self.getCnt()};
            }
            PTuple tuple = factory.createTuple(tupleElements);
            return factory.createTuple(new Object[]{type, tuple});
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getCnt() >= 0")
        static TruffleString reprPos(VirtualFrame frame, PRepeat self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClass") @Cached GetClassNode getClass,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttrNode,
                        @Shared("repr") @Cached PyObjectReprAsObjectNode reprNode,
                        @Shared("castToTruffleString") @Cached CastToTruffleStringNode castNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object type = getClass.execute(inliningTarget, self);
            return simpleTruffleStringFormatNode.format("%s(%s, %d)", castNode.execute(inliningTarget, getAttrNode.execute(frame, inliningTarget, type, T___NAME__)),
                            castNode.execute(inliningTarget, reprNode.execute(frame, inliningTarget, self.getElement())),
                            self.getCnt());
        }

        @Specialization(guards = "self.getCnt() < 0")
        static TruffleString reprNeg(VirtualFrame frame, PRepeat self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClass") @Cached GetClassNode getClass,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttrNode,
                        @Shared("repr") @Cached PyObjectReprAsObjectNode reprNode,
                        @Shared("castToTruffleString") @Cached CastToTruffleStringNode castNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object type = getClass.execute(inliningTarget, self);
            return simpleTruffleStringFormatNode.format("%s(%s)", castNode.execute(inliningTarget, getAttrNode.execute(frame, inliningTarget, type, T___NAME__)),
                            castNode.execute(inliningTarget, reprNode.execute(frame, inliningTarget, self.getElement())));
        }
    }
}
