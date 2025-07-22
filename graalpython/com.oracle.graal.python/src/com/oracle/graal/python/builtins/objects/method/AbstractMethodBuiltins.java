/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_GETATTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___SELF__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PMethod, PythonBuiltinClassType.PBuiltinFunctionOrMethod, PythonBuiltinClassType.MethodWrapper})
public final class AbstractMethodBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = AbstractMethodBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractMethodBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class CallNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object doItNonFunction(VirtualFrame frame, PMethod self, Object[] arguments, PKeyword[] keywords,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode) {
            return callNode.execute(frame, self.getFunction(), PythonUtils.prependArgument(self.getSelf(), arguments), keywords);
        }

        @Specialization
        static Object doItNonFunction(VirtualFrame frame, PBuiltinMethod self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached CallDispatchers.BuiltinMethodCachedCallNode callNode) {
            return callNode.execute(frame, inliningTarget, self, arguments, keywords);
        }

    }

    @Builtin(name = J___SELF__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SelfNode extends PythonBuiltinNode {
        @Specialization
        protected static Object doIt(PMethod self) {
            return self.getSelf() != PNone.NO_VALUE ? self.getSelf() : PNone.NONE;
        }

        @Specialization
        protected static Object doIt(PBuiltinMethod self) {
            if (self.getBuiltinFunction().isStatic()) {
                return PNone.NONE;
            }
            return self.getSelf() != PNone.NO_VALUE ? self.getSelf() : PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class EqNode extends RichCmpBuiltinNode {

        @Child private InteropLibrary identicalLib = InteropLibrary.getFactory().createDispatched(3);
        @Child private InteropLibrary identicalLib2 = InteropLibrary.getFactory().createDispatched(3);

        private boolean eq(Object function1, Object function2, Object self1, Object self2) {
            if (function1 != function2) {
                return false;
            }
            if (self1 != self2) {
                // CPython compares PyObject* pointers:
                if (self1 instanceof PythonAbstractNativeObject && self2 instanceof PythonAbstractNativeObject) {
                    if (identicalLib.isIdentical(((PythonAbstractNativeObject) self1).getPtr(), ((PythonAbstractNativeObject) self2).getPtr(), identicalLib2)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }

        @Specialization(guards = "op.isEqOrNe()")
        boolean eqOrNe(Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile isBuiltinProfile,
                        @Cached InlinedConditionProfile isMethodProfile) {
            Object selfFunction, otherFunction;
            Object selfSelf, otherSelf;

            if (isBuiltinProfile.profile(inliningTarget, self instanceof PBuiltinMethod)) {
                selfFunction = ((PBuiltinMethod) self).getBuiltinFunction();
                selfSelf = ((PBuiltinMethod) self).getSelf();
            } else if (isMethodProfile.profile(inliningTarget, self instanceof PMethod)) {
                selfFunction = ((PMethod) self).getFunction();
                selfSelf = ((PMethod) self).getSelf();
            } else {
                return op.isNe();
            }

            if (isBuiltinProfile.profile(inliningTarget, other instanceof PBuiltinMethod)) {
                otherFunction = ((PBuiltinMethod) other).getBuiltinFunction();
                otherSelf = ((PBuiltinMethod) other).getSelf();
            } else if (isMethodProfile.profile(inliningTarget, other instanceof PMethod)) {
                otherFunction = ((PMethod) other).getFunction();
                otherSelf = ((PMethod) other).getSelf();
            } else {
                return op.isNe();
            }

            return eq(selfFunction, otherFunction, selfSelf, otherSelf) == op.isEq();
        }

        @Fallback
        static Object others(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(PMethod self) {
            return PythonAbstractObject.systemHashCode(self.getSelf()) ^ PythonAbstractObject.systemHashCode(self.getFunction());
        }

        @Specialization
        static long hash(PBuiltinMethod self) {
            return PythonAbstractObject.systemHashCode(self.getSelf()) ^ PythonAbstractObject.systemHashCode(self.getFunction());
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class GetModuleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object getModule(VirtualFrame frame, PBuiltinMethod self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached ReadAttributeFromPythonObjectNode readAttrNode) {
            // No profiling, performance here is not very important
            Object module = readAttrNode.execute(self, T___MODULE__, PNone.NO_VALUE);
            if (module != PNone.NO_VALUE) {
                return module;
            }
            if (self.getSelf() instanceof PythonModule) {
                PythonContext context = PythonContext.get(inliningTarget);
                PythonLanguage language = context.getLanguage(inliningTarget);
                Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
                try {
                    return lookup.execute(null, inliningTarget, self.getSelf(), T___NAME__);
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object getModule(PBuiltinMethod self, Object value,
                        @Cached WriteAttributeToPythonObjectNode writeAttrNode) {
            writeAttrNode.execute(self, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(VirtualFrame frame, PMethod self, @SuppressWarnings("unused") Object value,
                        @Cached("create(T___MODULE__)") GetAttributeNode getAttributeNode) {
            return getAttributeNode.executeObject(frame, self.getFunction());
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object getModule(@SuppressWarnings("unused") PMethod self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "method", T___MODULE__);
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getDoc(PMethod self,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(inliningTarget, self.getFunction(), T___DOC__);
        }

        @Specialization
        static Object getDoc(PBuiltinMethod self,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(inliningTarget, self.getFunction(), T___DOC__);
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getName(VirtualFrame frame, PBuiltinMethod method,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttr) {
            try {
                return toStringNode.execute(inliningTarget, getAttr.execute(frame, inliningTarget, method.getFunction(), T___NAME__));
            } catch (CannotCastException cce) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization
        static Object getName(VirtualFrame frame, PMethod method,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getAttr") @Cached PyObjectGetAttr getAttr) {
            try {
                return toStringNode.execute(inliningTarget, getAttr.execute(frame, inliningTarget, method.getFunction(), T___NAME__));
            } catch (CannotCastException cce) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class QualNameNode extends PythonUnaryBuiltinNode {

        protected static boolean isSelfModuleOrNull(PMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        protected static boolean isSelfModuleOrNull(PBuiltinMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PMethod method,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, lookupName);
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PBuiltinMethod method,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, lookupName);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        static TruffleString doSelfIsObject(VirtualFrame frame, PMethod method,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getQualname") @Cached PyObjectGetAttr getQualname,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return getQualName(frame, inliningTarget, method.getSelf(), method.getFunction(), getClassNode, isTypeNode, toStringNode, getQualname, lookupName, simpleTruffleStringFormatNode,
                            raiseNode);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        static TruffleString doSelfIsObject(VirtualFrame frame, PBuiltinMethod method,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetClassNode getClassNode,
                        @Shared @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getQualname") @Cached PyObjectGetAttr getQualname,
                        @Shared("lookupName") @Cached PyObjectLookupAttr lookupName,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return getQualName(frame, inliningTarget, method.getSelf(), method.getFunction(), getClassNode, isTypeNode, toStringNode, getQualname, lookupName, simpleTruffleStringFormatNode,
                            raiseNode);
        }

        private static TruffleString getQualName(VirtualFrame frame, Node inliningTarget, Object self, Object func, GetClassNode getClassNode, TypeNodes.IsTypeNode isTypeNode,
                        CastToTruffleStringNode toStringNode, PyObjectGetAttr getQualname, PyObjectLookupAttr lookupName, SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        PRaiseNode raiseNode) {
            Object type = isTypeNode.execute(inliningTarget, self) ? self : getClassNode.execute(inliningTarget, self);

            try {
                TruffleString typeQualName = toStringNode.execute(inliningTarget, getQualname.execute(frame, inliningTarget, type, T___QUALNAME__));
                return simpleTruffleStringFormatNode.format("%s.%s", typeQualName, getName(frame, inliningTarget, func, toStringNode, lookupName));
            } catch (CannotCastException cce) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_UNICODE_OBJECT, T___QUALNAME__);
            }
        }

        private static TruffleString getName(VirtualFrame frame, Node inliningTarget, Object func, CastToTruffleStringNode toStringNode, PyObjectLookupAttr lookupName) {
            return toStringNode.execute(inliningTarget, lookupName.execute(frame, inliningTarget, func, T___NAME__));
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonBuiltinNode {
        protected static boolean isSelfModuleOrNull(PMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        protected static boolean isSelfModuleOrNull(PBuiltinMethod method) {
            return method.getSelf() == PNone.NO_VALUE || PGuards.isPythonModule(method.getSelf());
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getName") @Cached PyObjectGetAttr getName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, getName);
        }

        @Specialization(guards = "isSelfModuleOrNull(method)")
        static TruffleString doSelfIsModule(VirtualFrame frame, PBuiltinMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getName") @Cached PyObjectGetAttr getName) {
            return getName(frame, inliningTarget, method.getFunction(), toStringNode, getName);
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        PTuple doSelfIsObject(VirtualFrame frame, PMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getGetAttr") @Cached PyObjectGetAttr getGetAttr,
                        @Shared("getName") @Cached PyObjectGetAttr getName,
                        @Bind PythonLanguage language) {
            PythonModule builtins = getContext().getBuiltins();
            Object getattr = getGetAttr.execute(frame, inliningTarget, builtins, T_GETATTR);
            PTuple args = PFactory.createTuple(language, new Object[]{method.getSelf(), getName(frame, inliningTarget, method.getFunction(), toStringNode, getName)});
            return PFactory.createTuple(language, new Object[]{getattr, args});
        }

        @Specialization(guards = "!isSelfModuleOrNull(method)")
        PTuple doSelfIsObject(VirtualFrame frame, PBuiltinMethod method, @SuppressWarnings("unused") Object obj,
                        @Bind Node inliningTarget,
                        @Shared("toStringNode") @Cached CastToTruffleStringNode toStringNode,
                        @Shared("getGetAttr") @Cached PyObjectGetAttr getGetAttr,
                        @Shared("getName") @Cached PyObjectGetAttr getName,
                        @Bind PythonLanguage language) {
            PythonModule builtins = getContext().getBuiltins();
            Object getattr = getGetAttr.execute(frame, inliningTarget, builtins, T_GETATTR);
            PTuple args = PFactory.createTuple(language, new Object[]{method.getSelf(), getName(frame, inliningTarget, method.getFunction(), toStringNode, getName)});
            return PFactory.createTuple(language, new Object[]{getattr, args});
        }

        private static TruffleString getName(VirtualFrame frame, Node inliningTarget, Object func, CastToTruffleStringNode toStringNode, PyObjectGetAttr getName) {
            return toStringNode.execute(inliningTarget, getName.execute(frame, inliningTarget, func, T___NAME__));
        }
    }
}
