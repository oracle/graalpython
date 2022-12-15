/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
@NodeInfo(shortName = "cpython://Objects/abstract.c/recursive_issubclass")
@ImportStatic({PythonOptions.class, PGuards.class})
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

    /**
     * This method is used to search for a constant base type in the mro of non-constant potential
     * subtypes when all subtypes' MROs have the same length. Since the entire base mro must
     * strictly be behind the base, we only need to search from the beginning of the mro to the
     * length difference.
     */
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected boolean isSubMro(Object base, PythonAbstractClass[] derivedMroAry, int mroDiff, IsSameTypeNode isSameTypeNode) {
        CompilerAsserts.partialEvaluationConstant(base);
        CompilerAsserts.partialEvaluationConstant(mroDiff);
        for (int i = 0; i <= mroDiff; i++) {
            if (isSameType(isSameTypeNode, derivedMroAry[i], base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is used to search in a constant length subtype mro for a (non-constant) base
     * type. It has to loop over the entire mro to do this.
     */
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

    @Specialization(guards = "isSameType(isSameTypeNode, derived, cls)")
    @SuppressWarnings("unused")
    static boolean isIdentical(Object derived, Object cls,
                    @Cached IsSameTypeNode isSameTypeNode) {
        // trivial case: derived == cls
        return true;
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
                    @Cached ConditionProfile builtinTypeProfile,
                    @Cached ConditionProfile builtinClassProfile,
                    @Cached("getType(derived, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedDerived,
                    @Cached("getType(cls, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedCls,
                    @SuppressWarnings("unused") @Cached IsSameTypeNode isSameTypeNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("isInMro(cachedCls, getMro.execute(cachedDerived), getMro.execute(cachedDerived).getInternalClassArray().length, isSameTypeNode)") boolean isInMro) {
        return isInMro;
    }

    protected static int sub(int a, int b) {
        return a - b;
    }

    @Specialization(guards = {
                    "cachedCls != null",
                    "getType(cls, builtinTypeProfile, builtinClassProfile) == cachedCls",
                    "isKindOfBuiltinClass(derived)", // see assertion in isSubMro
                    "mroAry.length == derivedMroLen",
                    "mroDiff < 16",
    }, replaces = "isSubtypeOfCachedMultiContext", limit = "getVariableArgumentInlineCacheLimit()")
    boolean isVariableSubtypeOfConstantTypeCachedMultiContext(@SuppressWarnings("unused") Object derived, @SuppressWarnings("unused") Object cls,
                    @SuppressWarnings("unused") @Cached ConditionProfile builtinTypeProfile,
                    @SuppressWarnings("unused") @Cached ConditionProfile builtinClassProfile,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMro,
                    @Bind("getMro.execute(derived).getInternalClassArray()") PythonAbstractClass[] mroAry,
                    @SuppressWarnings("unused") @Cached("mroAry.length") int derivedMroLen,
                    @Cached("getType(cls, builtinTypeProfile, builtinClassProfile)") PythonBuiltinClassType cachedCls,
                    @Cached("sub(derivedMroLen, getMro.execute(cachedCls).getInternalClassArray().length)") int mroDiff) {
        return isSubMro(cachedCls, mroAry, mroDiff, isSameTypeNode);
    }

    @Specialization(guards = {
                    "isSingleContext()",
                    "isTypeDerived.execute(derived)", "isTypeCls.execute(cls)",
                    "isSameType(isSameDerivedNode, derived, cachedDerived)",
                    "isSameType(isSameClsNode, cls, cachedCls)",
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
    }, assumptions = {
                    "mro.getLookupStableAssumption()",
    })
    @SuppressWarnings("unused")
    boolean isSubtypeOfCached(Object derived, Object cls,
                    @Cached("derived") Object cachedDerived,
                    @Cached("cls") Object cachedCls,
                    @Cached TypeNodes.IsTypeNode isTypeDerived,
                    @Cached TypeNodes.IsTypeNode isTypeCls,
                    @Cached IsSameTypeNode isSameDerivedNode,
                    @Cached IsSameTypeNode isSameClsNode,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached GetMroStorageNode getMro,
                    @Cached("getMro.execute(cachedDerived)") MroSequenceStorage mro,
                    @Cached("isInMro(cachedCls, mro, mro.getInternalClassArray().length, isSameTypeInLoopNode)") boolean isInMro) {
        return isInMro;
    }

    @Specialization(guards = {
                    "isSingleContext()",
                    "isTypeDerived.execute(derived)", "isTypeCls.execute(cls)",
                    "isSameType(isSameDerivedNode, derived, cachedDerived)",
                    "mro.getInternalClassArray().length < 32"
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached"
    }, assumptions = {
                    "mro.getLookupStableAssumption()"
    })
    boolean isSubtypeOfVariableTypeCached(@SuppressWarnings("unused") Object derived, Object cls,
                    @Cached("derived") @SuppressWarnings("unused") Object cachedDerived,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeDerived,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeCls,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMro,
                    @Cached("getMro.execute(cachedDerived)") MroSequenceStorage mro,
                    @Cached("mro.getInternalClassArray().length") int sz,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameDerivedNode) {
        return isInMro(cls, mro, sz, isSameTypeInLoopNode);
    }

    @Specialization(guards = {
                    "isSingleContext()",
                    "isKindOfBuiltinClass(derived)", // see assertion in isSubMro
                    "isKindOfBuiltinClass(cls)", // see assertion in isSubMro
                    "mroAry.length == derivedMroLen",
                    "mroDiff < 16",
                    "isSameType(isSameClsNode, cls, cachedCls)",
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached",
    }, assumptions = {
                    "baseMro.getLookupStableAssumption()"
    })
    boolean isVariableSubtypeOfConstantTypeCached(@SuppressWarnings("unused") Object derived, @SuppressWarnings("unused") Object cls,
                    @Cached("cls") @SuppressWarnings("unused") Object cachedCls,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMro,
                    @SuppressWarnings("unused") @Cached("getMro.execute(cachedCls)") MroSequenceStorage baseMro,
                    @Cached IsSameTypeNode isSameTypeInLoopNode,
                    @Bind("getMro.execute(derived).getInternalClassArray()") PythonAbstractClass[] mroAry,
                    @SuppressWarnings("unused") @Cached("mroAry.length") int derivedMroLen,
                    @Cached("sub(derivedMroLen, baseMro.getInternalClassArray().length)") int mroDiff,
                    @Cached @SuppressWarnings("unused") IsSameTypeNode isSameClsNode) {
        return isSubMro(cachedCls, mroAry, mroDiff, isSameTypeInLoopNode);
    }

    @Specialization(guards = {
                    "isTypeDerived.execute(derived)", "isTypeCls.execute(cls)",
                    "mro.getInternalClassArray().length == sz",
                    "sz < 16"
    }, limit = "getVariableArgumentInlineCacheLimit()", replaces = {
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached",
                    "isVariableSubtypeOfConstantTypeCached",
    })
    boolean isSubtypeGenericCachedLen(@SuppressWarnings("unused") Object derived, Object cls,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeDerived,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeCls,
                    @SuppressWarnings("unused") @Cached GetMroStorageNode getMro,
                    @Bind("getMro.execute(derived)") MroSequenceStorage mro,
                    @Cached("mro.getInternalClassArray().length") int sz,
                    @Cached IsSameTypeNode isSameTypeInLoopNode) {
        return isInMro(cls, mro, sz, isSameTypeInLoopNode);
    }

    @Specialization(guards = {"isTypeDerived.execute(derived)", "isTypeCls.execute(cls)"}, replaces = {
                    "isVariableSubtypeOfConstantTypeCached",
                    "isSubtypeOfCachedMultiContext",
                    "isVariableSubtypeOfConstantTypeCachedMultiContext",
                    "isSubtypeOfCached",
                    "isSubtypeOfVariableTypeCached",
                    "isSubtypeGenericCachedLen"
    }, limit = "1")
    @Megamorphic
    boolean issubTypeGeneric(Object derived, Object cls,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeDerived,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeCls,
                    @Cached ConditionProfile builtinClassIsSubtypeProfile,
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

    @Specialization(guards = {"!isTypeDerived.execute(derived) || !isTypeCls.execute(cls)"}, limit = "1")
    @Megamorphic
    boolean fallback(VirtualFrame frame, Object derived, Object cls,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeDerived,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeCls,
                    @Cached AbstractObjectGetBasesNode getBasesNode,
                    @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                    @Cached ConditionProfile exceptionDerivedProfile,
                    @Cached ConditionProfile exceptionClsProfile,
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

    @NeverDefault
    public static IsSubtypeNode create() {
        return IsSubtypeNodeGen.create();
    }

    public static IsSubtypeNode getUncached() {
        return IsSubtypeNodeGen.getUncached();
    }
}
