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
package com.oracle.graal.python.nodes.attributes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.UnaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * The same as {@link LookupAttributeInMRONode}, but this may also return an instance of
 * {@link BuiltinMethodDescriptor}.
 * {@link com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode} and similar should accept
 * such objects as a callable.
 */
@ImportStatic({PythonOptions.class, PythonLanguage.class})
public abstract class LookupCallableSlotInMRONode extends LookupInMROBaseNode {

    protected abstract static class CachedLookup extends LookupCallableSlotInMRONode {
        protected final SpecialMethodSlot slot;

        protected CachedLookup(SpecialMethodSlot slot) {
            this.slot = slot;
        }

        // Single and multi context:
        // PythonBuiltinClassType: if there is a value for the slot in PythonBuiltinClassType, then
        // we can just cache it even in multi-context case
        @Specialization(guards = {"klass == cachedKlass", "result != null"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doBuiltinTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                        @SuppressWarnings("unused") @Cached(value = "klass") PythonBuiltinClassType cachedKlass,
                        @Cached(value = "slot.getValue(klass)") Object result) {
            assert isCacheable(result) : result;
            return result;
        }

        // Single-context

        @Specialization(guards = {"isSingleContext()", "klass == cachedKlass"}, //
                        assumptions = "cachedKlass.getSlotsFinalAssumption()", //
                        limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doSlotCachedSingleCtx(@SuppressWarnings("unused") PythonClass klass,
                        @SuppressWarnings("unused") @Cached(value = "klass", weak = true) PythonClass cachedKlass,
                        @Cached(value = "slot.getValue(klass)", weak = true) Object result) {
            return result;
        }

        @Specialization(guards = {"isSingleContext()", "klass == cachedKlass"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doBuiltinCachedSingleCtx(@SuppressWarnings("unused") PythonBuiltinClass klass,
                        @SuppressWarnings("unused") @Cached("klass") PythonBuiltinClass cachedKlass,
                        @Cached("slot.getValue(klass)") Object result) {
            return result;
        }

        // PythonBuiltinClassType: if the value of the slot is not node factory or None, we must
        // read
        // the slot from the resolved builtin class
        @Specialization(guards = {"isSingleContext()", "klassType == cachedKlassType", "slot.getValue(cachedKlassType) == null"}, //
                        limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doBuiltinTypeCachedSingleCtx(@SuppressWarnings("unused") PythonBuiltinClassType klassType,
                        @SuppressWarnings("unused") @Cached("klassType") PythonBuiltinClassType cachedKlassType,
                        @Cached("slot.getValue(getContext().lookupType(cachedKlassType))") Object value) {
            return value;
        }

        // Multi-context:

        @Specialization(replaces = "doSlotCachedSingleCtx", guards = {"slot.getValue(klass) == result", "isCacheable(result)"}, //
                        limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doSlotCachedMultiCtx(@SuppressWarnings("unused") PythonClass klass,
                        @Cached("slot.getValue(klass)") Object result) {
            // in multi-context we can still cache primitives and BuiltinMethodDescriptor instances
            return result;
        }

        @Specialization(replaces = "doSlotCachedMultiCtx")
        Object doSlotUncachedMultiCtx(PythonClass klass,
                        @Bind("this") Node inliningTarget,
                        @Shared("slotValueProfile") @Cached SlotValueProfile slotValueProfile) {
            return slotValueProfile.profile(inliningTarget, slot.getValue(klass));
        }

        // For PythonBuiltinClass it depends on whether we can cache the result:

        @Idempotent
        protected static boolean isCacheable(Object value) {
            return PythonLanguage.canCache(value) || BuiltinMethodDescriptor.isInstance(value);
        }

        @Specialization(guards = {"klass.getType() == cachedType", "isCacheable(result)"}, //
                        replaces = "doBuiltinCachedSingleCtx", limit = "getAttributeAccessInlineCacheMaxDepth()")
        static Object doBuiltinCachedMultiCtx(@SuppressWarnings("unused") PythonBuiltinClass klass,
                        @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                        @Cached("slot.getValue(klass)") Object result) {
            return result;
        }

        @Specialization(replaces = {"doBuiltinCachedSingleCtx", "doBuiltinCachedMultiCtx"})
        Object doBuiltinUncachableMultiCtx(PythonBuiltinClass klass,
                        @Bind("this") Node inliningTarget,
                        @Shared("slotValueProfile") @Cached SlotValueProfile slotValueProfile) {
            return slotValueProfile.profile(inliningTarget, slot.getValue(klass));
        }

        // PythonBuiltinClassType: if the value of the slot is null, we must read the slot from the
        // resolved builtin class
        @Specialization(guards = {"klassType == cachedKlassType", "slot.getValue(cachedKlassType) == null"}, limit = "1")
        static Object doBuiltinTypeMultiContext(@SuppressWarnings("unused") PythonBuiltinClassType klassType,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached SlotValueProfile slotValueProfile,
                        @SuppressWarnings("unused") @Cached("klassType") PythonBuiltinClassType cachedKlassType,
                        @Bind("slot.getValue(getContext().lookupType(cachedKlassType))") Object value) {
            return slotValueProfile.profile(inliningTarget, value);
        }

        // Fallback when the cache with PythonBuiltinClassType overflows:

        @Specialization(replaces = {"doBuiltinTypeCached", "doBuiltinTypeCachedSingleCtx", "doBuiltinTypeMultiContext"})
        Object doBuiltinTypeGeneric(PythonBuiltinClassType klass,
                        @Bind("this") Node inliningTarget,
                        @Shared("slotValueProfile") @Cached SlotValueProfile slotValueProfile) {
            Object result = slot.getValue(klass);
            if (result == null) {
                result = slot.getValue(PythonContext.get(this).lookupType(klass));
            }
            return slotValueProfile.profile(inliningTarget, result);
        }

        // Native classes:

        @Specialization
        @InliningCutoff
        static Object doNativeClass(PythonAbstractNativeObject klass,
                        @Bind("this") Node inliningTarget,
                        @Shared("slotValueProfile") @Cached SlotValueProfile slotValueProfile,
                        @Cached("create(slot.getName())") LookupAttributeInMRONode lookup) {
            return slotValueProfile.profile(inliningTarget, lookup.execute(klass));
        }
    }

    @NeverDefault
    public static LookupCallableSlotInMRONode create(SpecialMethodSlot slot) {
        return LookupCallableSlotInMRONodeFactory.CachedLookupNodeGen.create(slot);
    }

    protected static final class UncachedLookup extends LookupCallableSlotInMRONode {

        private final SpecialMethodSlot slot;

        private UncachedLookup(SpecialMethodSlot slot) {
            this.slot = slot;
        }

        @Override
        @TruffleBoundary
        public final Object execute(Object klass) {
            if (klass instanceof PythonBuiltinClassType) {
                Object result = slot.getValue((PythonBuiltinClassType) klass);
                if (result == null) {
                    result = slot.getValue(PythonContext.get(null).lookupType((PythonBuiltinClassType) klass));
                }
                return result;
            } else if (klass instanceof PythonManagedClass) {
                return slot.getValue((PythonManagedClass) klass);
            } else {
                assert klass instanceof PythonAbstractNativeObject;
                return LookupAttributeInMRONode.Dynamic.getUncached().execute(klass, slot.getName());
            }
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

    public static LookupCallableSlotInMRONode getUncached(SpecialMethodSlot slot) {
        return UncachedLookup.UNCACHEDS[slot.ordinal()];
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    protected abstract static class SlotValueProfile extends Node {
        final Object profile(Node inliningTarget, Object value) {
            return execute(inliningTarget, value);
        }

        abstract Object execute(Node inliningTarget, Object value);

        @Specialization
        static UnaryBuiltinDescriptor unaryDescr(UnaryBuiltinDescriptor value) {
            return value;
        }

        @Specialization
        static BinaryBuiltinDescriptor binaryDescr(BinaryBuiltinDescriptor value) {
            return value;
        }

        @Specialization
        static TernaryBuiltinDescriptor ternaryDescr(TernaryBuiltinDescriptor value) {
            return value;
        }

        @Specialization
        static PBuiltinFunction builtin(PBuiltinFunction builtin) {
            return builtin;
        }

        @Specialization
        static PFunction fun(PFunction fun) {
            return fun;
        }

        @Specialization(guards = "isNoValue(none)")
        static PNone noValue(@SuppressWarnings("unused") PNone none) {
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isNone(none)")
        static PNone none(@SuppressWarnings("unused") PNone none) {
            return PNone.NONE;
        }

        // Intentionally not guarded, if it is activated first, we want to just bail out from
        // profiling
        @Specialization(replaces = {"unaryDescr", "binaryDescr", "ternaryDescr", "builtin", "fun", "noValue", "none"})
        static Object other(Object value) {
            return value;
        }
    }
}
