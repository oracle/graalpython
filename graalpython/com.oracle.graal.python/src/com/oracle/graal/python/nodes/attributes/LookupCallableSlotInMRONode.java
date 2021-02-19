/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * The same as {@link LookupAttributeInMRONode}, but this may also return an instance of
 * {@link com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltinsFactory.BuiltinNodeFactory}
 * if available. {@link com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode} and similar
 * should accept
 * {@link com.oracle.graal.python.builtins.modules.GraalPythonModuleBuiltinsFactory.BuiltinNodeFactory}
 * as a callable.
 */
@ImportStatic({PythonOptions.class, PythonLanguage.class})
public abstract class LookupCallableSlotInMRONode extends LookupInMROBaseNode {

    protected final SpecialMethodSlot slot;

    protected LookupCallableSlotInMRONode(SpecialMethodSlot slot) {
        this.slot = slot;
    }

    // Single and multi context:
    // PythonBuiltinClassType: if there is a value for the slot in PythonBuiltinClassType, then we
    // can just cache it even in multi-context case
    @Specialization(guards = {"klass == cachedKlass", "result != null"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    Object doBuiltinTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @SuppressWarnings("unused") @Cached(value = "klass") PythonBuiltinClassType cachedKlass,
                    @Cached(value = "slot.getValue(klass)") Object result) {
        assert isCacheable(result);
        return result;
    }

    // Single-context

    @Specialization(guards = "klass == cachedKlass", //
                    assumptions = {"singleContextAssumption()", "cachedKlass.getSlotsFinalAssumption()"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    Object doSlotCachedSingleCtx(@SuppressWarnings("unused") PythonClass klass,
                    @SuppressWarnings("unused") @Cached(value = "klass", weak = true) PythonClass cachedKlass,
                    @Cached(value = "slot.getValue(klass)", weak = true) Object result) {
        return result;
    }

    @Specialization(guards = "klass == cachedKlass", assumptions = {"singleContextAssumption()"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    Object doBuiltinCachedSingleCtx(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass") PythonBuiltinClass cachedKlass,
                    @Cached("slot.getValue(klass)") Object result) {
        return result;
    }

    // PythonBuiltinClassType: if the value of the slot is not node factory or None, we must read
    // the slot from the resolved builtin class
    @Specialization(guards = {"klassType == cachedKlassType", "slot.getValue(cachedKlassType) == null"}, //
                    assumptions = {"singleContextAssumption()"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    Object doBuiltinTypeCachedSingleCtx(@SuppressWarnings("unused") PythonBuiltinClassType klassType,
                    @SuppressWarnings("unused") @Cached("klassType") PythonBuiltinClassType cachedKlassType,
                    @Cached("slot.getValue(getCore().lookupType(cachedKlassType))") Object value) {
        return value;
    }

    // Multi-context:

    @Specialization(replaces = "doSlotCachedSingleCtx")
    Object doBuiltinGenericMultiCtx(PythonClass klass) {
        return slot.getValue(klass);
    }

    // For PythonBuiltinClass it depends on whether we can cache the result:

    protected static boolean isCacheable(Object value) {
        return PythonLanguage.canCache(value) || value instanceof BuiltinMethodInfo;
    }

    @Specialization(guards = {"klass.getType() == cachedType", "isCacheable(result)"}, //
                    replaces = "doBuiltinCachedSingleCtx", limit = "getAttributeAccessInlineCacheMaxDepth()")
    Object doBuiltinCachedMultiCtx(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                    @Cached("slot.getValue(klass)") Object result) {
        return result;
    }

    @Specialization(replaces = "doBuiltinCachedSingleCtx")
    Object doBuiltinUncachableMultiCtx(PythonBuiltinClass klass) {
        return slot.getValue(klass);
    }

    // PythonBuiltinClassType: if the value of the slot is null, we must read the slot from the
    // resolved builtin class
    @Specialization(guards = {"klassType == cachedKlassType", "slot.getValue(cachedKlassType) == null"})
    Object doBuiltinTypeMultiContext(@SuppressWarnings("unused") PythonBuiltinClassType klassType,
                    @SuppressWarnings("unused") @Cached("klassType") PythonBuiltinClassType cachedKlassType,
                    @Bind("slot.getValue(getCore().lookupType(cachedKlassType))") Object value) {
        return value;
    }

    // Fallback when the cache with PythonBuiltinClassType overflows:

    @Specialization(replaces = {"doBuiltinTypeCached", "doBuiltinTypeCachedSingleCtx", "doBuiltinTypeMultiContext"})
    Object doBuiltinTypeGeneric(PythonBuiltinClassType klass) {
        Object result = slot.getValue(klass);
        if (result != null) {
            return result;
        } else {
            return slot.getValue(getCore().lookupType(klass));
        }
    }

    // Native classes:

    @Specialization
    Object doNativeClass(PythonAbstractNativeObject klass,
                    @Cached("create(slot.getName())") LookupAttributeInMRONode lookup) {
        return lookup.execute(klass);
    }
}
