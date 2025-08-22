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

package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_NEW;
import static com.oracle.graal.python.nodes.BuiltinNames.J_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.PGuards.isDeleteMarker;
import static com.oracle.graal.python.nodes.PGuards.isDict;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.PGuards.isPythonModule;
import static com.oracle.graal.python.nodes.PGuards.isPythonObject;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SIZEOF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSHOOK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_JOIN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_SORT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NONE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SINGLE_QUOTE_COMMA_SPACE;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrDeleteNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrGetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrSetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsClinicProviders.ReduceExNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.GetAttributeNodeFactory;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked0Node;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRepr.CallSlotReprNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.MergedObjectTypeModuleGetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsOtherBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.DeleteDictNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonObject)
public final class ObjectBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ObjectBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CLASS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ClassNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object getClass(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode) {
            return getClassNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "!isNoValue(value)")
        static PNone setClass(VirtualFrame frame, Object self, Object value,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached IsBuiltinClassProfile isModuleProfile,
                        @Cached TypeNodes.GetTypeFlagsNode getTypeFlagsNode,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigmentNode,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Cached SetClassNode setClassNode,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CLASS_MUST_BE_SET_TO_CLASS, value);
            }
            Object type = getClassNode.execute(inliningTarget, self);
            boolean bothModuleSubtypes = isModuleProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PythonModule) &&
                            isModuleProfile.profileClass(inliningTarget, value, PythonBuiltinClassType.PythonModule);
            boolean bothMutable = (getTypeFlagsNode.execute(type) & TypeFlags.IMMUTABLETYPE) == 0 && (getTypeFlagsNode.execute(value) & TypeFlags.IMMUTABLETYPE) == 0;
            if (!bothModuleSubtypes && !bothMutable) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CLASS_ASSIGNMENT_ONLY_SUPPORTED_FOR_HEAP_TYPES_OR_MODTYPE_SUBCLASSES);
            }

            checkCompatibleForAssigmentNode.execute(frame, type, value);
            setClassNode.execute(inliningTarget, self, value);

            return PNone.NONE;
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class SetClassNode extends Node {
            public abstract void execute(Node inliningTarget, Object self, Object newClass);

            @Specialization
            static void doPythonObject(PythonObject self, Object newClass,
                            @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
                // Clear the dynamic type when setting the class, so further class changes do not
                // create new shapes
                dylib.setDynamicType(self, PNone.NO_VALUE);
                self.setPythonClass(newClass);
            }

            @Specialization
            static void doNative(PythonAbstractNativeObject self, Object newClass,
                            @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNewRefNode) {
                writeObjectNewRefNode.writeToObject(self, CFields.PyObject__ob_type, newClass);
            }
        }
    }

    // object()
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {

        @Child private ReportAbstractClassNode reportAbstractClassNode;

        @GenerateInline(false) // Used lazily
        abstract static class ReportAbstractClassNode extends PNodeWithContext {
            public abstract PException execute(VirtualFrame frame, Object type);

            @Specialization
            static PException report(VirtualFrame frame, Object type,
                            @Bind Node inliningTarget,
                            @Cached PyObjectCallMethodObjArgs callSort,
                            @Cached PyObjectCallMethodObjArgs callJoin,
                            @Cached PyObjectSizeNode sizeNode,
                            @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                            @Cached CastToTruffleStringNode cast,
                            @Cached ListNodes.ConstructListNode constructListNode,
                            @Cached PRaiseNode raiseNode) {
                PList list = constructListNode.execute(frame, readAttributeFromObjectNode.execute(type, T___ABSTRACTMETHODS__));
                int methodCount = sizeNode.execute(frame, inliningTarget, list);
                callSort.execute(frame, inliningTarget, list, T_SORT);
                TruffleString joined = cast.execute(inliningTarget, callJoin.execute(frame, inliningTarget, T_SINGLE_QUOTE_COMMA_SPACE, T_JOIN, list));
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_INSTANTIATE_ABSTRACT_CLASS_WITH_ABSTRACT_METHODS, type, methodCount > 1 ? "s" : "", joined);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CheckExcessArgsNode extends Node {
            abstract void execute(Node inliningTarget, Object type, Object[] args, PKeyword[] kwargs);

            @Specialization(guards = {"args.length == 0", "kwargs.length == 0"})
            @SuppressWarnings("unused")
            static void doNothing(Object type, Object[] args, PKeyword[] kwargs) {
            }

            @Fallback
            @SuppressWarnings("unused")
            static void check(Node inliningTarget, Object type, Object[] args, PKeyword[] kwargs,
                            @Cached GetCachedTpSlotsNode getSlots,
                            @Cached PRaiseNode raiseNode) {
                TpSlots slots = getSlots.execute(inliningTarget, type);
                if (slots.tp_new() != SLOTS.tp_new()) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.NEW_TAKES_ONE_ARG);
                }
                if (slots.tp_init() == SLOTS.tp_init()) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.NEW_TAKES_NO_ARGS, type);
                }
            }
        }

        @Specialization(guards = {"!self.needsNativeAllocation()"})
        Object doManagedObject(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached CheckExcessArgsNode checkExcessArgsNode,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            checkExcessArgsNode.execute(inliningTarget, self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw reportAbstractClass(frame, self);
            }
            return PFactory.createPythonObject(language, self, getInstanceShape.execute(self));
        }

        @Specialization
        static Object doBuiltinTypeType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached CheckExcessArgsNode checkExcessArgsNode,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            checkExcessArgsNode.execute(inliningTarget, self, varargs, kwargs);
            return PFactory.createPythonObject(language, self, getInstanceShape.execute(self));
        }

        @Specialization(guards = "self.needsNativeAllocation()")
        @SuppressWarnings("truffle-static-method")
        @InliningCutoff
        Object doNativeObjectIndirect(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Shared @Cached CheckExcessArgsNode checkExcessArgsNode,
                        @Shared @Cached CallNativeGenericNewNode callNativeGenericNewNode) {
            checkExcessArgsNode.execute(inliningTarget, self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw reportAbstractClass(frame, self);
            }
            return callNativeGenericNewNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "isNativeClass(self)")
        @SuppressWarnings("truffle-static-method")
        @InliningCutoff
        Object doNativeObjectDirect(VirtualFrame frame, Object self, Object[] varargs, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Shared @Cached CheckExcessArgsNode checkExcessArgsNode,
                        @Exclusive @Cached TypeNodes.GetTypeFlagsNode getTypeFlagsNode,
                        @Shared @Cached CallNativeGenericNewNode callNativeGenericNewNode) {
            checkExcessArgsNode.execute(inliningTarget, self, varargs, kwargs);
            if ((getTypeFlagsNode.execute(self) & TypeFlags.IS_ABSTRACT) != 0) {
                throw reportAbstractClass(frame, self);
            }
            return callNativeGenericNewNode.execute(inliningTarget, self);
        }

        @GenerateInline
        @GenerateCached(false)
        protected abstract static class CallNativeGenericNewNode extends Node {
            abstract Object execute(Node inliningTarget, Object cls);

            @Specialization
            static Object call(Object cls,
                            @Cached(inline = false) CApiTransitions.PythonToNativeNode toNativeNode,
                            @Cached(inline = false) CApiTransitions.NativeToPythonTransferNode toPythonNode,
                            @Cached(inline = false) CExtNodes.PCallCapiFunction callCapiFunction) {
                return toPythonNode.execute(callCapiFunction.call(FUN_PY_OBJECT_NEW, toNativeNode.execute(cls)));
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object o, Object[] varargs, PKeyword[] kwargs) {
            throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "object.__new__(X): X", o);
        }

        @InliningCutoff
        private PException reportAbstractClass(VirtualFrame frame, Object type) {
            if (reportAbstractClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportAbstractClassNode = insert(ObjectBuiltinsFactory.ObjectNodeFactory.ReportAbstractClassNodeGen.create());
            }
            return reportAbstractClassNode.execute(frame, type);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = {"arguments.length == 0", "keywords.length == 0"})
        @SuppressWarnings("unused")
        static PNone initNoArgs(Object self, Object[] arguments, PKeyword[] keywords) {
            return PNone.NONE;
        }

        @Specialization(replaces = "initNoArgs")
        @SuppressWarnings("unused")
        static PNone init(Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached PRaiseNode raiseNode) {
            if (arguments.length != 0 || keywords.length != 0) {
                Object type = getClassNode.execute(inliningTarget, self);
                TpSlots slots = getSlots.execute(inliningTarget, type);
                if (slots.tp_init() != SLOTS.tp_init()) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INIT_TAKES_ONE_ARG_OBJECT);
                }

                if (slots.tp_new() == SLOTS.tp_new()) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INIT_TAKES_ONE_ARG, type);
                }
            }
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        public long hash(PythonBuiltinClassType self) {
            return hash(getContext().lookupType(self));
        }

        @TruffleBoundary
        @Specialization(guards = "!isPythonBuiltinClassType(self)")
        public static long hash(Object self) {
            return self.hashCode();
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class EqNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization(guards = "op.isEq()")
        static Object eq(Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile isEq,
                        @Cached IsNode isNode) {
            if (isEq.profile(inliningTarget, isNode.execute(self, other))) {
                return true;
            } else {
                // Return NotImplemented instead of False, so if two objects are compared, both get
                // a chance at the comparison
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = "op.isNe()")
        static Object ne(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile isEq,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached TpSlotRichCompare.CallSlotRichCmpNode callSlotRichCmp,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            // By default, __ne__() delegates to __eq__() and inverts the result, unless the latter
            // returns NotImplemented
            TpSlot selfRichCmp = getSlotsNode.execute(inliningTarget, self).tp_richcmp();
            if (selfRichCmp == null) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            Object result = callSlotRichCmp.execute(frame, inliningTarget, selfRichCmp, self, other, RichCmpOp.Py_EQ);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return !isTrueNode.execute(frame, result);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Fallback
        static Object doOthers(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotReprNode callSlot,
                        @Cached ObjectNodes.DefaultObjectReprNode defaultRepr) {
            TpSlots slots = getSlots.execute(inliningTarget, self);
            if (slots.tp_repr() != null) {
                return callSlot.execute(frame, inliningTarget, slots.tp_repr(), self);
            }
            return defaultRepr.execute(frame, inliningTarget, self);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isNone(self)")
        static TruffleString reprNone(@SuppressWarnings("unused") PNone self) {
            return T_NONE;
        }

        @Specialization(guards = "!isNone(self)")
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached ObjectNodes.DefaultObjectReprNode defaultReprNode) {
            return defaultReprNode.execute(frame, inliningTarget, self);
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends GetAttrBuiltinNode {
        @Child private CallSlotDescrGet callSlotDescrGet;
        @Child private ReadAttributeFromObjectNode attrRead;

        /**
         * Keep in sync with {@link TypeBuiltins.GetattributeNode} and
         * {@link ThreadLocalBuiltins.GetAttributeNode} and
         * {@link MergedObjectTypeModuleGetAttributeNode}
         */
        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached CastToTruffleStringChecked1Node castToString,
                        @Cached InlinedConditionProfile hasDescProfile,
                        @Cached InlinedConditionProfile hasDescrGetProfile,
                        @Cached InlinedConditionProfile hasValueProfile,
                        @Cached PRaiseNode raiseNode) {
            TruffleString key = castToString.cast(inliningTarget, keyObj, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);

            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = lookup.execute(type, key);
            boolean hasDescr = hasDescProfile.profile(inliningTarget, descr != PNone.NO_VALUE);

            TpSlot get = null;
            boolean hasDescrGet = false;
            if (hasDescr) {
                var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                get = descrSlots.tp_descr_get();
                hasDescrGet = hasDescrGetProfile.profile(inliningTarget, get != null);
                if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return dispatchDescrGet(frame, object, type, descr, get);
                }
            }

            // The only difference between all 3 nodes
            Object value = readAttributeOfObject(object, key);
            if (hasValueProfile.profile(inliningTarget, value != PNone.NO_VALUE)) {
                return value;
            }

            if (hasDescr) {
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return dispatchDescrGet(frame, object, type, descr, get);
                }
            }

            throw raiseNode.raiseAttributeError(inliningTarget, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }

        private Object readAttributeOfObject(Object object, TruffleString key) {
            if (attrRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attrRead = insert(ReadAttributeFromObjectNode.create());
            }
            return attrRead.execute(object, key);
        }

        private Object dispatchDescrGet(VirtualFrame frame, Object object, Object type, Object descr, TpSlot getSlot) {
            if (callSlotDescrGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSlotDescrGet = insert(CallSlotDescrGet.create());
            }
            return callSlotDescrGet.executeCached(frame, getSlot, descr, object, type);
        }

        // Note: we need this factory method as a workaround of a Truffle DSL bug
        @NeverDefault
        public static GetAttributeNode create() {
            return GetAttributeNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization
        void set(VirtualFrame frame, Object object, Object keyObject, Object value,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringChecked0Node castKeyNode,
                        @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Cached WriteAttributeToObjectNode write) {
            TruffleString key = castKeyNode.cast(inliningTarget, keyObject, ATTR_NAME_MUST_BE_STRING);
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
        }

        @NeverDefault
        public static SetattrNode create() {
            return ObjectBuiltinsFactory.SetattrNodeFactory.create();
        }
    }

    @Builtin(name = J___DICT__, autoRegister = false, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {

        protected static boolean isExactObject(Node inliningTarget, IsBuiltinClassExactProfile profile, Object clazz) {
            return profile.profileClass(inliningTarget, clazz, PythonBuiltinClassType.PythonObject);
        }

        protected static boolean isAnyBuiltinButModule(Node inliningTarget, IsOtherBuiltinClassProfile profile, Object clazz) {
            // any builtin class except Modules
            return profile.profileIsOtherBuiltinClass(inliningTarget, clazz, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"!isAnyBuiltinButModule(inliningTarget, otherBuiltinClassProfile, selfClass)", //
                        "!isExactObject(inliningTarget, isBuiltinClassProfile, selfClass)", "isNoValue(none)"}, limit = "1")
        static Object dict(VirtualFrame frame, Object self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached IsOtherBuiltinClassProfile otherBuiltinClassProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached IsBuiltinClassExactProfile isBuiltinClassProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @Bind("getClassNode.execute(inliningTarget, self)") Object selfClass,
                        @Exclusive @Cached GetBaseClassNode getBaseNode,
                        @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") @Shared LookupAttributeInMRONode getDescrNode,
                        @Cached DescrGetNode getNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Exclusive @Cached InlinedBranchProfile branchProfile) {
            // typeobject.c#subtype_getdict()
            Object func = getDescrFromBuiltinBase(inliningTarget, selfClass, getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter(inliningTarget);
                return getNode.execute(frame, func, self);
            }

            return getDict.execute(inliningTarget, self);
        }

        @Specialization(guards = {"!isAnyBuiltinButModule(inliningTarget, otherBuiltinClassProfile, selfClass)", //
                        "!isExactObject(inliningTarget, isBuiltinClassProfile, selfClass)", "!isPythonModule(self)"}, limit = "1")
        static Object dict(VirtualFrame frame, Object self, PDict dict,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached IsOtherBuiltinClassProfile otherBuiltinClassProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached IsBuiltinClassExactProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Bind("getClassNode.execute(inliningTarget, self)") Object selfClass,
                        @Exclusive @Cached GetBaseClassNode getBaseNode,
                        @Shared @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") LookupAttributeInMRONode getDescrNode,
                        @Cached DescrSetNode setNode,
                        @Cached SetDictNode setDict,
                        @Exclusive @Cached InlinedBranchProfile branchProfile) {
            // typeobject.c#subtype_setdict()
            Object func = getDescrFromBuiltinBase(inliningTarget, getClassNode.execute(inliningTarget, self), getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter(inliningTarget);
                return setNode.execute(frame, func, self, dict);
            }

            setDict.execute(inliningTarget, self, dict);
            return PNone.NONE;
        }

        @Specialization
        static Object dict(VirtualFrame frame, PythonObject self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Exclusive @Cached GetBaseClassNode getBaseNode,
                        @Shared @Cached("createForLookupOfUnmanagedClasses(T___DICT__)") LookupAttributeInMRONode getDescrNode,
                        @Cached DescrDeleteNode deleteNode,
                        @Cached DeleteDictNode deleteDictNode,
                        @Exclusive @Cached InlinedBranchProfile branchProfile) {
            // typeobject.c#subtype_setdict()
            Object func = getDescrFromBuiltinBase(inliningTarget, getClassNode.execute(inliningTarget, self), getBaseNode, getDescrNode);
            if (func != null) {
                branchProfile.enter(inliningTarget);
                return deleteNode.execute(frame, func, self);
            }
            deleteDictNode.execute(self);
            return PNone.NONE;
        }

        /**
         * see typeobject.c#get_builtin_base_with_dict()
         */
        private static Object getDescrFromBuiltinBase(Node inliningTarget, Object type, GetBaseClassNode getBaseNode, LookupAttributeInMRONode getDescrNode) {
            Object t = type;
            Object base = getBaseNode.execute(inliningTarget, t);
            while (base != null) {
                if (t instanceof PythonBuiltinClass) {
                    Object func = getDescrNode.execute(t);
                    if (func != PNone.NO_VALUE) {
                        return func;
                    }
                }
                t = base;
                base = getBaseNode.execute(inliningTarget, t);
            }
            return null;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)", "!isDeleteMarker(mapping)"})
        static Object dict(@SuppressWarnings("unused") Object self, Object mapping,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }

        @Specialization(guards = "isFallback(self, mapping, inliningTarget, getClassNode, otherBuiltinClassProfile, isBuiltinClassProfile)", limit = "1")
        @SuppressWarnings("unused")
        static Object raise(Object self, Object mapping,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached IsOtherBuiltinClassProfile otherBuiltinClassProfile,
                        @Exclusive @Cached IsBuiltinClassExactProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetClassNode getClassNode) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, "__dict__");
        }

        static boolean isFallback(Object self, Object mapping, Node inliningTarget,
                        GetClassNode getClassNode,
                        IsOtherBuiltinClassProfile otherBuiltinClassProfile,
                        IsBuiltinClassExactProfile isBuiltinClassProfile) {
            Object selfClass = getClassNode.execute(inliningTarget, self);
            boolean classFilter = !isAnyBuiltinButModule(inliningTarget, otherBuiltinClassProfile, selfClass) && !isExactObject(inliningTarget, isBuiltinClassProfile, selfClass);
            return !((classFilter && isNoValue(mapping)) ||
                            (classFilter && !isPythonModule(self) && isDict(mapping)) ||
                            (isPythonObject(self) && isDeleteMarker(mapping)) ||
                            (!isNoValue(mapping) && !isDict(mapping) && !isDeleteMarker(mapping)));
        }

        @NeverDefault
        public static DictNode create() {
            return DictNodeFactory.create();
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static Object format(Object self, @SuppressWarnings("unused") TruffleString formatString,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_FORMAT_STRING_PASSED_TO_P_FORMAT, self);
        }

        @Specialization(guards = "formatString.isEmpty()")
        static Object format(VirtualFrame frame, Object self, @SuppressWarnings("unused") TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode str) {
            return str.execute(frame, inliningTarget, self);
        }
    }

    @Builtin(name = J___INIT_SUBCLASS__, minNumOfPositionalArgs = 1, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class InitSubclass extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone initSubclass(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SUBCLASSHOOK__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true, takesVarArgs = true, takesVarKeywordArgs = true, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class SubclassHookNode extends PythonVarargsBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object notImplemented(Object self, Object[] arguments, PKeyword[] keywords) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = J___SIZEOF__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SizeOfNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(VirtualFrame frame, Object obj,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached TypeNodes.GetBasicSizeNode getBasicSizeNode,
                        @Cached TypeNodes.GetItemSizeNode getItemSizeNode) {
            Object cls = getClassNode.execute(inliningTarget, obj);
            long size = 0;
            long itemsize = getItemSizeNode.execute(inliningTarget, cls);
            if (itemsize != 0) {
                Object objLen = lookupAttr.execute(frame, inliningTarget, obj, T___LEN__);
                if (objLen != PNone.NO_VALUE) {
                    size = sizeNode.execute(frame, inliningTarget, obj) * itemsize;
                }
            }
            size += getBasicSizeNode.execute(inliningTarget, cls);
            return size;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    // Note: this must not inherit from PythonUnaryBuiltinNode, i.e. must not be AST inlined.
    // The CommonReduceNode seems to need a fresh frame, otherwise it can mess up the existing one.
    public abstract static class ReduceNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object ignored,
                        @Bind Node inliningTarget,
                        @Cached ObjectNodes.CommonReduceNode commonReduceNode) {
            return commonReduceNode.execute(frame, inliningTarget, obj, 0);
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    // Note: this must not inherit from PythonBinaryClinicBuiltinNode, i.e. must not be AST inlined.
    // The CommonReduceNode seems to need a fresh frame, otherwise it can mess up the existing one.
    public abstract static class ReduceExNode extends PythonClinicBuiltinNode {
        static final Object REDUCE_FACTORY = ObjectBuiltinsFactory.ReduceNodeFactory.getInstance();

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ReduceExNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, Object obj, int proto,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CallNode callNode,
                        @Cached InlinedConditionProfile reduceProfile,
                        @Cached ObjectNodes.CommonReduceNode commonReduceNode) {
            Object _reduce = lookupAttr.execute(frame, inliningTarget, obj, T___REDUCE__);
            if (reduceProfile.profile(inliningTarget, _reduce != PNone.NO_VALUE)) {
                // Check if __reduce__ has been overridden:
                // "type(obj).__reduce__ is not object.__reduce__"
                if (!(_reduce instanceof PBuiltinMethod) || ((PBuiltinMethod) _reduce).getBuiltinFunction().getBuiltinNodeFactory() != REDUCE_FACTORY) {
                    return callNode.execute(frame, _reduce);
                }
            }
            return commonReduceNode.execute(frame, inliningTarget, obj, proto);
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for generic objects\n\n\tReturns __dict__, __class__ and recursively up the\n\t__class__.__bases__ chain.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization
        static Object dir(VirtualFrame frame, Object obj,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached SetBuiltins.UpdateSingleNode updateSetNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ConstructListNode constructListNode,
                        @Bind PythonLanguage language) {
            PSet names = PFactory.createSet(language);
            Object ns = lookupAttrNode.execute(frame, inliningTarget, obj, T___DICT__);
            if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, ns), PythonBuiltinClassType.PDict)) {
                updateSetNode.execute(frame, names, ns);
            }
            Object klass = lookupAttrNode.execute(frame, inliningTarget, obj, T___CLASS__);
            if (klass != PNone.NO_VALUE) {
                Object state = IndirectCallContext.enter(frame, inliningTarget, indirectCallData);
                try {
                    TypeBuiltins.DirNode.dir(names, klass);
                } finally {
                    IndirectCallContext.exit(frame, inliningTarget, indirectCallData, state);
                }
            }
            return constructListNode.execute(frame, names);
        }
    }

    @Builtin(name = J___GETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getstate(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached ObjectNodes.ObjectGetStateDefaultNode getstateDefaultNode) {
            return getstateDefaultNode.execute(frame, inliningTarget, self, false);
        }
    }
}
