/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Unlike {@link LookupCallableSlotInMRONode} this node does not use the slots on
 * {@link PythonBuiltinClassType}.
 */
@ImportStatic(PythonOptions.class)
public abstract class LookupAttributeInMRONode extends LookupInMROBaseNode {
    /**
     * If possible caches the key and delegates to {@link LookupAttributeInMRONode}.
     */
    @GenerateUncached
    public abstract static class Dynamic extends PNodeWithContext {
        public abstract Object execute(Object klass, Object key);

        protected static boolean compareStrings(String key, String cachedKey) {
            return cachedKey.equals(key);
        }

        @Specialization(guards = "compareStrings(key, cachedKey)", limit = "2")
        protected static Object lookupConstantMRO(Object klass, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("create(key)") LookupAttributeInMRONode lookup) {
            return lookup.execute(klass);
        }

        @Specialization(replaces = "lookupConstantMRO")
        protected Object lookup(PythonBuiltinClassType klass, Object key,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @Cached CastToJavaStringNode castKeyNode,
                        @Cached BranchProfile canBeSlotProfile,
                        @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
            return findAttrUseSlots(ctx.getCore(), klass, key, castKeyNode, canBeSlotProfile, readAttrNode);
        }

        @Specialization(replaces = "lookupConstantMRO")
        protected static Object lookup(Object klass, Object key,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached BranchProfile canBeSlotProfile,
                        @Cached CastToJavaStringNode castKeyNode,
                        @Cached(value = "createForceType()", uncached = "getUncachedForceType()") ReadAttributeFromObjectNode readAttrNode) {
            return lookupSlowUseSlots(klass, key, getMroNode, castKeyNode, canBeSlotProfile, readAttrNode);
        }

        public static LookupAttributeInMRONode.Dynamic create() {
            return LookupAttributeInMRONodeGen.DynamicNodeGen.create();
        }

        public static LookupAttributeInMRONode.Dynamic getUncached() {
            return LookupAttributeInMRONodeGen.DynamicNodeGen.getUncached();
        }
    }

    private final boolean skipPythonClasses;
    protected final String key;
    protected final SpecialMethodSlot slot;
    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @Child private TypeNodes.IsSameTypeNode isSameTypeNode;
    @Child private GetMroStorageNode getMroNode;

    @Override
    protected PythonCore getCore() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef.get().getCore();
    }

    public LookupAttributeInMRONode(String key, boolean skipPythonClasses) {
        this.key = key;
        this.skipPythonClasses = skipPythonClasses;
        this.slot = SpecialMethodSlot.findSpecialSlot(key);
    }

    public static LookupAttributeInMRONode create(String key) {
        return LookupAttributeInMRONodeGen.create(key, false);
    }

    /**
     * Specific case to facilitate lookup on native and built-in classes only. This is useful for
     * certain slot wrappers.
     */
    public static LookupAttributeInMRONode createForLookupOfUnmanagedClasses(String key) {
        return LookupAttributeInMRONodeGen.create(key, true);
    }

    // --------------
    // Helper methods and Specializations that handle PythonBuiltinClassTypes (abbreviated as PBCT):

    protected static Object findAttrUseSlots(PythonCore core, PythonBuiltinClassType klass, Object key) {
        return findAttrUseSlots(core, klass, key, CastToJavaStringNode.getUncached(), BranchProfile.getUncached(), ReadAttributeFromDynamicObjectNode.getUncached());
    }

    protected static Object findAttrUseSlots(PythonCore core, PythonBuiltinClassType klass, Object key, CastToJavaStringNode keyCastNode, BranchProfile canBeSlot,
                    ReadAttributeFromDynamicObjectNode readAttrNode) {
        SpecialMethodSlot slot = null;
        try {
            String keyStr = keyCastNode.execute(key);
            if (SpecialMethodSlot.canBeSpecial(keyStr)) {
                canBeSlot.enter();
                slot = SpecialMethodSlot.findSpecialSlot(keyStr);
            }
        } catch (CannotCastException ignore) {
        }
        return findAttrUseSlots(core, klass, key, slot, readAttrNode);
    }

    protected static Object findAttrUseSlots(PythonCore core, PythonBuiltinClassType klass, Object key, SpecialMethodSlot slot, ReadAttributeFromDynamicObjectNode readAttrNode) {
        if (slot != null) {
            return slot.getValue(core.lookupType(klass));
        }
        return findAttr(core, klass, key, readAttrNode);
    }

    public static Object findAttr(PythonCore core, PythonBuiltinClassType klass, Object key, ReadAttributeFromDynamicObjectNode readAttrNode) {
        PythonBuiltinClassType current = klass;
        Object value = PNone.NO_VALUE;
        while (current != null) {
            value = readAttrNode.execute(core.lookupType(current), key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
            current = current.getBase();
        }
        return value;
    }

    @Specialization(guards = {"klass == cachedKlass"}, limit = "getAttributeAccessInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    protected Object lookupPBCTCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttrUseSlots(getCore(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    protected static boolean canCache(Object value) {
        return PythonLanguage.canCache(value);
    }

    @Specialization(guards = {"klass == cachedKlass", "canCache(cachedValue)"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected Object lookupPBCTCachedMulti(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttrUseSlots(getCore(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    @Specialization(replaces = "lookupPBCTCached", guards = "slot != null")
    protected Object lookupPBCTWithSlotGeneric(PythonBuiltinClassType klass) {
        return slot.getValue(getCore().lookupType(klass));
    }

    @Specialization(replaces = "lookupPBCTCached", guards = "slot == null")
    protected Object lookupPBCTNoSlotGeneric(PythonBuiltinClassType klass,
                    @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
        return findAttr(getCore(), klass, key, readAttrNode);
    }

    // --------------
    // Following helper methods and Specializations handle other class types than
    // PythonBuiltinClassTypes (PBCT). NOTE: this includes PythonBuiltinClass.

    protected static boolean isPBCT(Object klass) {
        return klass instanceof PythonBuiltinClassType;
    }

    protected boolean isSlotLookup(Object klass) {
        return slot != null && klass instanceof PythonManagedClass;
    }

    static final class AttributeAssumptionPair {
        public final Assumption assumption;
        public final Object value;

        AttributeAssumptionPair(Assumption assumption, Object value) {
            this.assumption = assumption;
            this.value = value;
        }
    }

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
            dict = PythonObjectLibrary.getUncached().getDict(klass);
        }
        if (dict != null && HashingStorageLibrary.getUncached().hasSideEffect(GetDictStorageNode.getUncached().execute(dict))) {
            return null;
        }
        MroSequenceStorage mro = getMro(klass);
        Assumption attrAssumption = mro.createAttributeInMROFinalAssumption(key);
        for (int i = 0; i < mro.length(); i++) {
            Object clsObj = mro.getItemNormalized(i);
            if (i > 0) {
                assert clsObj != klass : "MRO chain is incorrect: '" + klass + "' was found at position " + i;
                getMro(clsObj).addAttributeInMROFinalAssumption(key, attrAssumption);
            }
            if (skipPythonClasses && clsObj instanceof PythonClass) {
                continue;
            }
            Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(clsObj, key);
            if (value != PNone.NO_VALUE) {
                return new AttributeAssumptionPair(attrAssumption, value);
            }
        }
        return new AttributeAssumptionPair(attrAssumption, PNone.NO_VALUE);
    }

    // Single context version that caches the result: used for both slots and non-slots, in this
    // case we always lookup in MRO and ignore cached values in the slots
    @Specialization(guards = {"!isPBCT(klass)", "isSameType(cachedKlass, klass)", "cachedAttrInMROInfo != null"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    assumptions = {"cachedAttrInMROInfo.assumption", "singleContextAssumption()"})
    protected Object lookupConstantMROCached(@SuppressWarnings("unused") Object klass,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @Cached("findAttrAndAssumptionInMRO(cachedKlass)") AttributeAssumptionPair cachedAttrInMROInfo) {
        return cachedAttrInMROInfo.value;
    }

    @Specialization(guards = "slot != null", replaces = "lookupConstantMROCached")
    protected Object lookupSlot(@SuppressWarnings("unused") PythonManagedClass klass) {
        assert isSlotLookup(klass);
        return slot.getValue(klass);
    }

    protected static ReadAttributeFromObjectNode[] create(int size) {
        ReadAttributeFromObjectNode[] nodes = new ReadAttributeFromObjectNode[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = ReadAttributeFromObjectNode.createForceType();
        }
        return nodes;
    }

    @Specialization(guards = {"!isPBCT(klass)", "!isSlotLookup(klass)", "isSameType(cachedKlass, klass)", "mroLength < 32"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    replaces = "lookupConstantMROCached", //
                    assumptions = {"lookupStable", "singleContextAssumption()"})
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected Object lookupConstantMRO(@SuppressWarnings("unused") Object klass,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @Cached("getMro(cachedKlass)") MroSequenceStorage mro,
                    @Cached("mro.getLookupStableAssumption()") @SuppressWarnings("unused") Assumption lookupStable,
                    @Cached("mro.length()") int mroLength,
                    @Cached("create(mroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
        for (int i = 0; i < mroLength; i++) {
            Object kls = mro.getItemNormalized(i);
            if (skipPythonClasses && kls instanceof PythonClass) {
                continue;
            }
            Object value = readAttrNodes[i].execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"!isPBCT(klass)", "!isSlotLookup(klass)", "mroLength == cachedMroLength", "cachedMroLength < 32"}, //
                    replaces = {"lookupConstantMROCached", "lookupConstantMRO"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected Object lookupCachedLen(@SuppressWarnings("unused") Object klass,
                    @Bind("getMro(klass)") MroSequenceStorage mro,
                    @Bind("mro.length()") @SuppressWarnings("unused") int mroLength,
                    @Cached("mro.length()") int cachedMroLength,
                    @Cached("create(cachedMroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
        for (int i = 0; i < cachedMroLength; i++) {
            Object kls = mro.getItemNormalized(i);
            if (skipPythonClasses && kls instanceof PythonClass) {
                continue;
            }
            Object value = readAttrNodes[i].execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"!isPBCT(klass)", "!isSlotLookup(klass)"}, //
                    replaces = {"lookupConstantMROCached", "lookupConstantMRO", "lookupCachedLen"})
    @Megamorphic
    protected Object lookup(Object klass,
                    @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode) {
        return lookupSlow(klass, key, ensureGetMroNode(), readAttrNode, skipPythonClasses);
    }

    protected GetMroStorageNode ensureGetMroNode() {
        if (getMroNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMroNode = insert(GetMroStorageNode.create());
        }
        return getMroNode;
    }

    protected MroSequenceStorage getMro(Object clazz) {
        return ensureGetMroNode().execute(clazz);
    }

    public static Object lookupSlowUseSlots(Object klass, Object key, GetMroStorageNode getMroNode, CastToJavaStringNode keyCastNode, BranchProfile canBeSlot,
                    ReadAttributeFromObjectNode readAttrNode) {
        try {
            String keyStr = keyCastNode.execute(key);
            if (SpecialMethodSlot.canBeSpecial(keyStr) && klass instanceof PythonManagedClass) {
                canBeSlot.enter();
                SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlot(keyStr);
                if (slot != null) {
                    return slot.getValue((PythonManagedClass) klass);
                }
            }
        } catch (CannotCastException ignore) {
        }
        return lookupSlow(klass, key, getMroNode, readAttrNode, false);
    }

    @TruffleBoundary
    public static Object lookupSlow(Object klass, Object key) {
        return lookupSlow(klass, key, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), false);
    }

    @TruffleBoundary
    public static Object lookupSlowSkipPythonClasses(Object klass, Object key) {
        return lookupSlow(klass, key, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true);
    }

    private static Object lookupSlow(Object klass, Object key, GetMroStorageNode getMroNode, ReadAttributeFromObjectNode readAttrNode, boolean skipPythonClasses) {
        MroSequenceStorage mro = getMroNode.execute(klass);
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getItemNormalized(i);
            if (skipPythonClasses && kls instanceof PythonClass) {
                continue;
            }
            Object value = readAttrNode.execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    protected boolean isSameType(Object cachedKlass, Object klass) {
        if (isSameTypeNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            isSameTypeNode = insert(IsSameTypeNodeGen.create());
        }
        return isSameTypeNode.execute(cachedKlass, klass);
    }
}
