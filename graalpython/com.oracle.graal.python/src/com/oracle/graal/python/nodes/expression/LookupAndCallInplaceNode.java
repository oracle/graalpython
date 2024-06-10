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
package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInMROBaseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class LookupAndCallInplaceNode extends PNodeWithContext implements BinaryOp {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(Object arg, Object arg2);
    }

    @Child private CallBinaryMethodNode callBinaryMethodNode;
    @Child private BinaryOpNode binaryOpNode;
    @Child private LookupAndCallTernaryNode lookupAndCallTernaryNode;
    @Child private NotImplementedHandler handler;

    final InplaceArithmetic arithmetic;

    LookupAndCallInplaceNode(InplaceArithmetic arithmetic) {
        this.arithmetic = arithmetic;
    }

    @NeverDefault
    public static LookupAndCallInplaceNode create(InplaceArithmetic arithmetic) {
        return LookupAndCallInplaceNodeGen.create(arithmetic);
    }

    private CallBinaryMethodNode ensureBinaryCallNode() {
        if (callBinaryMethodNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callBinaryMethodNode = insert(CallBinaryMethodNode.create());
        }
        return callBinaryMethodNode;
    }

    private BinaryOpNode ensureLookupAndCallBinaryNode() {
        if (binaryOpNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            binaryOpNode = insert(arithmetic.binary.create());
        }
        return binaryOpNode;
    }

    private LookupAndCallTernaryNode ensureLookupAndCallTernaryNode() {
        if (lookupAndCallTernaryNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupAndCallTernaryNode = insert(LookupAndCallTernaryNode.createReversible(arithmetic.binaryOpName, null));
        }
        return lookupAndCallTernaryNode;
    }

    @Override
    public final Object executeObject(VirtualFrame frame, Object left, Object right) {
        return execute(frame, left, right);
    }

    public final Object execute(VirtualFrame frame, Object left, Object right) {
        return executeTernary(frame, left, right, PNone.NO_VALUE);
    }

    public abstract Object executeTernary(VirtualFrame frame, Object x, Object y, Object z);

    @NeverDefault
    protected final LookupInMROBaseNode createInplaceLookup() {
        if (arithmetic.slot != null) {
            return LookupCallableSlotInMRONode.create(arithmetic.slot);
        } else {
            return LookupAttributeInMRONode.create(arithmetic.methodName);
        }
    }

    @Specialization
    Object doBinary(VirtualFrame frame, Object left, Object right, Object z,
                    @Bind("this") Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Cached("createInplaceLookup()") LookupInMROBaseNode lookupInplace) {
        Object result;
        Object inplaceCallable = lookupInplace.execute(getClassNode.execute(inliningTarget, left));
        if (inplaceCallable != PNone.NO_VALUE) {
            // nb.: The only ternary in-place operator is '__ipow__' but according to 'typeobject.c:
            // slot_nb_inplace_power', this is always called as binary.
            result = ensureBinaryCallNode().executeObject(frame, inplaceCallable, left, right);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }

        // try non-inplace variant
        boolean isBinary = PGuards.isPNone(z);
        if (isBinary) {
            result = ensureLookupAndCallBinaryNode().executeObject(frame, left, right);
        } else {
            result = ensureLookupAndCallTernaryNode().execute(frame, left, right, z);
        }
        if (result != PNotImplemented.NOT_IMPLEMENTED) {
            return result;
        }
        if (handler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handler = insert(arithmetic.notImplementedHandler.get());
        }
        return handler.execute(left, right);
    }
}
