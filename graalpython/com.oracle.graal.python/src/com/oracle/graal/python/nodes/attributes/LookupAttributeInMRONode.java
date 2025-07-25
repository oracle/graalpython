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
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.MroShape;
import com.oracle.graal.python.builtins.objects.type.MroShape.MroShapeLookupResult;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(PythonOptions.class)
public abstract class LookupAttributeInMRONode extends PNodeWithContext {

    public abstract Object execute(Object klass);

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 36 -> 17
    public abstract static class Dynamic extends PNodeWithContext {
        public abstract Object execute(Object klass, TruffleString key);

        @Specialization(guards = "equalNode.execute(inliningTarget, key, cachedKey)", limit = "2")
        static Object lookupConstantMROEquals(Object klass, TruffleString key,
                        @Bind Node inliningTarget,
                        @Cached("key") TruffleString cachedKey,
                        @Cached @Shared StringUtils.EqualNode equalNode,
                        @Cached("create(cachedKey)") LookupAttributeInMRONode lookup) {
            return lookup.execute(klass);
        }

        // Merged PythonBuiltinClassType and generic cases to have single @InliningCutoff
        @InliningCutoff
        @Specialization(replaces = "lookupConstantMROEquals")
        static Object lookupGeneric(Object klass, TruffleString key,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile pbctProfile,
                        @Cached ReadAttributeFromPythonObjectNode readPBCTAttrNode,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            if (pbctProfile.profile(inliningTarget, klass instanceof PythonBuiltinClassType)) {
                return findAttr(PythonContext.get(inliningTarget), (PythonBuiltinClassType) klass, key, readPBCTAttrNode);
            } else {
                return lookup(key, getMroNode.execute(inliningTarget, klass), readAttrNode, false);
            }
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
    final TruffleString key;

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

    static Object findAttr(Python3Core core, PythonBuiltinClassType klass, TruffleString key) {
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

    @Idempotent
    static boolean canCache(Object value) {
        return value instanceof Long ||
                        value instanceof Integer ||
                        value instanceof Boolean ||
                        value instanceof Double ||
                        value instanceof PNone;
    }

    @Specialization(guards = {"klass == cachedKlass", "isSingleContext() || canCache(cachedValue)"}, limit = "getAttributeAccessInlineCacheMaxDepth()")
    static Object lookupPBCTCached(PythonBuiltinClassType klass,
                    @Cached("klass") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getContext(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    // PythonClass specializations:

    record AttributeAssumptionPair(Assumption assumption, Object value, boolean invalidate) {
    }

    @SuppressWarnings("serial")
    static final class InvalidateLookupException extends ControlFlowException {
        private static final InvalidateLookupException INSTANCE = new InvalidateLookupException();
    }

    private static boolean skipNonStaticBase(Object clsObj, boolean skipNonStaticBases) {
        return skipNonStaticBases && clsObj instanceof PythonClass && !((PythonClass) clsObj).isStaticBase();
    }

    AttributeAssumptionPair findAttrAndAssumptionInMRO(Object klass) {
        CompilerAsserts.neverPartOfCompilation();
        // Regarding potential side effects to MRO caused by __eq__ of the keys in the dicts that we
        // search through: CPython seems to read the MRO once and then compute the result also
        // ignoring the side effects. Moreover, CPython has lookup cache, so the side effects
        // may or may not be visible during subsequent lookups. We want to avoid triggering the side
        // effects twice, so we succeed this lookup no matter what, however, we will invalidate on
        // the next lookup if there were some MRO side effects.
        MroSequenceStorage mro = GetMroStorageNode.executeUncached(klass);
        Assumption attrAssumption = mro.createAttributeInMROFinalAssumption(key);
        Object result = PNone.NO_VALUE;
        for (int i = 0; i < mro.length(); i++) {
            PythonAbstractClass clsObj = mro.getPythonClassItemNormalized(i);
            if (i > 0) {
                assert clsObj != klass : "MRO chain is incorrect: '" + klass + "' was found at position " + i;
                GetMroStorageNode.executeUncached(clsObj).addAttributeInMROFinalAssumption(key, attrAssumption);
            }
            if (skipNonStaticBase(clsObj, skipNonStaticBases)) {
                continue;
            }
            Object value = ReadAttributeFromObjectNode.getUncached().execute(clsObj, key);
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

    @Specialization(guards = {"isSingleContext()", "isSameTypeNode.execute(inliningTarget, cachedKlass, klass)", "cachedAttrInMROInfo != null"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    assumptions = "cachedAttrInMROInfo.assumption()", //
                    rewriteOn = InvalidateLookupException.class)
    static Object lookupConstantMROCached(Object klass,
                    @Bind Node inliningTarget,
                    @Cached("klass") Object cachedKlass,
                    @Cached IsSameTypeNode isSameTypeNode,
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

    // The slow-path specializations are extracted in a separate node to have a single cutoff from
    // the fast path
    @InliningCutoff
    @Specialization(replaces = {"lookupPBCTCached", "lookupConstantMROCached"})
    Object lookupSlowPath(Object klass,
                    @Cached SlowPath slowPathNode) {
        return slowPathNode.execute(klass, key, skipNonStaticBases);
    }

    public abstract static class SlowPath extends PNodeWithContext {

        @Child private GetMroStorageNode getMroNode;

        public abstract Object execute(Object klass, TruffleString key, boolean skipNonStaticBases);

        static PythonBuiltinClassType findOwnerInMro(Python3Core core, PythonBuiltinClassType klass, TruffleString key) {
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

        // used to be replaces = lookupPBCTCached
        @Specialization(guards = "klass == cachedKlass", limit = "getAttributeAccessInlineCacheMaxDepth()")
        Object lookupPBCTCachedOwner(PythonBuiltinClassType klass, TruffleString key, boolean skipNonStaticBases,
                        @Cached("klass") PythonBuiltinClassType cachedKlass,
                        @Cached("findOwnerInMro(getContext(), cachedKlass, key)") PythonBuiltinClassType ownerKlass,
                        @Shared @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
            if (ownerKlass == null) {
                return PNone.NO_VALUE;
            } else {
                return readAttrNode.execute(PythonContext.get(this).lookupType(ownerKlass), key);
            }
        }

        @Specialization(replaces = "lookupPBCTCachedOwner")
        Object lookupPBCTGeneric(PythonBuiltinClassType klass, TruffleString key, boolean skipNonStaticBases,
                        @Shared @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
            return findAttr(PythonContext.get(this), klass, key, readAttrNode);
        }

        // This specialization works well only for multi-context mode
        // Note: MroShape creation and updates are disabled in multi-context mode, see
        // PythonClass#initializeMroShape
        @Specialization(guards = {"!isSingleContext()", "cachedMroShape != null", "klass.getMroShape() == cachedMroShape"}, //
                        limit = "getAttributeAccessInlineCacheMaxDepth()")
        Object lookupConstantMROShape(PythonClass klass, TruffleString key, boolean skipNonStaticBases,
                        @Cached("klass.getMroShape()") MroShape cachedMroShape,
                        @Cached("lookupInMroShape(cachedMroShape, klass, key)") MroShapeLookupResult lookupResult) {
            return lookupResult.getFromMro(getMro(klass), key);
        }

        @NeverDefault
        static ReadAttributeFromObjectNode[] create(int size) {
            ReadAttributeFromObjectNode[] nodes = new ReadAttributeFromObjectNode[size];
            for (int i = 0; i < size; i++) {
                nodes[i] = ReadAttributeFromObjectNode.create();
            }
            return nodes;
        }

        @Specialization(guards = {"isSingleContext()", "isSameTypeNode.execute(inliningTarget, cachedKlass, klass)", "mroLength < 32"}, //
                        limit = "getAttributeAccessInlineCacheMaxDepth()", //
                        replaces = "lookupConstantMROShape", //
                        assumptions = "lookupStable")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        static Object lookupConstantMRO(Object klass, TruffleString key, boolean skipNonStaticBases,
                        @Bind Node inliningTarget,
                        @Cached("klass") Object cachedKlass,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached("getMroUncached(cachedKlass)") MroSequenceStorage mro,
                        @Cached("mro.getLookupStableAssumption()") Assumption lookupStable,
                        @Cached("mro.length()") int mroLength,
                        @Cached("create(mroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
            for (int i = 0; i < mroLength; i++) {
                Object kls = mro.getPythonClassItemNormalized(i);
                if (skipNonStaticBase(kls, skipNonStaticBases)) {
                    continue;
                }
                Object value = readAttrNodes[i].execute(kls, key);
                if (value != PNone.NO_VALUE) {
                    return value;
                }
            }
            return PNone.NO_VALUE;
        }

        // used to be replaces = lookupConstantMROCached
        @Specialization(guards = {"mroLength == cachedMroLength", "cachedMroLength < 32"}, //
                        replaces = "lookupConstantMRO", //
                        limit = "getAttributeAccessInlineCacheMaxDepth()")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        Object lookupCachedLen(Object klass, TruffleString key, boolean skipNonStaticBases,
                        @Bind("getMro(klass)") MroSequenceStorage mro,
                        @Bind("mro.length()") int mroLength,
                        @Cached("mro.length()") int cachedMroLength,
                        @Cached("create(cachedMroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
            for (int i = 0; i < cachedMroLength; i++) {
                Object kls = mro.getPythonClassItemNormalized(i);
                if (skipNonStaticBase(kls, skipNonStaticBases)) {
                    continue;
                }
                Object value = readAttrNodes[i].execute(kls, key);
                if (value != PNone.NO_VALUE) {
                    return value;
                }
            }
            return PNone.NO_VALUE;
        }

        // used to be replaces = lookupConstantMROCached
        @Specialization(replaces = {"lookupConstantMRO", "lookupCachedLen"})
        @Megamorphic
        @InliningCutoff
        Object lookupGeneric(Object klass, TruffleString key, boolean skipNonStaticBases,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            return lookup(key, getMro(klass), readAttrNode, skipNonStaticBases);
        }

        public MroShapeLookupResult lookupInMroShape(MroShape shape, Object klass, TruffleString key) {
            assert MroShape.validate(klass, PythonLanguage.get(this));
            return shape.lookup(key);
        }

        GetMroStorageNode ensureGetMroNode() {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroStorageNode.create());
            }
            return getMroNode;
        }

        MroSequenceStorage getMro(Object clazz) {
            return ensureGetMroNode().executeCached(clazz);
        }

        static MroSequenceStorage getMroUncached(Object clazz) {
            return GetMroStorageNode.executeUncached(clazz);
        }

    }

    @TruffleBoundary
    public static Object lookupSlowPath(Object klass, TruffleString key) {
        return lookup(key, GetMroStorageNode.executeUncached(klass), ReadAttributeFromObjectNode.getUncached(), false);
    }

    public static Object lookup(TruffleString key, MroSequenceStorage mro, ReadAttributeFromObjectNode readTypeAttrNode, boolean skipNonStaticBases) {
        for (int i = 0; i < mro.length(); i++) {
            Object kls = mro.getPythonClassItemNormalized(i);
            if (skipNonStaticBase(kls, skipNonStaticBases)) {
                continue;
            }
            Object value = readTypeAttrNode.execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }
}
