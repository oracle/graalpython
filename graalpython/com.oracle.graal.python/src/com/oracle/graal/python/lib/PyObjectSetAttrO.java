/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.CallSlotSetAttrONode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Like {@link PyObjectSetAttr}, but accepts any object as a key.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectSetAttrO extends PNodeWithContext {
    public static void executeUncached(Object receiver, Object name, Object value) {
        PyObjectSetAttrONodeGen.getUncached().execute(null, null, receiver, name, value);
    }

    public static PyObjectSetAttrO create() {
        return PyObjectSetAttrONodeGen.create();
    }

    public final void executeCached(VirtualFrame frame, Object object, Object key, Object value) {
        execute(frame, this, object, key, value);
    }

    public abstract void execute(Frame frame, Node inliningTarget, Object receiver, Object name, Object value);

    @Specialization
    static void doIt(Frame frame, Node inliningTarget, Object self, TruffleString name, Object value,
                    @Shared @Cached PyObjectSetAttr setAttr) {
        setAttr.execute(frame, inliningTarget, self, name, value);
    }

    @Specialization(guards = "isBuiltinPString(name)")
    static void doIt(Frame frame, Node inliningTarget, Object self, PString name, Object value,
                    @Cached CastToTruffleStringNode castNode,
                    @Shared @Cached PyObjectSetAttr setAttr) {
        setAttr.execute(frame, inliningTarget, self, castNode.castKnownString(inliningTarget, name), value);
    }

    @Fallback
    @InliningCutoff
    static void doIt(Frame frame, Object self, Object nameObj, Object value,
                    @Cached(inline = false) PyObjectSetAttrOGeneric generic) {
        generic.execute(frame, self, nameObj, value);
    }

    @GenerateInline(false) // intentionally lazy
    @GenerateUncached
    public abstract static class PyObjectSetAttrOGeneric extends Node {
        public abstract void execute(Frame frame, Object self, Object nameObj, Object value);

        @Specialization
        static void doIt(Frame frame, Object self, Object nameObj, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PRaiseNode.Lazy raise,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached CallSlotSetAttrONode callSetAttr) {
            if (!unicodeCheckNode.execute(inliningTarget, nameObj)) {
                throw raise.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ATTR_NAME_MUST_BE_STRING, nameObj);
            }
            assert value != null; // should use PNone.NO_VALUE
            TpSlots slots = getSlotsNode.execute(inliningTarget, self);
            if (slots.combined_tp_setattro_setattr() != null) {
                callSetAttr.execute((VirtualFrame) frame, inliningTarget, slots, self, nameObj, value);
            } else {
                PyObjectSetAttr.raiseNoSlotError(inliningTarget, self, nameObj, value, raise, slots);
            }
        }
    }
}
