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
package com.oracle.graal.python.nodes.expression;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild("arg")
@NodeChild("arg2")
public abstract class LookupAndCallInplaceNode extends ExpressionNode {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(Object arg, Object arg2);
    }

    protected final String inplaceOpName;
    protected final String binaryOpName;
    protected final String reverseBinaryOpName;
    protected final Supplier<NotImplementedHandler> handlerFactory;

    @Child private CallBinaryMethodNode dispatchNode;
    @Child private NotImplementedHandler handler;

    LookupAndCallInplaceNode(String inplaceOpName, String binaryOpName, String reverseBinaryOpName, Supplier<NotImplementedHandler> handlerFactory) {
        this.inplaceOpName = inplaceOpName;
        this.binaryOpName = binaryOpName;
        this.reverseBinaryOpName = reverseBinaryOpName;
        this.handlerFactory = handlerFactory;
    }

    public static LookupAndCallInplaceNode create(String inplaceOpName) {
        return LookupAndCallInplaceNodeGen.create(inplaceOpName, null, null, null, null, null);
    }

    public static LookupAndCallInplaceNode createWithBinary(String inplaceOpName, ExpressionNode left, ExpressionNode right, Supplier<LookupAndCallInplaceNode.NotImplementedHandler> handlerFactory) {
        return LookupAndCallInplaceNodeGen.create(inplaceOpName, inplaceOpName.replaceFirst("__i", "__"), inplaceOpName.replaceFirst("__i", "__r"), handlerFactory, left, right);
    }

    public static LookupAndCallInplaceNode create(String inplaceOpName, String binaryOpName) {
        return LookupAndCallInplaceNodeGen.create(inplaceOpName, binaryOpName, null, null, null, null);
    }

    public static LookupAndCallInplaceNode create(String inplaceOpName, String binaryOpName, String reverseBinaryOpName, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallInplaceNodeGen.create(inplaceOpName, binaryOpName, reverseBinaryOpName, handlerFactory, null, null);
    }

    private CallBinaryMethodNode ensureDispatch() {
        if (dispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchNode = insert(CallBinaryMethodNode.create());
        }
        return dispatchNode;
    }

    protected boolean hasBinaryVersion() {
        return binaryOpName != null;
    }

    public abstract Object execute(VirtualFrame frame, Object left, Object right);

    @Specialization(guards = "!hasBinaryVersion()")
    Object callObject(VirtualFrame frame, Object left, Object right,
                    @Cached("create(inplaceOpName)") LookupInheritedAttributeNode getattr) {
        Object leftCallable = getattr.execute(left);
        Object result;
        if (leftCallable == PNone.NO_VALUE) {
            result = PNotImplemented.NOT_IMPLEMENTED;
        } else {
            result = ensureDispatch().executeObject(frame, leftCallable, left, right);
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            if (handler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handler = insert(handlerFactory.get());
            }
            return handler.execute(left, right);
        }
        return result;
    }

    @Specialization(guards = "hasBinaryVersion()")
    Object callObject(VirtualFrame frame, Object left, Object right,
                    @Cached("create(inplaceOpName)") LookupInheritedAttributeNode getattrInplace,
                    @Cached("create(binaryOpName, reverseBinaryOpName)") LookupAndCallBinaryNode binaryNode) {
        Object result = PNotImplemented.NOT_IMPLEMENTED;
        Object inplaceCallable = getattrInplace.execute(left);
        if (inplaceCallable != PNone.NO_VALUE) {
            result = ensureDispatch().executeObject(frame, inplaceCallable, left, right);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        if (binaryNode != null) {
            result = binaryNode.executeObject(frame, left, right);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            if (handler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handler = insert(handlerFactory.get());
            }
            return handler.execute(left, right);
        }
        return result;
    }

}
