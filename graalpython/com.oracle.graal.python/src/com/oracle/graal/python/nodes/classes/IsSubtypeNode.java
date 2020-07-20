/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
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
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@ImportStatic({PythonOptions.class, PGuards.class})
@ReportPolymorphism
public abstract class IsSubtypeNode extends PNodeWithContext {
    protected abstract boolean executeInternal(Frame frame, Object derived, Object cls);

    public final boolean execute(VirtualFrame frame, Object derived, Object cls) {
        return executeInternal(frame, derived, cls);
    }

    public final boolean execute(Object derived, Object cls) {
        return executeInternal(null, derived, cls);
    }

    protected static boolean isSameType(IsSameTypeNode isSameTypeNode, Object cls, Object cachedCls) {
        return isSameTypeNode.execute(cls, cachedCls);
    }

    protected boolean isSubMro(Object base, MroSequenceStorage derivedMro, int baseMroLen, IsSameTypeNode isSameTypeNode) {
        CompilerAsserts.partialEvaluationConstant(baseMroLen);
        PythonAbstractClass[] derivedMroAry = derivedMro.getInternalClassArray();
        int derivedMroLen = derivedMroAry.length;
        int offset = derivedMroLen - baseMroLen;
        if (offset >= 0) {
            return isSameType(isSameTypeNode, derivedMroAry[offset], base);
        } else {
            return false;
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected boolean isInMro(Object cls, MroSequenceStorage mro, int sz, IsSameTypeNode isSameTypeNode) {
        PythonAbstractClass[] mroAry = mro.getInternalClassArray();
        for (int i = 0; i < sz; i++) {
            if (isSameType(isSameTypeNode, mroAry[i], cls)) {
                return true;
            }
        }
        return false;
    }

    protected PythonBuiltinClassType getType(Object cls, ConditionProfile builtinType, ConditionProfile builtinClass) {
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
    boolean isSubtypeOfCachedMultiContext(Object derived, Object cls,
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
    boolean isVariableSubtypeOfConstantTypeCachedMultiContext(Object derived, @SuppressWarnings("unused") Object cls,
                    @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile builtinTypeProfile,
                    @SuppressWarnings("unused") @Cached("createBinaryProfile()") ConditionProfile builtinClassProfile,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("getType(cls, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedCls,
                    @Cached("getMro.execute(cachedCls).getInternalClassArray().length") int baseMroLen) {
        return isSubMro(cachedCls, getMro.execute(derived), baseMroLen, isSameTypeNode);
    }

    @Specialization(guards = {
                    "libD.isLazyPythonClass(derived)", "libC.isLazyPythonClass(cls)",
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
    boolean isSubtypeOfCached(Object derived, Object cls,
                    @Cached("derived") Object cachedDerived,
                    @Cached("cls") Object cachedCls,
                    @CachedLibrary("derived") PythonObjectLibrary libD,
                    @CachedLibrary("cls") PythonObjectLibrary libC,
                    @Cached IsSameTypeNode isSameDerivedNode,
                    @Cached IsSameTypeNode isSameClsNode,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("getMro.execute(cachedDerived)") MroSequenceStorage mro,
                    @Cached("isInMro(cachedCls, mro, mro.getInternalClassArray().length, isSameTypeInLoopNode)") boolean isInMro) {
        return isInMro;
    }

    @Specialization(guards = {
                    "libD.isLazyPythonClass(derived)", "libC.isLazyPythonClass(cls)",
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
    boolean isSubtypeOfVariableTypeCached(@SuppressWarnings("unused") Object derived, Object cls,
                    @Cached("derived") @SuppressWarnings("unused") Object cachedDerived,
                    @SuppressWarnings("unused") @CachedLibrary("derived") PythonObjectLibrary libD,
                    @SuppressWarnings("unused") @CachedLibrary("cls") PythonObjectLibrary libC,
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
    boolean isVariableSubtypeOfConstantTypeCached(Object derived, @SuppressWarnings("unused") Object cls,
                    @Cached("cls") @SuppressWarnings("unused") Object cachedCls,
                    @Cached GetMroStorageNode getMro,
                    @SuppressWarnings("unused") @Cached("getMro.execute(cachedCls)") MroSequenceStorage baseMro,
                    @Cached("baseMro.getInternalClassArray().length") int baseMroLen,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameClsNode) {
        return isSubMro(cachedCls, getMro.execute(derived), baseMroLen, isSameTypeInLoopNode);
    }

    @Specialization(guards = {"libD.isLazyPythonClass(derived)", "libC.isLazyPythonClass(cls)"}, replaces = {
                    "isVariableSubtypeOfConstantTypeCached",
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached"
    }, limit = "4")
    boolean issubTypeGeneric(Object derived, Object cls,
                    @SuppressWarnings("unused") @CachedLibrary("derived") PythonObjectLibrary libD,
                    @SuppressWarnings("unused") @CachedLibrary("cls") PythonObjectLibrary libC,
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

    @Specialization(guards = {"!libD.isLazyPythonClass(derived) || !libC.isLazyPythonClass(cls)"})
    public boolean fallback(VirtualFrame frame, Object derived, Object cls,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary libD,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary libC,
                    @Cached AbstractObjectGetBasesNode getBasesNode,
                    @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                    @Cached("createBinaryProfile()") ConditionProfile exceptionDerivedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile exceptionClsProfile,
                    @Cached PRaiseNode raise) {
        if (exceptionDerivedProfile.profile(getBasesNode.execute(frame, derived) == null)) {
            throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
        }

        if (exceptionClsProfile.profile(getBasesNode.execute(frame, cls) == null)) {
            throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ISSUBCLASS_MUST_BE_CLASS_OR_TUPLE);
        }

        return abstractIsSubclassNode.execute(frame, derived, cls);
    }

    private static boolean isBuiltinClass(Object cls) {
        return cls instanceof PythonBuiltinClass || cls instanceof PythonBuiltinClassType;
    }

    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create();
    }

    public static IsSubtypeNode getUncached() {
        return IsSubtypeNodeGen.getUncached();
    }
}
