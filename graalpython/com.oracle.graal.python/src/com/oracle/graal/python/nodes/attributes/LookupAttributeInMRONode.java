/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@ImportStatic(PythonOptions.class)
public abstract class LookupAttributeInMRONode extends PNodeWithContext {
    public abstract static class Dynamic extends PNodeWithContext {
        public abstract Object execute(LazyPythonClass klass, Object key);

        public static LookupAttributeInMRONode.Dynamic create() {
            return LookupAttributeInMRONodeGen.DynamicCachedNodeGen.create();
        }

        public static LookupAttributeInMRONode.Dynamic getUncached() {
            return new DynamicUncached();
        }
    }

    static abstract class DynamicCached extends Dynamic {
        protected static boolean compareStrings(String key, String cachedKey) {
            return cachedKey.equals(key);
        }

        @Specialization(guards = "compareStrings(key, cachedKey)", limit = "2")
        @ExplodeLoop
        protected Object lookupConstantMRO(LazyPythonClass klass, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("create(key)") LookupAttributeInMRONode lookup) {
            return lookup.execute(klass);
        }

        @Specialization(replaces = "lookupConstantMRO")
        protected Object lookup(PythonBuiltinClassType klass, Object key) {
            return LookupAttributeInMRONode.findAttr(getCore(), klass, key);
        }

        @Specialization(replaces = "lookupConstantMRO")
        protected Object lookup(PythonClass klass, Object key,
                        @Cached("create()") ReadAttributeFromObjectNode readAttrNode) {
            return LookupAttributeInMRONode.lookupSlow(klass, key, readAttrNode);
        }
    }

    static class DynamicUncached extends Dynamic {
        private final ReadAttributeFromObjectNode readAttrNode = ReadAttributeFromObjectNode.getUncached();

        @Override
        public Object execute(LazyPythonClass klass, Object key) {
            if (klass instanceof PythonBuiltinClassType) {
                return LookupAttributeInMRONode.findAttr(getCore(), (PythonBuiltinClassType) klass, key);
            } else if (klass instanceof PythonClass) {
                return LookupAttributeInMRONode.lookupSlow((PythonClass) klass, key, readAttrNode);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException("not implemented: lookup inherited attribute from non-PythonClass");
            }
        }
    }

    protected final String key;

    public LookupAttributeInMRONode(String key) {
        this.key = key;
    }

    public static LookupAttributeInMRONode create(String key) {
        return LookupAttributeInMRONodeGen.create(key);
    }

    /**
     * Looks up the {@code key} in the MRO of the {@code klass}.
     *
     * @return The lookup result, or {@link PNone#NO_VALUE} if the key isn't defined on any object
     *         in the MRO.
     */
    public abstract Object execute(LazyPythonClass klass);

    @TruffleBoundary
    protected static Object findAttr(PythonCore core, PythonBuiltinClassType klass, Object key) {
        PythonBuiltinClassType current = klass;
        while (current != PythonBuiltinClassType.PythonObject) {
            Object value = core.lookupType(current).getAttribute(key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
            current = current.getBase();
        }
        return core.lookupType(current).getAttribute(key);
    }

    @Specialization(guards = {"klass == cachedKlass"}, limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)")
    protected Object lookupPBCTCached(@SuppressWarnings("unused") PythonBuiltinClassType klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonBuiltinClassType cachedKlass,
                    @Cached("findAttr(getCore(), cachedKlass, key)") Object cachedValue) {
        return cachedValue;
    }

    @Specialization(replaces = "lookupPBCTCached")
    protected Object lookupPBCTGeneric(PythonBuiltinClassType klass) {
        return findAttr(getCore(), klass, key);
    }

    final static class PythonClassAssumptionPair {
        public final Assumption assumption;
        public final Object value;

        PythonClassAssumptionPair(Assumption assumption, Object value) {
            this.assumption = assumption;
            this.value = value;
        }
    }

    protected PythonClassAssumptionPair findAttrClassAndAssumptionInMRO(PythonClass klass) {
        PythonClass[] mro = klass.getMethodResolutionOrder();
        Assumption attrAssumption = klass.createAttributeInMROFinalAssumption(key);
        for (int i = 0; i < mro.length; i++) {
            PythonClass cls = mro[i];
            if (i > 0) {
                assert cls != klass : "MRO chain is incorrect: '" + klass + "' was found at position " + i;
                cls.addAttributeInMROFinalAssumption(key, attrAssumption);
            }

            if (cls.getStorage().containsKey(key)) {
                Object value = cls.getStorage().get(key);
                if (value != PNone.NO_VALUE) {
                    return new PythonClassAssumptionPair(attrAssumption, value);
                }
            }
        }
        return new PythonClassAssumptionPair(attrAssumption, PNone.NO_VALUE);
    }

    @Specialization(guards = {"klass == cachedKlass"}, limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", assumptions = {"cachedClassInMROInfo.assumption"})
    protected Object lookupConstantMROCached(@SuppressWarnings("unused") PythonClass klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonClass cachedKlass,
                    @Cached("findAttrClassAndAssumptionInMRO(cachedKlass)") PythonClassAssumptionPair cachedClassInMROInfo) {
        return cachedClassInMROInfo.value;
    }

    protected ReadAttributeFromObjectNode[] create(int size) {
        ReadAttributeFromObjectNode[] nodes = new ReadAttributeFromObjectNode[size];
        for (int i = 0; i < size; i++) {
            nodes[i] = ReadAttributeFromObjectNode.create();
        }
        return nodes;
    }

    @Specialization(guards = {"klass == cachedKlass", "mroLength < 32"}, limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", assumptions = "lookupStable")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    protected Object lookupConstantMRO(@SuppressWarnings("unused") PythonClass klass,
                    @Cached("klass") @SuppressWarnings("unused") PythonClass cachedKlass,
                    @Cached("cachedKlass.getLookupStableAssumption()") @SuppressWarnings("unused") Assumption lookupStable,
                    @Cached(value = "cachedKlass.getMethodResolutionOrder()", dimensions = 1) PythonClass[] mro,
                    @Cached("mro.length") @SuppressWarnings("unused") int mroLength,
                    @Cached("create(mroLength)") ReadAttributeFromObjectNode[] readAttrNodes) {
        for (int i = 0; i < mro.length; i++) {
            PythonClass kls = mro[i];
            Object value = readAttrNodes[i].execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    @Specialization(replaces = {"lookupConstantMROCached", "lookupConstantMRO"})
    protected Object lookup(PythonClass klass,
                    @Cached("create()") ReadAttributeFromObjectNode readAttrNode) {
        return lookupSlow(klass, key, readAttrNode);
    }

    protected static Object lookupSlow(PythonClass klass, Object key, ReadAttributeFromObjectNode readAttrNode) {
        PythonClass[] mro = klass.getMethodResolutionOrder();
        for (int i = 0; i < mro.length; i++) {
            PythonClass kls = mro[i];
            Object value = readAttrNode.execute(kls, key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }

    public static Object lookupSlow(PythonClass klass, String key) {
        PythonClass[] mro = klass.getMethodResolutionOrder();
        for (int i = 0; i < mro.length; i++) {
            PythonClass kls = mro[i];
            Object value = kls.getAttribute(key);
            if (value != PNone.NO_VALUE) {
                return value;
            }
        }
        return PNone.NO_VALUE;
    }
}
