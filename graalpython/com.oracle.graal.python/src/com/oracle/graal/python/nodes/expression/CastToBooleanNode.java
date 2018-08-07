/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNodeFactory.NotNodeGen;
import com.oracle.graal.python.nodes.expression.CastToBooleanNodeFactory.YesNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;

public abstract class CastToBooleanNode extends UnaryOpNode {
    @Child private GetClassNode getClassNode;

    public static CastToBooleanNode createIfTrueNode() {
        return YesNodeGen.create(null);
    }

    public static CastToBooleanNode createIfTrueNode(PNode operand) {
        return YesNodeGen.create(operand);
    }

    public static CastToBooleanNode createIfFalseNode() {
        return NotNodeGen.create(null);
    }

    public static CastToBooleanNode createIfFalseNode(PNode operand) {
        return NotNodeGen.create(operand);
    }

    public abstract boolean executeBoolean(VirtualFrame frame, Object value);

    @Override
    public abstract boolean executeBoolean(VirtualFrame frame);

    public abstract boolean executeWith(Object value);

    GetClassNode getGetClassNode() {
        if (getClassNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getClassNode = insert(GetClassNode.create());
        }
        return getClassNode;
    }

    @ImportStatic(Message.class)
    public abstract static class YesNode extends CastToBooleanNode {

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
        boolean doObject(Object object,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callBoolNode) {
            Object value = callBoolNode.executeObject(object);
            if (value instanceof Boolean) {
                return (boolean) value;
            } else if (value instanceof PInt && getGetClassNode().execute(value) == getCore().lookupType(PythonBuiltinClassType.Boolean)) {
                return ((PInt) value).isOne();
            } else {
                throw raise(TypeError, "__bool__ should return bool, returned %p", value);
            }
        }
    }

    public abstract static class NotNode extends CastToBooleanNode {

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
            return operand.len() == 0;
        }

        @Specialization
        boolean doByteArray(PByteArray operand) {
            return operand.len() == 0;
        }

        @Specialization(guards = "isForeignObject(operand)")
        boolean doForeignObject(TruffleObject operand,
                        @Cached("createIfTrueNode()") CastToBooleanNode yesNode) {
            return !yesNode.executeWith(operand);
        }

        @Specialization(guards = "!isForeignObject(object)")
        boolean doObject(Object object,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callBoolNode) {
            Object value = callBoolNode.executeObject(object);
            if (value instanceof Boolean) {
                return !((boolean) value);
            } else if (value instanceof PInt && getGetClassNode().execute(value) == getCore().lookupType(PythonBuiltinClassType.Boolean)) {
                return ((PInt) value).isZero();
            } else {
                throw raise(TypeError, "__bool__ should return bool, returned %p", value);
            }
        }
    }
}
