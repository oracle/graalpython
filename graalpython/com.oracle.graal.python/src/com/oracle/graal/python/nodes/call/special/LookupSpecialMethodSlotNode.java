/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * The same as {@link LookupSpecialMethodNode}, but this searches the special slots first. On top of
 * the possible types of return values of {@link LookupSpecialMethodNode}, this node may also return
 * {@link BuiltinMethodDescriptor}, which all the {@link CallBinaryMethodNode} and similar should
 * handle as well.
 */
public abstract class LookupSpecialMethodSlotNode extends LookupSpecialBaseNode {
    protected abstract static class CachedLookup extends LookupSpecialMethodSlotNode {
        protected final SpecialMethodSlot slot;

        public CachedLookup(SpecialMethodSlot slot) {
            this.slot = slot;
        }

        @Specialization
        Object lookup(VirtualFrame frame, Object type, Object receiver,
                        @Bind("this") Node inliningTarget,
                        @Cached(parameters = "slot") LookupCallableSlotInMRONode lookupSlot,
                        @Cached MaybeBindDescriptorNode bind) {
            return bind.execute(frame, inliningTarget, lookupSlot.execute(type), receiver, type);
        }
    }

    @NeverDefault
    public static LookupSpecialMethodSlotNode create(SpecialMethodSlot slot) {
        return LookupSpecialMethodSlotNodeFactory.CachedLookupNodeGen.create(slot);
    }

    private static final class UncachedLookup extends LookupSpecialMethodSlotNode {
        protected final LookupCallableSlotInMRONode lookup;

        public UncachedLookup(SpecialMethodSlot slot) {
            this.lookup = LookupCallableSlotInMRONode.getUncached(slot);
        }

        @Override
        public Object execute(Frame frame, Object type, Object receiver) {
            return executeImpl(type, receiver);
        }

        @TruffleBoundary
        private Object executeImpl(Object type, Object receiver) {
            return MaybeBindDescriptorNode.executeUncached(null, lookup.execute(type), receiver, type);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        private static final UncachedLookup[] UNCACHEDS = new UncachedLookup[SpecialMethodSlot.values().length];
        static {
            SpecialMethodSlot[] values = SpecialMethodSlot.values();
            for (int i = 0; i < values.length; i++) {
                SpecialMethodSlot slot = values[i];
                UNCACHEDS[i] = new UncachedLookup(slot);
            }
        }
    }

    public static LookupSpecialMethodSlotNode getUncached(SpecialMethodSlot slot) {
        return UncachedLookup.UNCACHEDS[slot.ordinal()];
    }
}
