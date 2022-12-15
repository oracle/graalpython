/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

// cpython://Objects/abstract.c#binary_op1
// Order operations are tried until either a valid result or error: w.op(v,w)[*], v.op(v,w), w.op(v,w)
//
//       [*] only when v->ob_type != w->ob_type && w->ob_type is a subclass of v->ob_type
//
// The (long, double) and (double, long) specializations are needed since long->double conversion
// is not always correct (it can lose information). See FloatBuiltins.EqNode.compareDoubleToLong().
// The (int, double) and (double, int) specializations are needed to avoid int->long conversion.
// Although it would produce correct results, the special handling of long to double comparison
// is slower than converting int->double, which is always correct.
@ImportStatic(PythonOptions.class)
public abstract class LookupAndCallBinaryNode extends Node {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object arg, Object arg2);
    }

    protected final Supplier<NotImplementedHandler> handlerFactory;
    protected final boolean ignoreDescriptorException;

    @Child private CallBinaryMethodNode dispatchNode;
    @Child private NotImplementedHandler handler;

    LookupAndCallBinaryNode(Supplier<NotImplementedHandler> handlerFactory, boolean ignoreDescriptorException) {
        this.handlerFactory = handlerFactory;
        this.ignoreDescriptorException = ignoreDescriptorException;
    }

    public abstract Object executeObject(VirtualFrame frame, Object arg, Object arg2);

    @NeverDefault
    public static LookupAndCallBinaryNode create(TruffleString name) {
        // Use SpecialMethodSlot overload for special slots, if there is a need to create
        // LookupAndCallBinaryNode for dynamic name, then we should change this method or the caller
        // to try to lookup a slot and use that if found
        assert SpecialMethodSlot.findSpecialSlotUncached(name) == null : name;
        return LookupAndCallNonReversibleBinaryNodeGen.create(name, null, false);
    }

    @NeverDefault
    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot) {
        return LookupAndCallNonReversibleBinaryNodeGen.create(slot, null, false);
    }

    @NeverDefault
    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallNonReversibleBinaryNodeGen.create(slot, handlerFactory, false);
    }

    @NeverDefault
    public static LookupAndCallBinaryNode createReversible(SpecialMethodSlot slot, SpecialMethodSlot rslot, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallReversibleBinaryNodeGen.create(slot, rslot, handlerFactory, false, false);
    }

    @NeverDefault
    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot, SpecialMethodSlot rslot, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        return LookupAndCallReversibleBinaryNodeGen.create(slot, rslot, null, alwaysCheckReverse, ignoreDescriptorException);
    }

    protected static Object getMethod(Object receiver, TruffleString methodName) {
        return LookupSpecialMethodNode.Dynamic.getUncached().execute(null, GetClassNode.getUncached().execute(receiver), methodName, receiver);
    }

    public abstract TruffleString getName();

    public abstract TruffleString getRname();

    protected final CallBinaryMethodNode ensureDispatch() {
        // this also serves as a branch profile
        if (dispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchNode = insert(CallBinaryMethodNode.create());
        }
        return dispatchNode;
    }

    protected final Object runErrorHandler(VirtualFrame frame, Object left, Object right) {
        if (handler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handler = insert(handlerFactory.get());
        }
        return handler.execute(frame, left, right);
    }
}
