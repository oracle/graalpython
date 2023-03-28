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
package com.oracle.graal.python.nodes.attributes;

import static com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.findAttr;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGuards;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.MroShape;
import com.oracle.graal.python.builtins.objects.type.MroShape.MroShapeLookupResult;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode.AttributeAssumptionPair;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@ImportStatic(LookupAttributeInMRONode.class)
public abstract class LookupNativeSlotNode extends PNodeWithContext {
    protected final SlotMethodDef slot;

    public LookupNativeSlotNode(SlotMethodDef slot) {
        this.slot = slot;
    }

    public abstract Object execute(Object type);

    private final static LookupNativeSlotNode[] UNCACHED = new LookupNativeSlotNode[SlotMethodDef.values().length];

    static {
        for (int i = 0; i < SlotMethodDef.values().length; i++) {
            SlotMethodDef slot = SlotMethodDef.values()[i];
            UNCACHED[i] = new Uncached(slot);
        }
    }

    public static LookupNativeSlotNode getUncached(SlotMethodDef slot) {
        return UNCACHED[slot.ordinal()];
    }

    @TruffleBoundary
    public static Object executeUncached(Object type, SlotMethodDef slot) {
        return getUncached(slot).execute(type);
    }

    @Specialization(guards = {"isSingleContext()", "klass == cachedKlass"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected Object lookupPBCTCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getContext(), cachedKlass, slot.methodName)") Object cachedValue) {
        return wrapManagedMethod(cachedValue);
    }

    @Specialization(guards = {"klass == cachedKlass", "canCache(cachedValue)"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected Object lookupPBCTCachedMulti(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getContext(), cachedKlass, slot.methodName)") Object cachedValue) {
        return wrapManagedMethod(cachedValue);
    }

    @Specialization(replaces = {"lookupPBCTCached", "lookupPBCTCachedMulti"}, guards = "klass == cachedKlass", //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected Object lookupPBCTCachedOwner(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findOwnerInMro(getContext(), cachedKlass, slot.methodName)") PythonBuiltinClassType ownerKlass,
                    @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
        if (ownerKlass == null) {
            return getNULL();
        } else {
            return wrapManagedMethod(readAttrNode.execute(PythonContext.get(this).lookupType(ownerKlass), slot.methodName));
        }
    }

    @Specialization(replaces = "lookupPBCTCachedOwner")
    protected Object lookupPBCTGeneric(PythonBuiltinClassType klass,
                    @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
        return wrapManagedMethod(findAttr(PythonContext.get(this), klass, slot.methodName, readAttrNode));
    }

    // PythonClass specializations:

    protected AttributeAssumptionPair findAttrAndAssumptionInMRO(Object klass) {
        CompilerAsserts.neverPartOfCompilation();
        // - avoid cases when attributes are stored in a dict containing elements
        // with a potential MRO sideeffect on access.
        // Also note that attr should not be read more than once.
        // - assuming that keys/elements can't be added or replaced in a class dict.
        // (PythonMangedClass returns MappingProxy, which is read-only). Native classes could
        // possibly do so, but for now leaving it as it is.
        PDict dict;
        if (klass instanceof PythonAbstractNativeObject) {
            Object nativedict = CExtNodes.GetTypeMemberNode.getUncached().execute(klass, NativeMember.TP_DICT);
            dict = nativedict == PNone.NO_VALUE ? null : (PDict) nativedict;
        } else {
            dict = GetDictIfExistsNode.getUncached().execute(klass);
        }
        if (dict != null && HashingStorageGuards.mayHaveSideEffects(dict)) {
            return null;
        }
        MroSequenceStorage mro = GetMroStorageNode.getUncached().execute(klass);
        Assumption attrAssumption = mro.createAttributeInMROFinalAssumption(slot.methodName);
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass clsObj = mro.getItemNormalized(i);
            if (i > 0) {
                assert clsObj != klass : "MRO chain is incorrect: '" + klass + "' was found at position " + i;
                GetMroStorageNode.getUncached().execute(clsObj).addAttributeInMROFinalAssumption(slot.methodName, attrAssumption);
            }
            Object value = readSlot(clsObj, ReadAttributeFromObjectNode.getUncachedForceType(), ToSulongNode.getUncached(), PCallCapiFunction.getUncached(), InteropLibrary.getUncached());
            if (value != null) {
                return new AttributeAssumptionPair(attrAssumption, value);
            }
        }
        return new AttributeAssumptionPair(attrAssumption, getNULL());
    }

    @Specialization(guards = {"isSingleContext()", "isSameTypeNode.execute(inliningTarget, cachedKlass, klass)", "cachedAttrInMROInfo != null"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    assumptions = "cachedAttrInMROInfo.assumption")
    protected static Object lookupConstantMROCached(@SuppressWarnings("unused") Object klass,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached InlinedIsSameTypeNode isSameTypeNode,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                    @Cached("findAttrAndAssumptionInMRO(cachedKlass)") AttributeAssumptionPair cachedAttrInMROInfo) {
        return cachedAttrInMROInfo.value;
    }

    // This specialization works well only for multi-context mode
    // Note: MroShape creation and updates are disabled in multi-context mode, see
    // PythonClass#initializeMroShape
    @Specialization(guards = {"!isSingleContext()", "cachedMroShape != null", "klass.getMroShape() == cachedMroShape"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected Object lookupConstantMROShape(PythonClass klass,
                    @Cached GetMroStorageNode getMroStorageNode,
                    @SuppressWarnings("unused") @Cached("klass.getMroShape()") MroShape cachedMroShape,
                    @Cached("lookupInMroShape(cachedMroShape, klass)") MroShapeLookupResult lookupResult) {
        return wrapManagedMethod(lookupResult.getFromMro(getMroStorageNode.execute(klass), slot.methodName));
    }

    @Specialization(guards = {"isSingleContext()", "isSameTypeNode.execute(inliningTarget, cachedKlass, klass)", "mroLength < 32"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    replaces = "lookupConstantMROShape", //
                    assumptions = "lookupStable")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected Object lookupConstantMRO(@SuppressWarnings("unused") Object klass,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached InlinedIsSameTypeNode isSameTypeNode,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMroStorageNode,
                    @Cached("getMroStorageNode.execute(cachedKlass)") MroSequenceStorage mro,
                    @Cached("mro.getLookupStableAssumption()") @SuppressWarnings("unused") Assumption lookupStable,
                    @Cached("mro.length()") int mroLength,
                    @Cached("create(mroLength)") ReadAttributeFromObjectNode[] readAttrNodes,
                    @Cached ToSulongNode toSulongNode,
                    @Cached PCallCapiFunction callCapiFunction,
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
        for (int i = 0; i < mroLength; i++) {
            Object kls = mro.getItemNormalized(i);
            Object value = readSlot(kls, readAttrNodes[i], toSulongNode, callCapiFunction, interopLibrary);
            if (value != null) {
                return value;
            }
        }
        return getNULL();
    }

    @Specialization(guards = {"mroLength == cachedMroLength", "cachedMroLength < 32"}, //
                    replaces = {"lookupConstantMROCached", "lookupConstantMRO"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected Object lookupCachedLen(@SuppressWarnings("unused") Object klass,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMroStorageNode,
                    @Bind("getMroStorageNode.execute(klass)") MroSequenceStorage mro,
                    @Bind("mro.length()") @SuppressWarnings("unused") int mroLength,
                    @Cached("mro.length()") int cachedMroLength,
                    @Cached("create(cachedMroLength)") ReadAttributeFromObjectNode[] readAttrNodes,
                    @Cached ToSulongNode toSulongNode,
                    @Cached PCallCapiFunction callCapiFunction,
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
        for (int i = 0; i < cachedMroLength; i++) {
            Object kls = mro.getItemNormalized(i);
            Object value = readSlot(kls, readAttrNodes[i], toSulongNode, callCapiFunction, interopLibrary);
            if (value != null) {
                return value;
            }
        }
        return getNULL();
    }

    @Specialization(replaces = {"lookupConstantMROCached", "lookupConstantMRO", "lookupCachedLen"})
    @Megamorphic
    protected Object lookupGeneric(Object klass,
                    @Cached GetMroStorageNode getMroStorageNode,
                    @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode,
                    @Cached ToSulongNode toSulongNode,
                    @Cached PCallCapiFunction callCapiFunction,
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
        MroSequenceStorage mro = getMroStorageNode.execute(klass);
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getItemNormalized(i);
            Object value = readSlot(kls, readAttrNode, toSulongNode, callCapiFunction, interopLibrary);
            if (value != null) {
                return value;
            }
        }
        return getNULL();
    }

    public MroShapeLookupResult lookupInMroShape(MroShape shape, Object klass) {
        assert MroShape.validate(klass, PythonLanguage.get(this));
        return shape.lookup(slot.methodName);
    }

    private Object getNULL() {
        return getContext().getNativeNull().getPtr();
    }

    private Object readSlot(Object currentType, ReadAttributeFromObjectNode readNode, ToSulongNode toSulongNode, PCallCapiFunction callCapiFunction,
                    InteropLibrary interopLibrary) {
        if (currentType instanceof PythonNativeClass) {
            Object value = callCapiFunction.call(slot.getGetter(), toSulongNode.execute(currentType));
            if (!interopLibrary.isNull(value)) {
                return value;
            }
        } else {
            Object value = readNode.execute(currentType, slot.methodName);
            if (value != PNone.NO_VALUE) {
                return wrapManagedMethod(value);
            }
        }
        return null;
    }

    private Object wrapManagedMethod(Object value) {
        if (value instanceof PNone) {
            return getNULL();
        }
        return getContext().getCApiContext().getOrCreateProcWrapper(slot, value);
    }

    @GenerateCached(false)
    private static final class Uncached extends LookupNativeSlotNode {
        Uncached(SlotMethodDef slot) {
            super(slot);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        @TruffleBoundary
        public Object execute(Object type) {
            if (type instanceof PythonBuiltinClassType pbct) {
                return lookupPBCTGeneric(pbct, ReadAttributeFromDynamicObjectNode.getUncached());
            }
            return lookupGeneric(type, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncachedForceType(), ToSulongNode.getUncached(), PCallCapiFunction.getUncached(),
                            InteropLibrary.getUncached());
        }
    }
}
