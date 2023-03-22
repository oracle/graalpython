/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.truffle.api.nodes.NodeCost.NONE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class GetNextNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object iterator);

    public Object execute(Object iterator) {
        return execute(null, iterator);
    }

    public abstract boolean executeBoolean(VirtualFrame frame, Object iterator) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, Object iterator) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object iterator) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Object iterator) throws UnexpectedResultException;

    @NodeInfo(cost = NONE)
    private static final class GetNextCached extends GetNextNode {

        @Child private LookupAndCallUnaryNode nextCall = LookupAndCallUnaryNode.create(SpecialMethodSlot.Next, () -> new NoAttributeHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object receiver) {
                throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, receiver);
            }
        });

        @Override
        public Object execute(Frame frame, Object iterator) {
            return nextCall.executeObject((VirtualFrame) frame, iterator);
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            return PGuards.expectBoolean(nextCall.executeObject(frame, iterator));
        }

        @Override
        public int executeInt(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            return PGuards.expectInteger(nextCall.executeObject(frame, iterator));
        }

        @Override
        public long executeLong(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            return PGuards.expectLong(nextCall.executeObject(frame, iterator));
        }

        @Override
        public double executeDouble(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            return PGuards.expectDouble(nextCall.executeObject(frame, iterator));
        }
    }

    private static final class GetNextUncached extends GetNextNode {
        static final GetNextUncached INSTANCE = new GetNextUncached();

        @Override
        public Object execute(Frame frame, Object iterator) {
            return executeImpl(iterator);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private Object executeImpl(Object iterator) {
            Object nextMethod = LookupSpecialMethodSlotNode.getUncached(SpecialMethodSlot.Next).execute(null, GetClassNode.executeUncached(iterator), iterator);
            if (nextMethod == PNone.NO_VALUE) {
                throw PRaiseNode.getUncached().raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, iterator, T___NEXT__);
            }
            return CallUnaryMethodNode.getUncached().executeObject(nextMethod, iterator);
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            Object value = execute(frame, iterator);
            if (value instanceof Boolean) {
                return (boolean) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        public int executeInt(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            Object value = execute(frame, iterator);
            if (value instanceof Integer) {
                return (int) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        public long executeLong(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            Object value = execute(frame, iterator);
            if (value instanceof Long) {
                return (long) value;
            }
            throw new UnexpectedResultException(value);
        }

        @Override
        public double executeDouble(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
            Object value = execute(frame, iterator);
            if (value instanceof Double) {
                return (double) value;
            }
            throw new UnexpectedResultException(value);
        }
    }

    @NeverDefault
    public static GetNextNode create() {
        return new GetNextCached();
    }

    public static GetNextNode getUncached() {
        return GetNextUncached.INSTANCE;
    }
}
