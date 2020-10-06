/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@ImportStatic(PythonOptions.class)
@ReportPolymorphism
public abstract class LookupAttributeInMRONode extends PNodeWithContext {
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
                        @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
            return findAttr(ctx.getCore(), klass, key, readAttrNode);
        }

        @Specialization(replaces = "lookupConstantMRO")
        protected static Object lookup(Object klass, Object key,
                        @Cached GetMroStorageNode getMroNode,
                        @Cached(value = "createForceType()", uncached = "getUncachedForceType()") ReadAttributeFromObjectNode readAttrNode) {
            return lookupSlow(klass, key, getMroNode, readAttrNode, false);
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
    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @Child private TypeNodes.IsSameTypeNode isSameTypeNode = IsSameTypeNodeGen.create();
    @Child private GetMroStorageNode getMroNode;

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

    /**
     * Looks up the {@code key} in the MRO of the {@code klass}.
     *
     * @return The lookup result, or {@link PNone#NO_VALUE} if the key isn't defined on any object
     *         in the MRO.
     */
    public abstract Object execute(Object klass);

    protected static Object findAttr(PythonCore core, PythonBuiltinClassType klass, Object key) {
        return findAttr(core, klass, key, ReadAttributeFromDynamicObjectNode.getUncached());
    }

    protected static Object findAttr(PythonCore core, PythonBuiltinClassType klass, Object key, ReadAttributeFromDynamicObjectNode readAttrNode) {
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
    protected static Object lookupPBCTCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getCore(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

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
                    @Cached("findAttr(getCore(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    @Specialization(replaces = "lookupPBCTCached")
    protected Object lookupPBCTGeneric(PythonBuiltinClassType klass,
                    @Cached ReadAttributeFromDynamicObjectNode readAttrNode) {
        return findAttr(getCore(), klass, key, readAttrNode);
    }

    static final class PythonClassAssumptionPair {
        public final Assumption assumption;
        public final Object value;

        PythonClassAssumptionPair(Assumption assumption, Object value) {
            this.assumption = assumption;
            this.value = value;
        }
    }

    protected PythonClassAssumptionPair findAttrClassAndAssumptionInMRO(Object klass) {
        CompilerAsserts.neverPartOfCompilation();
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
                return new PythonClassAssumptionPair(attrAssumption, value);
            }
        }
        return new PythonClassAssumptionPair(attrAssumption, PNone.NO_VALUE);
    }

    @Specialization(guards = {"isSameType(cachedKlass, klass)", "cachedClassInMROInfo != null"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    assumptions = {"cachedClassInMROInfo.assumption", "singleContextAssumption()"})
    protected static Object lookupConstantMROCached(@SuppressWarnings("unused") Object klass,
                    @Cached("klass") @SuppressWarnings("unused") Object cachedKlass,
                    @Cached("findAttrClassAndAssumptionInMRO(cachedKlass)") PythonClassAssumptionPair cachedClassInMROInfo) {
        return cachedClassInMROInfo.value;
    }

    protected static ReadAttributeFromObjectNode[] create(int size) {
        ReadAttributeFromObjectNode[] nodes = new ReadAttributeFromObjectNode[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = ReadAttributeFromObjectNode.createForceType();
        }
        return nodes;
    }

    @Specialization(guards = {"isSameType(cachedKlass, klass)", "mroLength < 32"}, //
                    limit = "getAttributeAccessInlineCacheMaxDepth()", //
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

    @Specialization(replaces = {"lookupConstantMROCached", "lookupConstantMRO"})
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

    public static Object lookupSlow(Object klass, Object key, GetMroStorageNode getMroNode, ReadAttributeFromObjectNode readAttrNode, boolean skipPythonClasses) {
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
        return isSameTypeNode.execute(cachedKlass, klass);
    }
}
