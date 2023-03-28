/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.SlotGroup;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public abstract class ReadSlotByNameNode extends Node {
    protected final SlotMethodDef[] slots;

    public ReadSlotByNameNode(SlotGroup group) {
        this.slots = group.slots;
    }

    public abstract Object execute(PythonNativeWrapper wrapper, String member);

    @Specialization(guards = {"cachedMember.equals(member)", "slot != null"}, limit = "slots.length")
    Object cachedMember(PythonNativeWrapper wrapper, @SuppressWarnings("unused") String member,
                    @SuppressWarnings("unused") @Cached("member") String cachedMember,
                    @SuppressWarnings("unused") @Cached("getSlot(cachedMember)") SlotMethodDef slot,
                    @Cached(parameters = "slot") LookupNativeSlotNode lookup) {
        return lookup.execute(wrapper.getDelegate());
    }

    @Specialization(guards = "getSlot(member) == null")
    @SuppressWarnings("unused")
    Object miss(PythonNativeWrapper wrapper, String member) {
        return null;
    }

    private static final ReadSlotByNameNode[] UNCACHED = new ReadSlotByNameNode[SlotGroup.values().length];
    static {
        for (int i = 0; i < SlotGroup.values().length; i++) {
            UNCACHED[i] = new Uncached(SlotGroup.values()[i]);
        }
    }

    public static ReadSlotByNameNode getUncached(SlotGroup group) {
        return UNCACHED[group.ordinal()];
    }

    @GenerateCached(false)
    private static final class Uncached extends ReadSlotByNameNode {

        public Uncached(SlotGroup group) {
            super(group);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        @TruffleBoundary
        public Object execute(PythonNativeWrapper wrapper, String member) {
            SlotMethodDef slot = getSlot(member);
            if (slot != null) {
                return LookupNativeSlotNode.executeUncached(wrapper.getDelegate(), slot);
            }
            return null;
        }
    }

    @ExplodeLoop
    public SlotMethodDef getSlot(String member) {
        for (SlotMethodDef slot : slots) {
            if (slot.getMemberNameJavaString().equals(member)) {
                return slot;
            }
        }
        return null;
    }
}
