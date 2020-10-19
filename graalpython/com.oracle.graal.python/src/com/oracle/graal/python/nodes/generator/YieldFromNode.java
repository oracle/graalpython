/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.generator.ThrowData;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class YieldFromNode extends AbstractYieldNode implements GeneratorControlNode {
    @Child private PythonObjectLibrary lib = PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth());
    @Child private GetNextNode next = GetNextNode.create();
    @Child private GeneratorAccessNode access = GeneratorAccessNode.create();

    @Child private GetAttributeNode getValue;

    @Child private GetAttributeNode getCloseNode;
    @Child private CallNode callCloseNode;

    @Child private GetAttributeNode getThrowNode;
    @Child private CallNode callThrowNode;

    @Child private GetAttributeNode getSendNode;
    @Child private CallNode callSendNode;

    @Child private GetTracebackNode getTracebackNode;
    @Child private WriteUnraisableNode writeUnraisableNode;

    @Child private IsBuiltinClassProfile stopIterProfile1 = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile stopIterProfile2 = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile stopIterProfile3 = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile genExitProfile = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile hasNoCloseProfile = IsBuiltinClassProfile.create();
    @Child private IsBuiltinClassProfile hasNoThrowProfile = IsBuiltinClassProfile.create();

    private final int iteratorSlot;

    @Child private ExpressionNode right;

    public YieldFromNode(ExpressionNode right, GeneratorInfo.Mutable generatorInfo) {
        super(generatorInfo);
        iteratorSlot = generatorInfo.nextIteratorSlotIndex();
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
            _i = lib.getIteratorWithState(right.execute(frame), PArguments.getThreadState(frame));
            try {
                _y = next.execute(frame, _i);
            } catch (PException e) {
                e.expectStopIteration(stopIterProfile1);
                return getGetValue().executeObject(frame, e.setCatchingFrameAndGetEscapedException(frame, this));
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
                if (_s instanceof ThrowData) {
                    gotException.enter();
                    ThrowData _e = (ThrowData) _s;
                    // except GeneratorExit as _e:
                    // ....try:
                    // ........_m = _i.close
                    // ....except AttributeError:
                    // ........pass
                    // ....else:
                    // ........_m()
                    // ....raise _e
                    if (genExitProfile.profileObject(_e.pythonException, PythonBuiltinClassType.GeneratorExit)) {
                        access.setIterator(frame, iteratorSlot, null);
                        Object close = null;
                        try {
                            close = getGetCloseNode().executeObject(frame, _i);
                        } catch (PException pe) {
                            if (!hasNoCloseProfile.profileException(pe, PythonBuiltinClassType.AttributeError)) {
                                ensureWriteUnraisable().execute(frame, pe.setCatchingFrameAndGetEscapedException(frame, this), null, _i);
                            }
                        }
                        if (close != null) {
                            getCallCloseNode().execute(frame, close);
                        }
                        throw PException.fromObject(_e.pythonException, this, _e.withJavaStacktrace);
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
                    Object _m;
                    try {
                        _m = getGetThrowNode().executeObject(frame, _i);
                    } catch (PException pe) {
                        pe.expectAttributeError(hasNoThrowProfile);
                        access.setIterator(frame, iteratorSlot, null);
                        throw PException.fromObject(_e.pythonException, this, _e.withJavaStacktrace);
                    }
                    try {
                        PBaseException pythonException = _e.pythonException;
                        Object excTraceback = ensureGetTracebackNode().execute(pythonException.getTraceback());
                        if (excTraceback == null) {
                            excTraceback = PNone.NONE;
                        }
                        _y = getCallThrowNode().execute(frame, _m, pythonException, PNone.NONE, excTraceback);
                    } catch (PException _e2) {
                        access.setIterator(frame, iteratorSlot, null);
                        _e2.expectStopIteration(stopIterProfile2);
                        return getGetValue().executeObject(frame, _e2.setCatchingFrameAndGetEscapedException(frame, this));
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
                            Object send = getGetSendNode().executeObject(frame, _i);
                            // send will be bound at this point
                            _y = getCallSendNode().execute(frame, send, _s);
                        }
                    } catch (PException _e) {
                        access.setIterator(frame, iteratorSlot, null);
                        _e.expectStopIteration(stopIterProfile3);
                        return getGetValue().executeObject(frame, _e.setCatchingFrameAndGetEscapedException(frame, this));
                    }
                }
            }
        }
    }

    public int getIteratorSlot() {
        return iteratorSlot;
    }

    private GetAttributeNode getGetValue() {
        if (getValue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getValue = insert(GetAttributeNode.create("value", null));
        }
        return getValue;
    }

    private GetAttributeNode getGetCloseNode() {
        if (getCloseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCloseNode = insert(GetAttributeNode.create("close"));
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

    private GetAttributeNode getGetThrowNode() {
        if (getThrowNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getThrowNode = insert(GetAttributeNode.create("throw"));
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

    private GetAttributeNode getGetSendNode() {
        if (getSendNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSendNode = insert(GetAttributeNode.create("send"));
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
            getTracebackNode = insert(GetTracebackNode.create());
        }
        return getTracebackNode;
    }

    private WriteUnraisableNode ensureWriteUnraisable() {
        if (writeUnraisableNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeUnraisableNode = insert(WriteUnraisableNode.create());
        }
        return writeUnraisableNode;
    }
}
