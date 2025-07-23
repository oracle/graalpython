/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.MroShape;
import com.oracle.graal.python.builtins.objects.type.MroShape.MroShapeLookupResult;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
public abstract class LookupAttributeInMRONode extends PNodeWithContext {

    public abstract Object execute(Object klass);

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 17
    public abstract static class Dynamic extends PNodeWithContext {
        public abstract Object execute(Object klass, TruffleString key);

        @Specialization(guards = "stringEquals(key, cachedKey, equalNode)", limit = "2")
        protected static Object lookupConstantMRO(Object klass, @SuppressWarnings("unused") TruffleString key,
                        @Cached("key") @SuppressWarnings("unused") TruffleString cachedKey,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode,
                        @Cached("create(key)") LookupAttributeInMRONode lookup) {
            return lookup.execute(klass);
        }

        @Specialization(replaces = "lookupConstantMRO")
        @InliningCutoff
        protected Object lookupInBuiltinType(PythonBuiltinClassType klass, TruffleString key,
                        @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
            return findAttr(PythonContext.get(this), klass, key, readAttrNode);
        }

        @Specialization(replaces = "lookupConstantMRO")
        @InliningCutoff
        protected static Object lookupGeneric(Object klass, TruffleString key,
                        @Bind Node inliningTarget,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached(value = "createForceType()", uncached = "getUncachedForceType()") ReadAttributeFromObjectNode readAttrNode) {
            return lookup(key, getMroNode.execute(inliningTarget, klass), readAttrNode, false, DynamicObjectLibrary.getUncached());
        }

        @NeverDefault
        public static LookupAttributeInMRONode.Dynamic create() {
            return LookupAttributeInMRONodeGen.DynamicNodeGen.create();
        }

        public static LookupAttributeInMRONode.Dynamic getUncached() {
            return LookupAttributeInMRONodeGen.DynamicNodeGen.getUncached();
        }
    }

    private final boolean skipNonStaticBases;
    protected final TruffleString key;
    @Child private TypeNodes.IsSameTypeNode isSameTypeNode;
    @Child private GetMroStorageNode getMroNode;

    public LookupAttributeInMRONode(TruffleString key, boolean skipNonStaticBases) {
        this.key = key;
        this.skipNonStaticBases = skipNonStaticBases;
    }

    @NeverDefault
    public static LookupAttributeInMRONode create(TruffleString key) {
        return LookupAttributeInMRONodeGen.create(key, false);
    }

    /**
     * Specific case to facilitate lookup on native and built-in classes only. This is useful for
     * certain slot wrappers.
     */
    @NeverDefault
    public static LookupAttributeInMRONode createForLookupOfUnmanagedClasses(TruffleString key) {
        return LookupAttributeInMRONodeGen.create(key, true);
    }

    protected static Object findAttr(Python3Core core, PythonBuiltinClassType klass, TruffleString key) {
        return findAttr(core, klass, key, ReadAttributeFromPythonObjectNode.getUncached());
    }

    public static Object findAttr(Python3Core core, PythonBuiltinClassType klass, TruffleString key, ReadAttributeFromPythonObjectNode readAttrNode) {
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

    @Specialization(guards = {"isSingleContext()", "klass == cachedKlass"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected static Object lookupPBCTCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getContext(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    @Idempotent
    protected static boolean canCache(Object value) {
        return value instanceof Long ||
                        value instanceof Integer ||
                        value instanceof Boolean ||
                        value instanceof Double ||
                        value instanceof PNone;
    }

    @Specialization(guards = {"klass == cachedKlass", "canCache(cachedValue)"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    protected static Object lookupPBCTCachedMulti(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getContext(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    public static PythonBuiltinClassType findOwnerInMro(Python3Core core, PythonBuiltinClassType klass, TruffleString key) {
        PythonBuiltinClassType current = klass;
        ReadAttributeFromPythonObjectNode readNode = ReadAttributeFromPythonObjectNode.getUncached();
        while (current != null) {
            if (readNode.execute(core.lookupType(current), key) != PNone.NO_VALUE) {
                return current;
            }
            current = current.getBase();
        }
        return null;
    }

    @Specialization(replaces = {"lookupPBCTCached", "lookupPBCTCachedMulti"}, guards = "klass == cachedKlass", //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    @InliningCutoff
    protected Object lookupPBCTCachedOwner(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findOwnerInMro(getContext(), cachedKlass, key)") PythonBuiltinClassType ownerKlass,
                    @Shared @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
        if (ownerKlass == null) {
            return PNone.NO_VALUE;
        } else {
            return readAttrNode.execute(PythonContext.get(this).lookupType(ownerKlass), key);
        }
    }

    @Specialization(replaces = "lookupPBCTCachedOwner")
    @InliningCutoff
    protected Object lookupPBCTGeneric(PythonBuiltinClassType klass,
                    @Shared @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
        return findAttr(PythonContext.get(this), klass, key, readAttrNode);
    }

    // PythonClass specializations:

    record AttributeAssumptionPair(Assumption assumption, Object value, boolean invalidate) {
    }

    @SuppressWarnings("serial")
    static final class InvalidateLookupException extends ControlFlowException {
        private static final InvalidateLookupException INSTANCE = new InvalidateLookupException();
    }

    private static boolean skipNonStaticBase(Object clsObj, boolean skipNonStaticBases, DynamicObjectLibrary dylib) {
        return skipNonStaticBases && clsObj instanceof PythonClass && !((PythonClass) clsObj).isStaticBase(dylib);
    }

    protected AttributeAssumptionPair findAttrAndAssumptionInMRO(Object klass) {
        CompilerAsserts.neverPartOfCompilation();
        DynamicObjectLibrary dylib = DynamicObjectLibrary.getUncached();
        // Regarding potential side effects to MRO caused by __eq__ of the keys in the dicts that we
        // search through: CPython seems to read the MRO once and then compute the result also
        // ignoring the side effects. Moreover, CPython has lookup cache, so the side effects
        // may or may not be visible during subsequent lookups. We want to avoid triggering the side
        // effects twice, so we succeed this lookup no matter what, however, we will invalidate on
        // the next lookup if there were some MRO side effects.
        MroSequenceStorage mro = getMro(klass);
        Assumption attrAssumption = mro.createAttributeInMROFinalAssumption(key);
        Object result = PNone.NO_VALUE;
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass clsObj = mro.getPythonClassItemNormalized(i);
            if (i > 0) {
                assert clsObj != klass : "MRO chain is incorrect: '" + klass + "' was found at position " + i;
                getMro(clsObj).addAttributeInMROFinalAssumption(key, attrAssumption);
            }
            if (skipNonStaticBase(clsObj, skipNonStaticBases, dylib)) {
                continue;
            }
            Object value = ReadAttributeFromObjectNode.getUncachedForceType().execute(clsObj, key);
            if (value != PNone.NO_VALUE) {
                result = value;
                break;
            }
        }
        if (!attrAssumption.isValid()) {
            return new AttributeAssumptionPair(Assumption.ALWAYS_VALID, result, true);
        } else {
            return new AttributeAssumptionPair(attrAssumption, result, false);
        }
    }

    @Specialization(guards = {"isSingleContext()", "isSameType(cachedKlass, klass)", "cachedAttrInMROInfo != null"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    assumptions = "cachedAttrInMROInfo.assumption()", //
                    rewriteOn = InvalidateLookupException.class)
    protected static Object lookupConstantMROCached(@SuppressWarnings("unused") Object klass,
                    @Bind Node inliningTarget,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @Cached InlinedBranchProfile shouldInvalidate,
                    @Cached("findAttrAndAssumptionInMRO(cachedKlass)") AttributeAssumptionPair cachedAttrInMROInfo) {
        if (shouldInvalidate.wasEntered(inliningTarget)) {
            throw InvalidateLookupException.INSTANCE;
        } else if (cachedAttrInMROInfo.invalidate) {
            // next time we will invalidate, but this time we must return the result to avoid
            // triggering side effects in __eq__ multiple times
            shouldInvalidate.enter(inliningTarget);
        }
        return cachedAttrInMROInfo.value;
    }

    public MroShapeLookupResult lookupInMroShape(MroShape shape, Object klass) {
        assert MroShape.validate(klass, PythonLanguage.get(this));
        return shape.lookup(key);
    }

    // This specialization works well only for multi-context mode
    // Note: MroShape creation and updates are disabled in multi-context mode, see
    // PythonClass#initializeMroShape
    @Specialization(guards = {"!isSingleContext()", "cachedMroShape != null", "klass.getMroShape() == cachedMroShape"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    @InliningCutoff
    protected Object lookupConstantMROShape(PythonClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getMroShape()") MroShape cachedMroShape,
                    @Cached("lookupInMroShape(cachedMroShape, klass)") MroShapeLookupResult lookupResult) {
        return lookupResult.getFromMro(getMro(klass), key);
    }

    @NeverDefault
    protected static ReadAttributeFromObjectNode[] create(int size) {
        ReadAttributeFromObjectNode[] nodes = new ReadAttributeFromObjectNode[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = ReadAttributeFromObjectNode.createForceType();
        }
        return nodes;
    }

    @Specialization(guards = {"isSingleContext()", "isSameType(cachedKlass, klass)", "mroLength < 32"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    replaces = "lookupConstantMROShape", //
                    assumptions = "lookupStable")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    @InliningCutoff
    protected Object lookupConstantMRO(@SuppressWarnings("unused") Object klass,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @Cached("getMro(cachedKlass)") MroSequenceStorage mro,
                    @Cached("mro.getLookupStableAssumption()") @SuppressWarnings("unused") Assumption lookupStable,
                    @Cached("mro.length()") int mroLength,
                    @Exclusive @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                    @Cached("create(mroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
        for (int i = 0; i < mroLength; i++) {
            Object kls = mro.getPythonClassItemNormalized(i);
            if (skipNonStaticBase(kls, skipNonStaticBases, dylib)) {
                continue;
            }
            Object value = readAttrNodes[i].execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(guards = {"mroLength == cachedMroLength", "cachedMroLength < 32"}, //
                    replaces = {"lookupConstantMROCached", "lookupConstantMRO"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    @InliningCutoff
    protected Object lookupCachedLen(@SuppressWarnings("unused") Object klass,
                    @Bind("getMro(klass)") MroSequenceStorage mro,
                    @Bind("mro.length()") @SuppressWarnings("unused") int mroLength,
                    @Cached("mro.length()") int cachedMroLength,
                    @Exclusive @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                    @Cached("create(cachedMroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
        for (int i = 0; i < cachedMroLength; i++) {
            Object kls = mro.getPythonClassItemNormalized(i);
            if (skipNonStaticBase(kls, skipNonStaticBases, dylib)) {
                continue;
            }
            Object value = readAttrNodes[i].execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(replaces = {"lookupConstantMROCached", "lookupConstantMRO", "lookupCachedLen"})
    @Megamorphic
    @InliningCutoff
    protected Object lookupGeneric(Object klass,
                    @Exclusive @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                    @Cached("createForceType()") ReadAttributeFromObjectNode readAttrNode) {
        return lookup(key, getMro(klass), readAttrNode, skipNonStaticBases, dylib);
    }

    protected GetMroStorageNode ensureGetMroNode() {
        if (getMroNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMroNode = insert(GetMroStorageNode.create());
        }
        return getMroNode;
    }

    protected MroSequenceStorage getMro(Object clazz) {
        return ensureGetMroNode().executeCached(clazz);
    }

    @TruffleBoundary
    public static Object lookupSlowPath(Object klass, TruffleString key) {
        return lookup(key, GetMroStorageNode.executeUncached(klass), ReadAttributeFromObjectNode.getUncachedForceType(), false, DynamicObjectLibrary.getUncached());
    }

    public static Object lookup(TruffleString key, MroSequenceStorage mro, ReadAttributeFromObjectNode readAttrNode, boolean skipNonStaticBases, DynamicObjectLibrary dylib) {
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getPythonClassItemNormalized(i);
            if (skipNonStaticBase(kls, skipNonStaticBases, dylib)) {
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
        return isSameTypeNode.executeCached(cachedKlass, klass);
    }
}
