/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNodeFactory.NotNodeGen;
import com.oracle.graal.python.nodes.expression.CastToBooleanNodeFactory.YesNodeGen;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.TruffleObject;

@GenerateWrapper
public abstract class CastToBooleanNode extends UnaryOpNode {
    protected final IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new CastToBooleanNodeWrapper(this, probe);
    }

    public static CastToBooleanNode createIfTrueNode() {
        return YesNodeGen.create(null);
    }

    public static CastToBooleanNode createIfTrueNode(ExpressionNode operand) {
        return YesNodeGen.create(operand);
    }

    public static CastToBooleanNode createIfFalseNode() {
        return NotNodeGen.create(null);
    }

    public static CastToBooleanNode createIfFalseNode(ExpressionNode operand) {
        return NotNodeGen.create(operand);
    }

    public abstract boolean executeBoolean(VirtualFrame frame, Object value);

    @Override
    public abstract boolean executeBoolean(VirtualFrame frame);

    public abstract static class YesNode extends CastToBooleanNode {
        @Child private HashingStorageNodes.LenNode lenNode;

        @Specialization
        boolean doBoolean(boolean operand) {
            return operand;
        }

        @Specialization
        boolean doInteger(int operand) {
            return operand != 0;
        }

        @Specialization
        boolean doLong(long operand) {
            return operand != 0L;
        }

        @Specialization
        boolean doPInt(PInt operand) {
            return !operand.isZero();
        }

        @Specialization
        boolean doDouble(double operand) {
            return operand != 0;
        }

        @Specialization
        boolean doString(String operand) {
            return operand.length() != 0;
        }

        @Specialization(guards = "isNone(operand)")
        boolean doNone(@SuppressWarnings("unused") Object operand) {
            return false;
        }

        @Specialization(guards = "isEmpty(set)")
        public boolean doPBaseSetEmpty(@SuppressWarnings("unused") PBaseSet set) {
            return false;
        }

        @Specialization
        boolean doObject(VirtualFrame frame, Object object,
                        @Cached PRaiseNode raise,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callBoolNode) {
            Object value = callBoolNode.executeObject(frame, object);
            if (value instanceof Boolean) {
                return (boolean) value;
            } else if (value instanceof PInt && isBuiltinClassProfile.profileObject((PInt) value, PythonBuiltinClassType.Boolean)) {
                return ((PInt) value).isOne();
            } else {
                throw raise.raise(TypeError, "__bool__ should return bool, returned %p", value);
            }
        }

        protected boolean isEmpty(PBaseSet s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(HashingStorageNodes.LenNode.create());
            }
            return lenNode.execute(s.getDictStorage()) == 0;
        }
    }

    public abstract static class NotNode extends CastToBooleanNode {

        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean doBool(boolean operand) {
            return !operand;
        }

        @Specialization
        boolean doInteger(int operand) {
            return operand == 0;
        }

        @Specialization
        boolean doLong(long operand) {
            return operand == 0L;
        }

        @Specialization
        boolean doPInt(PInt operand) {
            return operand.isZero();
        }

        @Specialization
        boolean doDouble(double operand) {
            return operand == 0;
        }

        @Specialization
        boolean doString(String operand) {
            return operand.length() == 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNone(operand)")
        boolean doNone(Object operand) {
            return true;
        }

        @Specialization
        boolean doBytes(PBytes operand) {
            return getLength(operand.getSequenceStorage()) == 0;
        }

        @Specialization
        boolean doByteArray(PByteArray operand) {
            return getLength(operand.getSequenceStorage()) == 0;
        }

        @Specialization(guards = "isForeignObject(operand)")
        boolean doForeignObject(VirtualFrame frame, TruffleObject operand,
                        @Cached("createIfTrueNode()") CastToBooleanNode yesNode) {
            return !yesNode.executeBoolean(frame, operand);
        }

        @Specialization(guards = "!isForeignObject(object)")
        boolean doObject(VirtualFrame frame, Object object,
                        @Cached PRaiseNode raise,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callBoolNode) {
            Object value = callBoolNode.executeObject(frame, object);
            if (value instanceof Boolean) {
                return !((boolean) value);
            } else if (value instanceof PInt && isBuiltinClassProfile.profileObject((PInt) value, PythonBuiltinClassType.Boolean)) {
                return ((PInt) value).isZero();
            } else {
                throw raise.raise(TypeError, "__bool__ should return bool, returned %p", value);
            }
        }

        protected int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }
}
