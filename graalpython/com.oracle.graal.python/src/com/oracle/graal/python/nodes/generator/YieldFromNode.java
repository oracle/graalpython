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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.GetTracebackNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public class YieldFromNode extends AbstractYieldNode implements GeneratorControlNode {
    @Child private GetIteratorNode iter = GetIteratorNode.create();
    @Child private GetNextNode next = GetNextNode.create();
    @Child private GeneratorAccessNode access = GeneratorAccessNode.create();

    @Child private GetAttributeNode getValue;

    @Child private LookupInheritedAttributeNode getCloseNode;
    @Child private CallNode callCloseNode;

    @Child private LookupInheritedAttributeNode getThrowNode;
    @Child private CallNode callThrowNode;
    @Child private GetClassNode getExcClassNode;

    @Child private LookupAndCallBinaryNode getSendNode;
    @Child private CallNode callSendNode;

    @Child private GetTracebackNode getTracebackNode;

    private final IsBuiltinClassProfile stopIterProfile1 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile stopIterProfile2 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile stopIterProfile3 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile genExitProfile = IsBuiltinClassProfile.create();

    @CompilationFinal private int iteratorSlot;

    private final BranchProfile noThrow = BranchProfile.create();

    @Child private ExpressionNode right;

    @Child private PythonObjectFactory ofactory;

    public YieldFromNode(ExpressionNode right) {
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object _i = access.getIterator(frame, iteratorSlot);
        Object _y = null;
        if (_i == null) {
            // _i = iter(EXPR)
            // ....try:
            // ........_y = next(_i)
            // ....except StopIteration as _e:
            // ........_r = _e.value
            _i = iter.executeWith(frame, right.execute(frame));
            try {
                _y = next.execute(frame, _i);
            } catch (PException e) {
                e.expectStopIteration(stopIterProfile1);
                return getGetValue().executeObject(frame, e.getExceptionObject());
            }
            access.setIterator(frame, iteratorSlot, _i);
        }
        // else:
        // ....while 1:
        // ........try:
        // ............_s = yield _y
        while (true) {
            if (!access.isActive(frame, flagSlot)) {
                access.setActive(frame, flagSlot, true);
                access.setLastYieldIndex(frame, yieldIndex);
                throw new YieldException(_y);
            } else {
                access.setActive(frame, flagSlot, false);
                _y = null;
                // resuming from yield, write _s
                Object _s = PArguments.getSpecialArgument(frame);
                if (_s instanceof PException) {
                    gotException.enter();
                    PException _e = (PException) _s;
                    // except GeneratorExit as _e:
                    // ....try:
                    // ........_m = _i.close
                    // ....except AttributeError:
                    // ........pass
                    // ....else:
                    // ........_m()
                    // ....raise _e
                    if (genExitProfile.profileException(_e, PythonBuiltinClassType.GeneratorExit)) {
                        access.setIterator(frame, iteratorSlot, null);
                        Object close = getGetCloseNode().execute(_i);
                        if (close != PNone.NO_VALUE) {
                            getCallCloseNode().execute(frame, close, new Object[]{_i}, PKeyword.EMPTY_KEYWORDS);
                        }
                        throw _e;
                    }
                    // except BaseException as _e:
                    // ...._x = sys.exc_info()
                    // ....try:
                    // ........_m = _i.throw
                    // ....except AttributeError:
                    // ........raise _e
                    // ....else:
                    // ........try:
                    // ............_y = _m(*_x)
                    // ........except StopIteration as _e:
                    // ............_r = _e.value
                    // ............break
                    Object _m = getGetThrowNode().execute(_i);
                    if (_m == PNone.NO_VALUE) {
                        noThrow.enter();
                        access.setIterator(frame, iteratorSlot, null);
                        throw _e;
                    } else {
                        try {
                            _y = getCallThrowNode().execute(frame, _m,
                                            new Object[]{_i, getExceptionClassNode().execute(((PException) _s).getExceptionObject()),
                                                            ((PException) _s).getExceptionObject(),
                                                            ensureGetTracebackNode().execute(frame, ((PException) _s).getExceptionObject())},
                                            PKeyword.EMPTY_KEYWORDS);
                        } catch (PException _e2) {
                            access.setIterator(frame, iteratorSlot, null);
                            _e2.expectStopIteration(stopIterProfile2);
                            return getGetValue().executeObject(frame, _e2.getExceptionObject());
                        }
                    }
                } else {
                    // else:
                    // ....try:
                    // ........if _s is None:
                    // ............_y = next(_i)
                    // ........else:
                    // ............_y = _i.send(_s)
                    // ........except StopIteration as _e:
                    // ............_r = _e.value
                    // ............break
                    try {
                        if (_s == null || _s == PNone.NONE) {
                            gotNothing.enter();
                            _y = next.execute(frame, _i);
                        } else {
                            Object send = getGetSendNode().executeObject(frame, _i, "send");
                            // send will be bound at this point
                            _y = getCallSendNode().execute(frame, send, new Object[]{_s}, PKeyword.EMPTY_KEYWORDS);
                        }
                    } catch (PException _e) {
                        access.setIterator(frame, iteratorSlot, null);
                        _e.expectStopIteration(stopIterProfile3);
                        return getGetValue().executeObject(frame, _e.getExceptionObject());
                    }
                }
            }
        }
    }

    private GetAttributeNode getGetValue() {
        if (getValue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getValue = insert(GetAttributeNode.create("value", null));
        }
        return getValue;
    }

    private GetClassNode getExceptionClassNode() {
        if (getExcClassNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getExcClassNode = insert(GetClassNode.create());
        }
        return getExcClassNode;
    }

    private LookupInheritedAttributeNode getGetCloseNode() {
        if (getCloseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCloseNode = insert(LookupInheritedAttributeNode.create("close"));
        }
        return getCloseNode;
    }

    private CallNode getCallCloseNode() {
        if (callCloseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callCloseNode = insert(CallNode.create());
        }
        return callCloseNode;
    }

    private LookupInheritedAttributeNode getGetThrowNode() {
        if (getThrowNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getThrowNode = insert(LookupInheritedAttributeNode.create("throw"));
        }
        return getThrowNode;
    }

    private CallNode getCallThrowNode() {
        if (callThrowNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callThrowNode = insert(CallNode.create());
        }
        return callThrowNode;
    }

    private LookupAndCallBinaryNode getGetSendNode() {
        if (getSendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSendNode = insert(LookupAndCallBinaryNode.create(SpecialMethodNames.__GETATTRIBUTE__));
        }
        return getSendNode;
    }

    private CallNode getCallSendNode() {
        if (callSendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callSendNode = insert(CallNode.create());
        }
        return callSendNode;
    }

    private GetTracebackNode ensureGetTracebackNode() {
        if (getTracebackNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getTracebackNode = insert(GetTracebackNodeGen.create());
        }
        return getTracebackNode;
    }

    public void setIteratorSlot(int slot) {
        this.iteratorSlot = slot;
    }
}
