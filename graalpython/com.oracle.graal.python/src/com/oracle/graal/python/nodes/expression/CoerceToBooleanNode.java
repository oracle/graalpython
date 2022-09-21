/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNodeFactory.NotNodeGen;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNodeFactory.YesNodeGen;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateWrapper
public abstract class CoerceToBooleanNode extends UnaryOpNode {

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new CoerceToBooleanNodeWrapper(this, probe);
    }

    public static CoerceToBooleanNode createIfTrueNode() {
        return YesNodeGen.create(null);
    }

    public static CoerceToBooleanNode createIfTrueNode(ExpressionNode operand) {
        return YesNodeGen.create(operand);
    }

    public static CoerceToBooleanNode createIfFalseNode() {
        return NotNodeGen.create(null);
    }

    public static CoerceToBooleanNode createIfFalseNode(ExpressionNode operand) {
        return NotNodeGen.create(operand);
    }

    public abstract boolean executeBoolean(VirtualFrame frame, Object value);

    @Override
    public abstract boolean executeBoolean(VirtualFrame frame);

    public abstract static class YesNode extends CoerceToBooleanNode {
        @Specialization
        static boolean doBoolean(boolean operand) {
            return operand;
        }

        @Specialization
        static boolean doInteger(int operand) {
            return operand != 0;
        }

        @Specialization
        static boolean doLong(long operand) {
            return operand != 0L;
        }

        @Specialization
        static boolean doDouble(double operand) {
            return operand != 0;
        }

        @Specialization
        static boolean doString(TruffleString operand) {
            return !operand.isEmpty();
        }

        @Megamorphic
        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Cached PyObjectIsTrueNode isTrue) {
            return isTrue.execute(frame, object);
        }
    }

    public abstract static class NotNode extends CoerceToBooleanNode {
        @Specialization
        static boolean doBool(boolean operand) {
            return !operand;
        }

        @Specialization
        static boolean doInteger(int operand) {
            return operand == 0;
        }

        @Specialization
        static boolean doLong(long operand) {
            return operand == 0L;
        }

        @Specialization
        static boolean doDouble(double operand) {
            return operand == 0;
        }

        @Specialization
        static boolean doString(TruffleString operand) {
            return operand.isEmpty();
        }

        @Megamorphic
        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Cached PyObjectIsTrueNode isTrue) {
            return !isTrue.execute(frame, object);
        }
    }
}
