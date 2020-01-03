/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@ImportStatic({PythonOptions.class, PGuards.class})
public abstract class IsSubtypeNode extends PNodeWithContext {
    public abstract boolean execute(VirtualFrame frame, Object derived, Object cls);

    protected static boolean isSameType(IsSameTypeNode isSameTypeNode, LazyPythonClass cls, LazyPythonClass cachedCls) {
        return isSameTypeNode.execute(cls, cachedCls);
    }

    protected boolean isSubMro(LazyPythonClass base, MroSequenceStorage derivedMro, int baseMroLen, IsSameTypeNode isSameTypeNode) {
        CompilerAsserts.partialEvaluationConstant(baseMroLen);
        PythonAbstractClass[] derivedMroAry = derivedMro.getInternalClassArray();
        int derivedMroLen = derivedMroAry.length;
        int offset = derivedMroLen - baseMroLen;
        if (offset >= 0) {
            // we can only do this for classes where all MRO entries have only a
            // single base
            assert GetBaseClassesNodeGen.getUncached().execute(derivedMroAry[offset]).length == 1;
            return isSameType(isSameTypeNode, derivedMroAry[offset], base);
        } else {
            return false;
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected boolean isInMro(LazyPythonClass cls, MroSequenceStorage mro, int sz, IsSameTypeNode isSameTypeNode) {
        PythonAbstractClass[] mroAry = mro.getInternalClassArray();
        for (int i = 0; i < sz; i++) {
            if (isSameType(isSameTypeNode, mroAry[i], cls)) {
                return true;
            }
        }
        return false;
    }

    protected PythonBuiltinClassType getType(LazyPythonClass cls, ConditionProfile builtinType, ConditionProfile builtinClass) {
        if (builtinType.profile(cls instanceof PythonBuiltinClassType)) {
            return (PythonBuiltinClassType) cls;
        } else if (builtinClass.profile(cls instanceof PythonBuiltinClass)) {
            return ((PythonBuiltinClass) cls).getType();
        } else {
            return null;
        }
    }

    @Specialization(guards = {
                    "cachedDerived != null",
                    "cachedCls != null",
                    "getType(derived, builtinTypeProfile, builtinClassProfile) == cachedDerived",
                    "getType(cls, builtinTypeProfile, builtinClassProfile) == cachedCls"
    }, limit = "getVariableArgumentInlineCacheLimit()")
    @SuppressWarnings("unused")
    // n.b.: in multi-context, we only cache PythonBuiltinClassType, so no need
    // for assumptions. we also use a larger limit here, because these generate
    // very little code
    boolean isSubtypeOfCachedMultiContext(LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("createBinaryProfile()") ConditionProfile builtinTypeProfile,
                    @Cached("createBinaryProfile()") ConditionProfile builtinClassProfile,
                    @Cached("getType(derived, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedDerived,
                    @Cached("getType(cls, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedCls,
                    @SuppressWarnings("unused") @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("isInMro(cachedCls, getMro.execute(cachedDerived), getMro.execute(cachedDerived).getInternalClassArray().length, isSameTypeNode)") boolean isInMro) {
        return isInMro;
    }

    @Specialization(guards = {
                    "cachedCls != null",
                    "getType(cls, builtinTypeProfile, builtinClassProfile) == cachedCls",
                    "isKindOfBuiltinClass(derived)" // see assertion in isSubMro
    }, replaces = "isSubtypeOfCachedMultiContext", limit = "getVariableArgumentInlineCacheLimit()")
    boolean isVariableSubtypeOfConstantTypeCachedMultiContext(LazyPythonClass derived, @SuppressWarnings("unused") LazyPythonClass cls,
                    @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile builtinTypeProfile,
                    @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile builtinClassProfile,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("getType(cls, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedCls,
                    @Cached("getMro.execute(cachedCls).getInternalClassArray().length") int baseMroLen) {
        return isSubMro(cachedCls, getMro.execute(derived), baseMroLen, isSameTypeNode);
    }

    @Specialization(guards = {
                    "isSameType(isSameDerivedNode, derived, cachedDerived)",
                    "isSameType(isSameClsNode, cls, cachedCls)",
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
    }, assumptions = {
                    "mro.getLookupStableAssumption()",
                    "singleContextAssumption()"
    })
    @SuppressWarnings("unused")
    boolean isSubtypeOfCached(LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("derived") LazyPythonClass cachedDerived,
                    @Cached("cls") LazyPythonClass cachedCls,
                    @Cached IsSameTypeNode isSameDerivedNode,
                    @Cached IsSameTypeNode isSameClsNode,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("getMro.execute(cachedDerived)") MroSequenceStorage mro,
                    @Cached("isInMro(cachedCls, mro, mro.getInternalClassArray().length, isSameTypeInLoopNode)") boolean isInMro) {
        return isInMro;
    }

    @Specialization(guards = {
                    "isSameType(isSameDerivedNode, derived, cachedDerived)",
                    "mro.getInternalClassArray().length < 32"
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached"
    }, assumptions = {
                    "mro.getLookupStableAssumption()",
                    "singleContextAssumption()"
    })
    boolean isSubtypeOfVariableTypeCached(@SuppressWarnings("unused") LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("derived") @SuppressWarnings("unused") LazyPythonClass cachedDerived,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMro,
                    @Cached("getMro.execute(cachedDerived)") MroSequenceStorage mro,
                    @Cached("mro.getInternalClassArray().length") int sz,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameDerivedNode) {
        return isInMro(cls, mro, sz, isSameTypeInLoopNode);
    }

    @Specialization(guards = {
                    "isKindOfBuiltinClass(derived)", // see assertion in isSubMro
                    "isKindOfBuiltinClass(cls)", // see assertion in isSubMro
                    "isSameType(isSameClsNode, cls, cachedCls)",
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached",
    }, assumptions = {
                    "baseMro.getLookupStableAssumption()",
                    "singleContextAssumption()"
    })
    boolean isVariableSubtypeOfConstantTypeCached(LazyPythonClass derived, @SuppressWarnings("unused") LazyPythonClass cls,
                    @Cached("cls") @SuppressWarnings("unused") LazyPythonClass cachedCls,
                    @Cached GetMroStorageNode getMro,
                    @SuppressWarnings("unused") @Cached("getMro.execute(cachedCls)") MroSequenceStorage baseMro,
                    @Cached("baseMro.getInternalClassArray().length") int baseMroLen,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameClsNode) {
        return isSubMro(cachedCls, getMro.execute(derived), baseMroLen, isSameTypeInLoopNode);
    }

    @Specialization(replaces = {
                    "isVariableSubtypeOfConstantTypeCached",
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached"
    })
    boolean issubTypeGeneric(LazyPythonClass derived, LazyPythonClass cls,
                    @Cached("createBinaryProfile()") ConditionProfile builtinClassIsSubtypeProfile,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetMroStorageNode getMro) {
        // a builtin class will never be a subclass of a non-builtin class
        if (builtinClassIsSubtypeProfile.profile(isBuiltinClass(derived) && !isBuiltinClass(cls))) {
            return false;
        }
        for (PythonAbstractClass n : getMro.execute(derived).getInternalClassArray()) {
            if (isSameType(isSameTypeNode, n, cls)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean notClass(Object obj) {
        return !(obj instanceof LazyPythonClass);
    }

    @Specialization(guards = {"notClass(derived) || notClass(cls)"})
    public boolean fallback(VirtualFrame frame, Object derived, Object cls,
                    @Cached AbstractObjectGetBasesNode getBasesNode,
                    @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                    @Cached("createBinaryProfile()") ConditionProfile exceptionDerivedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile exceptionClsProfile,
                    @Cached PRaiseNode raise) {
        if (exceptionDerivedProfile.profile(getBasesNode.execute(frame, derived) == null)) {
            throw raise.raise(PythonErrorType.TypeError, "issubclass() arg 1 must be a class");
        }

        if (exceptionClsProfile.profile(getBasesNode.execute(frame, cls) == null)) {
            throw raise.raise(PythonErrorType.TypeError, "issubclass() arg 2 must be a class or tuple of classes");
        }

        return abstractIsSubclassNode.execute(frame, derived, cls);
    }

    private static boolean isBuiltinClass(LazyPythonClass cls) {
        return cls instanceof PythonBuiltinClass || cls instanceof PythonBuiltinClassType;
    }

    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create();
    }

    @GenerateUncached
    public abstract static class IsSubtypeWithoutFrameNode extends PNodeWithContext {

        public abstract boolean executeWithGlobalState(Object derived, Object cls);

        @Specialization
        public boolean execute(Object derived, Object cls,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached IsSameTypeNode isSameTypeNode) {
            for (PythonAbstractClass n : getMroStorageNode.execute(derived).getInternalClassArray()) {
                if (isSameTypeNode.execute(n, cls)) {
                    return true;
                }
            }
            return false;
        }
    }
}
