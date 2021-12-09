/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.EqNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.GeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.GtNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.LeNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.LtNodeGen;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory.NeNodeGen;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class BinaryComparisonNode extends BinaryOpNode {

    private abstract static class ErrorNode extends BinaryComparisonNode {
        @Child private PRaiseNode raiseNode;

        protected final RuntimeException noSupported(Object left, Object right) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, operator(), left, right);
        }

        protected abstract String operator();
    }

    private abstract static class FallbackNode extends BinaryComparisonNode {
        @Child private IsNode isNode;

        protected final IsNode ensureIsNode() {
            if (isNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNode = insert(IsNode.create());
            }
            return isNode;
        }
    }

    public abstract boolean cmp(int l, int r);

    public abstract boolean cmp(long l, long r);

    public abstract boolean cmp(char l, char r);

    public abstract boolean cmp(byte l, byte r);

    public abstract boolean cmp(double l, double r);

    public abstract boolean cmp(String l, String r);

    public abstract boolean executeBool(VirtualFrame frame, Object left, Object right) throws UnexpectedResultException;

    public abstract static class LeNode extends ErrorNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l <= r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l <= r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l <= r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l <= r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l <= r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return StringUtils.compareToUnicodeAware(l, r) <= 0;
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l <= r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l <= r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw noSupported(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Le, SpecialMethodSlot.Ge, true, true);
        }

        @Override
        protected final String operator() {
            return "<=";
        }

        public static LeNode create() {
            return LeNodeGen.create(null, null);
        }
    }

    public abstract static class LtNode extends ErrorNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l < r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l < r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l < r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l < r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l < r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return StringUtils.compareToUnicodeAware(l, r) < 0;
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l < r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l < r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw noSupported(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Lt, SpecialMethodSlot.Gt, true, true);
        }

        @Override
        protected String operator() {
            return "<";
        }

        public static LtNode create() {
            return LtNodeGen.create(null, null);
        }
    }

    public abstract static class GeNode extends ErrorNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l >= r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l >= r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l >= r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l >= r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l >= r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return StringUtils.compareToUnicodeAware(l, r) >= 0;
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l >= r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l >= r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw noSupported(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ge, SpecialMethodSlot.Le, true, true);
        }

        @Override
        protected String operator() {
            return ">=";
        }

        public static GeNode create() {
            return GeNodeGen.create(null, null);
        }
    }

    public abstract static class GtNode extends ErrorNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l > r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l > r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l > r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l > r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l > r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return StringUtils.compareToUnicodeAware(l, r) > 0;
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l > r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l > r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                throw noSupported(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Gt, SpecialMethodSlot.Lt, true, true);
        }

        @Override
        protected String operator() {
            return ">";
        }

        public static GtNode create() {
            return GtNodeGen.create(null, null);
        }
    }

    public abstract static class EqNode extends FallbackNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l == r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l == r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l == r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l == r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l == r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return l.equals(r);
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l == r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l == r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                // just like python, if no implementation is available, do something sensible for
                // == and !=
                return ensureIsNode().execute(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Eq, SpecialMethodSlot.Eq, true, true);
        }

        public static EqNode create() {
            return EqNodeGen.create(null, null);
        }
    }

    public abstract static class NeNode extends FallbackNode {

        @Specialization
        @Override
        public final boolean cmp(int l, int r) {
            return l != r;
        }

        @Specialization
        @Override
        public final boolean cmp(long l, long r) {
            return l != r;
        }

        @Specialization
        @Override
        public final boolean cmp(char l, char r) {
            return l != r;
        }

        @Specialization
        @Override
        public final boolean cmp(byte l, byte r) {
            return l != r;
        }

        @Specialization
        @Override
        public final boolean cmp(double l, double r) {
            return l != r;
        }

        @Specialization
        @Override
        public final boolean cmp(String l, String r) {
            return !l.equals(r);
        }

        @Specialization
        static boolean cmp(int l, double r) {
            return l != r;
        }

        @Specialization
        static boolean cmp(double l, int r) {
            return l != r;
        }

        @Specialization
        protected final Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Cached("createCallNode()") LookupAndCallBinaryNode callNode) {
            Object result = callNode.executeObject(frame, left, right);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                // just like python, if no implementation is available, do something sensible for
                // == and !=
                return !ensureIsNode().execute(left, right);
            }
            return result;
        }

        protected static LookupAndCallBinaryNode createCallNode() {
            return LookupAndCallBinaryNode.create(SpecialMethodSlot.Ne, SpecialMethodSlot.Ne, true, true);
        }

        public static NeNode create() {
            return NeNodeGen.create(null, null);
        }
    }
}
