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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
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
    @Child private GetClassNode getExceptionClassNode;

    @Child private LookupAndCallBinaryNode callSendNode;

    private final IsBuiltinClassProfile stopIterProfile1 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile stopIterProfile2 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile stopIterProfile3 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile genExitProfile = IsBuiltinClassProfile.create();

    @CompilationFinal private int iteratorSlot;

    private final BranchProfile noThrow = BranchProfile.create();

    @Child private ExpressionNode right;
    @Child private WriteNode yieldWriteNode;

    public YieldFromNode(ExpressionNode right, WriteNode yieldSlot) {
        this.right = right;
        this.yieldWriteNode = yieldSlot;
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
            _i = iter.executeWith(right.execute(frame));
            try {
                _y = next.execute(_i);
            } catch (PException e) {
                e.expectStopIteration(stopIterProfile1);
                return getGetValue().executeObject(e.getExceptionObject());
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
                yieldWriteNode.doWrite(frame, _y);
                throw YieldException.INSTANCE;
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
                                            new Object[]{_i, getExceptionClassNode().execute(_s), ((PException) _s).getExceptionObject(),
                                                            ((PException) _s).getExceptionObject().getTraceback(factory())},
                                            PKeyword.EMPTY_KEYWORDS);
                        } catch (PException _e2) {
                            access.setIterator(frame, iteratorSlot, null);
                            _e2.expectStopIteration(stopIterProfile2);
                            return getGetValue().executeObject(_e2.getExceptionObject());
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
                        if (_s == null) {
                            gotNothing.enter();
                            _y = next.execute(_i);
                        } else {
                            _y = getCallSendNode().executeObject(_i, _s);
                        }
                    } catch (PException _e) {
                        access.setIterator(frame, iteratorSlot, null);
                        _e.expectStopIteration(stopIterProfile3);
                        return getGetValue().executeObject(_e.getExceptionObject());
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
        if (getExceptionClassNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getExceptionClassNode = insert(GetClassNode.create());
        }
        return getExceptionClassNode;
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

    private LookupAndCallBinaryNode getCallSendNode() {
        if (callSendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callSendNode = insert(LookupAndCallBinaryNode.create("send"));
        }
        return callSendNode;
    }

    public void setIteratorSlot(int slot) {
        this.iteratorSlot = slot;
    }
}
