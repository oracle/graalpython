/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Bind the descriptor to the receiver unless it's one of the descriptor types that our method call
 * nodes handle unbound.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(SpecialMethodSlot.class)
public abstract class MaybeBindDescriptorNode extends PNodeWithContext {

    public abstract Object execute(Frame frame, Node inliningTarget, Object descriptor, Object receiver, Object receiverType);

    public static Object executeUncached(Frame frame, Object descriptor, Object receiver, Object receiverType) {
        return MaybeBindDescriptorNodeGen.getUncached().execute(frame, null, descriptor, receiver, receiverType);
    }

    @Specialization(guards = "isNoValue(descriptor)")
    static Object doNoValue(Object descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(BuiltinMethodDescriptor descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(PBuiltinFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    @Specialization
    static Object doBuiltin(PBuiltinMethod descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return new BoundDescriptor(descriptor);
    }

    @Specialization
    static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object receiverType) {
        return descriptor;
    }

    public static boolean isMethodDescriptor(Object descriptor) {
        return descriptor instanceof BuiltinMethodDescriptor || (descriptor instanceof PBuiltinFunction pbf && !pbf.needsDeclaringType()) ||
                        descriptor instanceof PFunction;
    }

    public static boolean needsToBind(Object descriptor) {
        return !(descriptor == PNone.NO_VALUE || isMethodDescriptor(descriptor));
    }

    @Specialization(guards = "needsToBind(descriptor)")
    static Object doBind(VirtualFrame frame, Node inliningTarget, Object descriptor, Object receiver, Object receiverType,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached CallSlotDescrGet callGetNode) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, descriptor);
        if (slots.tp_descr_get() != null) {
            return new BoundDescriptor(callGetNode.execute(frame, inliningTarget, slots.tp_descr_get(), descriptor, receiver, receiverType));
        }
        // CPython considers non-descriptors already bound
        return new BoundDescriptor(descriptor);
    }
}
