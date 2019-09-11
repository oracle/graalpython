/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.control;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.truffle.api.nodes.NodeCost.NONE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.NoAttributeHandler;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(cost = NONE)
public final class GetNextNode extends PNodeWithContext {

    public static GetNextNode create() {
        return new GetNextNode();
    }

    @Child private LookupAndCallUnaryNode nextCall = LookupAndCallUnaryNode.create(__NEXT__, () -> new NoAttributeHandler() {
        @Child private PRaiseNode raiseNode = PRaiseNode.create();

        @Override
        public Object execute(Object receiver) {
            throw raiseNode.raise(PythonErrorType.AttributeError, "'%s' object has no attribute '__next__'", receiver);
        }
    });

    public Object execute(VirtualFrame frame, Object iterator) {
        return nextCall.executeObject(frame, iterator);
    }

    public boolean executeBoolean(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return nextCall.executeBoolean(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public int executeInt(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return nextCall.executeInt(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public long executeLong(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return nextCall.executeLong(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    public double executeDouble(VirtualFrame frame, Object iterator) throws UnexpectedResultException {
        try {
            return nextCall.executeDouble(frame, iterator);
        } catch (UnexpectedResultException e) {
            throw new UnexpectedResultException(e.getResult());
        }
    }

    @GenerateUncached
    public abstract static class GetNextWithoutFrameNode extends PNodeWithContext {

        public abstract Object executeWithGlobalState(Object iterator);

        private static Object checkResult(PRaiseNode raiseNode, ConditionProfile notAnIterator, Object result, Object iterator) {
            if (notAnIterator.profile(result == PNone.NO_VALUE)) {
                throw raiseNode.raise(PythonErrorType.AttributeError, "'%s' object has no attribute '__next__'", iterator);
            }
            return result;
        }

        @Specialization
        Object doObject(Object iterator,
                        @Cached LookupAndCallUnaryDynamicNode nextCall,
                        @Cached PRaiseNode raiseNode,
                        @Cached("createBinaryProfile()") ConditionProfile notAnIterator) {
            return checkResult(raiseNode, notAnIterator, nextCall.executeObject(iterator, __NEXT__), iterator);
        }
    }
}
