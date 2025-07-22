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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FUNC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___KWDEFAULTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CODE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

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
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetDefaultsNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetKeywordDefaultsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMethod)
public final class MethodBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = MethodBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MethodBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "method", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object method(@SuppressWarnings("unused") Object cls, PFunction func, Object self,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, self, func);
        }

        @Specialization
        static Object methodBuiltin(@SuppressWarnings("unused") Object cls, PBuiltinFunction func, Object self,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, self, func);
        }

        @Specialization
        static Object methodGeneric(@SuppressWarnings("unused") Object cls, Object func, Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached PRaiseNode raiseNode) {
            if (callableCheck.execute(inliningTarget, func)) {
                return PFactory.createMethod(language, self, func);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE_S, "");
            }
        }
    }

    @Builtin(name = J___FUNC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FuncNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self) {
            return self.getFunction();
        }
    }

    @Builtin(name = J___CODE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, PMethod self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getCode) {
            return getCode.execute(frame, inliningTarget, self.getFunction(), T___CODE__);
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self.getFunction());
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends GetAttrBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, PMethod self, Object keyObj,
                        @Bind Node inliningTarget,
                        @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Cached PRaiseNode raiseNode) {
            // TODO: (GR-53090) this is different to what CPython does and CPython also does not
            // define tp_descrget for method
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                e.expectAttributeError(inliningTarget, errorProfile);
                return objectGetattrNode.execute(frame, self.getFunction(), key);
            }
        }

        @Specialization(guards = "!isPMethod(self)")
        @InliningCutoff
        static Object getattribute(Object self, @SuppressWarnings("unused") Object key,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETATTRIBUTE__, "method", self);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprMethod(VirtualFrame frame, PMethod method,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object self = method.getSelf();
            Object func = method.getFunction();

            Object funcName = lookup.execute(frame, inliningTarget, func, T___QUALNAME__);
            if (funcName == PNone.NO_VALUE) {
                funcName = lookup.execute(frame, inliningTarget, func, T___NAME__);
            }

            try {
                return simpleTruffleStringFormatNode.format("<bound method %s of %s>", toStringNode.execute(inliningTarget, funcName), repr.execute(frame, inliningTarget, self));
            } catch (CannotCastException e) {
                return simpleTruffleStringFormatNode.format("<bound method ? of %s>", repr.execute(frame, inliningTarget, self));
            }
        }
    }

    @Builtin(name = J___DEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodDefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object defaults(PMethod self,
                        @Bind Node inliningTarget,
                        @Cached GetDefaultsNode getDefaultsNode) {
            Object[] argDefaults = getDefaultsNode.execute(inliningTarget, self);
            assert argDefaults != null;
            return (argDefaults.length == 0) ? PNone.NONE : PFactory.createTuple(PythonLanguage.get(inliningTarget), argDefaults);
        }
    }

    @Builtin(name = J___KWDEFAULTS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetMethodKwdefaultsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object kwDefaults(PMethod self,
                        @Bind Node inliningTarget,
                        @Cached GetKeywordDefaultsNode getKeywordDefaultsNode) {
            PKeyword[] kwdefaults = getKeywordDefaultsNode.execute(inliningTarget, self);
            return (kwdefaults.length > 0) ? PFactory.createDict(PythonLanguage.get(inliningTarget), kwdefaults) : PNone.NONE;
        }
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class GetNode extends DescrGetBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static PMethod doGeneric(PMethod self, Object obj, Object cls) {
            return self;
        }
    }
}
