/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.CallSlotNbBoolNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PyObject_IsTrue}. Converts object to a boolean value using its
 * {@code __bool__} special method. Falls back to comparing {@code __len__} result with 0. Defaults
 * to true if neither is defined.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectIsTrueNode extends PNodeWithContext {
    public final boolean executeCached(Frame frame, Object object) {
        return execute(frame, this, object);
    }

    public abstract boolean execute(Frame frame, Node inliningTarget, Object object);

    protected abstract Object executeObject(Frame frame, Node inliningTarget, Object object);

    public static boolean executeUncached(Object object) {
        return getUncached().execute(null, null, object);
    }

    @Specialization
    static boolean doBoolean(boolean object) {
        return object;
    }

    @Specialization
    static boolean doNone(@SuppressWarnings("unused") PNone object) {
        return false;
    }

    @Specialization
    static boolean doInt(int object) {
        return object != 0;
    }

    @Specialization
    static boolean doLong(long object) {
        return object != 0;
    }

    @Specialization
    static boolean doDouble(double object) {
        return object != 0.0;
    }

    @Specialization
    static boolean doString(TruffleString object) {
        return !object.isEmpty();
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doList(Node inliningTarget, PList object) {
        return object.getSequenceStorage().length() != 0;
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doTuple(Node inliningTarget, PTuple object) {
        return object.getSequenceStorage().length() != 0;
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static boolean doDict(Node inliningTarget, PDict object,
                    @Exclusive @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) != 0;
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)", limit = "1")
    @InliningCutoff
    static boolean doSet(Node inliningTarget, PSet object,
                    @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Exclusive @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage()) != 0;
    }

    // Intentionally not a fallback. We replace specializations with complex guards to avoid them in
    // the fallback guard. Possible improvement: check if not replacing the other fast-paths pay
    // off, because in the no-fast-path case, these extra typechecks have some measurable overhead
    // in micro benchmarks (if-polymorphic)
    @Specialization(guards = {"!isBoolean(object)", "!isPNone(object)", "!isInt(object)", "!isLong(object)", "!isDouble(object)", "!isTruffleString(object)"}, //
                    replaces = {"doList", "doTuple", "doDict", "doSet"})
    @InliningCutoff
    static boolean doOthers(Frame frame, Object object,
                    @Cached(inline = false) PyObjectIsTrueNodeGeneric internalNode) {
        // Cached PyObjectItTrue nodes used in PBytecodeRootNode are significant contributors to
        // footprint, so we use indirection to save all the fields for the nodes used in the generic
        // variant + this is one polymorphic dispatch to the execute method. Inside the cached
        // execute method, the hosted inlining can then inline cached nodes, unlike if we inlined to
        // logic here
        return internalNode.execute(frame, object);
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class PyObjectIsTrueNodeGeneric extends PNodeWithContext {
        public abstract boolean execute(Frame frame, Object object);

        protected abstract Object executeObject(Frame frame, Object object);

        @Specialization
        static boolean doIt(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getTpSlotsNode,
                        @Cached CallSlotNbBoolNode callBoolNode,
                        @Cached InlinedBranchProfile lenLookupBranch,
                        @Cached InlinedConditionProfile hasLenProfile,
                        @Cached CallSlotLenNode callLenNode) {
            // Full transcript of CPython:PyObject_IsTrue logic
            TpSlots slots = getTpSlotsNode.execute(inliningTarget, object);
            if (slots.nb_bool() != null) {
                return callBoolNode.execute(frame, inliningTarget, slots.nb_bool(), object);
            }
            lenLookupBranch.enter(inliningTarget);
            TpSlot lenSlot = slots.combined_mp_sq_length();
            if (hasLenProfile.profile(inliningTarget, lenSlot != null)) {
                return callLenNode.execute(frame, inliningTarget, lenSlot, object) != 0;
            }
            return true;
        }
    }

    @NeverDefault
    public static PyObjectIsTrueNode create() {
        return PyObjectIsTrueNodeGen.create();
    }

    public static PyObjectIsTrueNode getUncached() {
        return PyObjectIsTrueNodeGen.getUncached();
    }
}
