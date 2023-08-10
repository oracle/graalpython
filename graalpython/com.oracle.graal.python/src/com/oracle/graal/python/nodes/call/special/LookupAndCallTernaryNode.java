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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

// actual implementation is in the subclasses: one for reversible, other for non-reversible.
@ImportStatic({SpecialMethodNames.class, PythonOptions.class})
public abstract class LookupAndCallTernaryNode extends Node {
    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(Object arg, Object arg2, Object arg3);
    }

    protected final TruffleString name;
    protected final SpecialMethodSlot slot;

    public abstract Object execute(VirtualFrame frame, Object arg1, Object arg2, Object arg3);

    @NeverDefault
    public static LookupAndCallTernaryNode create(TruffleString name) {
        // Use SpecialMethodSlot overload for special slots, if there is a need to create
        // LookupAndCallBinaryNode for dynamic name, then we should change this method or the caller
        // to try to lookup a slot and use that if found
        assert SpecialMethodSlot.findSpecialSlotUncached(name) == null : name;
        return LookupAndCallNonReversibleTernaryNodeGen.create(name);
    }

    @NeverDefault
    public static LookupAndCallTernaryNode create(SpecialMethodSlot slot) {
        return LookupAndCallNonReversibleTernaryNodeGen.create(slot);
    }

    @NeverDefault
    public static LookupAndCallTernaryNode createReversible(TruffleString name, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallReversibleTernaryNodeGen.create(name, handlerFactory);
    }

    @NeverDefault
    public static LookupAndCallTernaryNode createReversible(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallReversibleTernaryNodeGen.create(slot, handlerFactory);
    }

    LookupAndCallTernaryNode(TruffleString name) {
        this.name = name;
        this.slot = null;
    }

    LookupAndCallTernaryNode(SpecialMethodSlot slot) {
        this.slot = slot;
        this.name = slot.getName();
    }

    @NeverDefault
    protected final LookupSpecialBaseNode createLookup() {
        if (slot != null) {
            return LookupSpecialMethodSlotNode.create(slot);
        }
        return LookupSpecialMethodNode.create(name);
    }
}
