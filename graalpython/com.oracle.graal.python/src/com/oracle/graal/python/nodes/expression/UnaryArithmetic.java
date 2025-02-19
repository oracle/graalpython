/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberPositiveNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

public enum UnaryArithmetic {
    Pos(PyNumberPositiveNode::create),
    Neg(PyNumberNegativeNode::create),
    Invert(PyNumberInvertNode::create);

    interface CreateUnaryOp {
        UnaryOpNode create();
    }

    private final CreateUnaryOp create;

    UnaryArithmetic(CreateUnaryOp create) {
        this.create = create;
    }

    public UnaryOpNode create() {
        return create.create();
    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class UnaryArithmeticNode extends UnaryOpNode {

        static Supplier<NoAttributeHandler> createHandler(String operator) {

            return () -> new NoAttributeHandler() {
                private final BranchProfile errorProfile = BranchProfile.create();

                @Override
                public Object execute(Object receiver) {
                    errorProfile.enter();
                    throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.BAD_OPERAND_FOR, "unary ", operator, receiver);
                }
            };
        }

        @NeverDefault
        public static LookupAndCallUnaryNode createCallNode(TruffleString name, Supplier<NoAttributeHandler> handler) {
            return LookupAndCallUnaryNode.create(name, handler);
        }
    }

    @GenerateCached
    public abstract static class GenericUnaryArithmeticNode extends UnaryArithmeticNode {

        private final TruffleString specialMethodName;

        protected GenericUnaryArithmeticNode(TruffleString specialMethodName) {
            this.specialMethodName = specialMethodName;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object arg,
                        @Cached("createCallNode()") LookupAndCallUnaryNode callNode) {
            return callNode.executeObject(frame, arg);
        }

        @NeverDefault
        protected LookupAndCallUnaryNode createCallNode() {
            return createCallNode(specialMethodName, createHandler(specialMethodName.toString()));
        }

        @NeverDefault
        public static GenericUnaryArithmeticNode create(TruffleString specialMethodName) {
            return UnaryArithmeticFactory.GenericUnaryArithmeticNodeGen.create(specialMethodName);
        }
    }
}
