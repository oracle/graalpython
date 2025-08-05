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

package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyHeapTypeObject__ht_name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyHeapTypeObject__ht_qualname;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FLAGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___MRO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TEXT_SIGNATURE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___WEAKREFOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TYPE_PARAMS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSHOOK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MRO_ENTRIES__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins.UpdateSingleNode;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked0Node;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.TypeNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpInitNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs.CallSlotTpNewNode;
import com.oracle.graal.python.builtins.objects.types.GenericTypeNodes;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.MergedObjectTypeModuleGetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonClass)
public final class TypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached("create(T___MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(T___QUALNAME__)") GetFixedAttributeNode readQualNameNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object moduleNameObj = readModuleNode.execute(frame, self);
            Object qualNameObj = readQualNameNode.execute(frame, self);
            TruffleString moduleName = null;
            if (moduleNameObj != NO_VALUE) {
                try {
                    moduleName = castToStringNode.execute(inliningTarget, moduleNameObj);
                } catch (CannotCastException e) {
                    // ignore
                }
            }
            if (moduleName == null || equalNode.execute(moduleName, T_BUILTINS, TS_ENCODING)) {
                return simpleTruffleStringFormatNode.format("<class '%s'>", castToStringNode.execute(inliningTarget, qualNameObj));
            }
            return simpleTruffleStringFormatNode.format("<class '%s.%s'>", moduleName, castToStringNode.execute(inliningTarget, qualNameObj));
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class DocNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object getDoc(PythonBuiltinClassType self, @SuppressWarnings("unused") PNone value) {
            return self.getDoc() != null ? self.getDoc() : PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        @TruffleBoundary
        static Object getDoc(PythonBuiltinClass self, @SuppressWarnings("unused") PNone value) {
            return getDoc(self.getType(), value);
        }

        @Specialization(guards = {"isNoValue(value)", "!isPythonBuiltinClass(self)"})
        static Object getDoc(VirtualFrame frame, PythonClass self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached ReadAttributeFromObjectNode read,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached CallSlotDescrGet callGet) {
            // see type.c#type_get_doc()
            Object res = read.execute(self, T___DOC__);
            if (res == NO_VALUE) {
                return PNone.NONE;
            }
            Object resClass = getClassNode.execute(inliningTarget, res);
            TpSlots resSlots = getSlots.execute(inliningTarget, resClass);
            if (resSlots.tp_descr_get() != null) {
                return callGet.execute(frame, inliningTarget, resSlots.tp_descr_get(), res, NO_VALUE, self);
            }
            return res;
        }

        @Specialization
        static Object getDoc(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone value) {
            return ReadAttributeFromObjectNode.getUncached().execute(self, T___DOC__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "!isPythonBuiltinClass(self)"})
        static Object setDoc(PythonClass self, Object value) {
            self.setAttribute(T___DOC__, value);
            return NO_VALUE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "isKindOfBuiltinClass(self)"})
        static Object doc(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }

        @Specialization
        static Object doc(Object self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_DELETE_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }
    }

    @Builtin(name = J___MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(Object klass,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetMroNode getMroNode,
                        @Cached InlinedConditionProfile notInitialized) {
            if (notInitialized.profile(inliningTarget, klass instanceof PythonManagedClass && !((PythonManagedClass) klass).isMROInitialized())) {
                return PNone.NONE;
            }
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return PFactory.createTuple(PythonLanguage.get(inliningTarget), mro);
        }
    }

    @Builtin(name = J_MRO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTypeNode.execute(inliningTarget, klass)", limit = "1")
        static Object doit(Object klass,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetMroNode getMroNode) {
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return PFactory.createList(PythonLanguage.get(inliningTarget), Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doit(Object object,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T_MRO, "type", object);
        }
    }

    // type(object, bases, dict)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, needsFrame = true)
    @GenerateNodeFactory
    public abstract static class TypeNode extends PythonVarargsBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private TypeNodes.IsAcceptableBaseNode isAcceptableBaseNode;
        private final BranchProfile errorProfile = BranchProfile.create();

        public abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        @Override
        public final Object execute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            if (arguments.length == 3) {
                return execute(frame, self, arguments[0], arguments[1], arguments[2], keywords);
            } else {
                errorProfile.enter();
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "type.__new__", 3, arguments.length);
            }
        }

        @Specialization(guards = "isString(wName)")
        @SuppressWarnings("truffle-static-method")
        Object typeNew(VirtualFrame frame, Object cls, Object wName, PTuple bases, PDict namespaceOrig, PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached CallSlotTpNewNode callNew,
                        @Cached @Exclusive IsTypeNode isTypeNode,
                        @Cached PyObjectLookupAttr lookupMroEntriesNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached TypeNodes.CreateTypeNode createType,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            // Determine the proper metatype to deal with this
            TruffleString name = castStr.execute(inliningTarget, wName);
            Object metaclass = cls;
            Object winner = calculateMetaclass(frame, inliningTarget, metaclass, bases, getClassNode, isTypeNode, lookupMroEntriesNode, getObjectArrayNode);
            if (winner != metaclass) {
                TpSlot winnerNew = getSlots.execute(inliningTarget, winner).tp_new();
                if (winnerNew != SLOTS.tp_new()) {
                    // Pass it to the winner
                    return callNew.execute(frame, inliningTarget, winnerNew, winner, new Object[]{name, bases, namespaceOrig}, kwds);
                }
                metaclass = winner;
            }

            return createType.execute(frame, namespaceOrig, name, bases, metaclass, kwds);
        }

        @Fallback
        Object generic(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object name, Object bases, Object namespace, @SuppressWarnings("unused") PKeyword[] kwds) {
            if (!(bases instanceof PTuple)) {
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 2, "tuple", bases);
            } else if (!(namespace instanceof PDict)) {
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 3, "dict", bases);
            } else {
                throw CompilerDirectives.shouldNotReachHere("type fallback reached incorrectly");
            }
        }

        private Object calculateMetaclass(VirtualFrame frame, Node inliningTarget, Object cls, PTuple bases, GetClassNode getClassNode, IsTypeNode isTypeNode,
                        PyObjectLookupAttr lookupMroEntries, GetObjectArrayNode getObjectArrayNode) {
            Object winner = cls;
            for (Object base : getObjectArrayNode.execute(inliningTarget, bases)) {
                if (!isTypeNode.execute(inliningTarget, base) && lookupMroEntries.execute(frame, inliningTarget, base, T___MRO_ENTRIES__) != NO_VALUE) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.TYPE_DOESNT_SUPPORT_MRO_ENTRY_RESOLUTION);
                }
                if (!ensureIsAcceptableBaseNode().execute(base)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.TYPE_IS_NOT_ACCEPTABLE_BASE_TYPE, base);
                }
                Object typ = getClassNode.execute(inliningTarget, base);
                if (isSubType(winner, typ)) {
                    continue;
                } else if (isSubType(typ, winner)) {
                    winner = typ;
                    continue;
                }
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.METACLASS_CONFLICT);
            }
            return winner;
        }

        protected boolean isSubType(Object subclass, Object superclass) {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode.execute(subclass, superclass);
        }

        @NeverDefault
        public static TypeNode create() {
            return TypeNodeFactory.create();
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached TypeNode nextTypeNode,
                        @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached IsTypeNode isTypeNode) {
            if (!(name instanceof TruffleString || name instanceof PString)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 1", name);
            } else if (!(bases instanceof PTuple)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 2", bases);
            } else if (!(dict instanceof PDict)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 3", dict);
            } else if (!isTypeNode.execute(inliningTarget, cls)) {
                // TODO: this is actually allowed, deal with it
                throw raiseNode.raise(inliningTarget, NotImplementedError, ErrorMessages.CREATING_CLASS_NON_CLS_META_CLS);
            }
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
        }

        private TypeNodes.IsAcceptableBaseNode ensureIsAcceptableBaseNode() {
            if (isAcceptableBaseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isAcceptableBaseNode = insert(TypeNodes.IsAcceptableBaseNode.create());
            }
            return isAcceptableBaseNode;
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object init(@SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (arguments.length != 1 && arguments.length != 3) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type.__init__()", 1, 3);
            }
            if (arguments.length == 1 && kwds.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "type.__init__()");
            }
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached CreateInstanceNode createInstanceNode) {
            if (isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PythonClass, self)) {
                if (arguments.length == 1 && keywords.length == 0) {
                    return getClassNode.execute(inliningTarget, arguments[0]);
                } else if (arguments.length != 3) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
                }
            }
            return createInstanceNode.execute(frame, inliningTarget, self, arguments, keywords);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class CreateInstanceNode extends PNodeWithContext {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object self, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object type, Object[] arguments, PKeyword[] keywords,
                        @Cached InlinedConditionProfile builtinProfile,
                        @Cached GetCachedTpSlotsNode getTypeSlots,
                        @Cached GetCachedTpSlotsNode getInstanceSlots,
                        @Cached GetClassNode getInstanceClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallSlotTpNewNode callNew,
                        @Cached CallSlotTpInitNode callInit,
                        @Cached PRaiseNode raiseNode) {
            if (builtinProfile.profile(inliningTarget, type instanceof PythonBuiltinClass)) {
                // PythonBuiltinClassType should help the code after this to optimize better
                type = ((PythonBuiltinClass) type).getType();
            }
            TpSlots typeSlots = getTypeSlots.execute(inliningTarget, type);
            if (typeSlots.tp_new() == null) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_CREATE_N_INSTANCES, type);
            }
            Object newInstance = callNew.execute(frame, inliningTarget, typeSlots.tp_new(), type, arguments, keywords);
            Object newInstanceKlass = getInstanceClassNode.execute(inliningTarget, newInstance);
            if (isSubtypeNode.execute(newInstanceKlass, type)) {
                TpSlots instanceSlots = getInstanceSlots.execute(inliningTarget, newInstanceKlass);
                if (instanceSlots.tp_init() != null) {
                    callInit.execute(frame, inliningTarget, instanceSlots.tp_init(), newInstance, arguments, keywords);
                }
            }
            return newInstance;
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends GetAttrBuiltinNode {
        @Child private CallSlotDescrGet callSlotDescrGet;
        @Child private CallSlotDescrGet callSlotValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;

        /**
         * Keep in sync with {@link ObjectBuiltins.GetAttributeNode} and
         * {@link ThreadLocalBuiltins.GetAttributeNode} and
         * {@link MergedObjectTypeModuleGetAttributeNode}
         */
        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached GetObjectSlotsNode getValueSlotsNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached CastToTruffleStringChecked1Node castToString,
                        @Cached InlinedConditionProfile hasDescProfile,
                        @Cached InlinedConditionProfile hasDescrGetProfile,
                        @Cached InlinedConditionProfile hasValueProfile,
                        @Cached InlinedBranchProfile hasNonDescriptorValueProfile,
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
            Object value = readAttributeOfClass(object, key);
            if (hasValueProfile.profile(inliningTarget, value != PNone.NO_VALUE)) {
                var valueGet = getValueSlotsNode.execute(inliningTarget, value).tp_descr_get();
                if (valueGet == null) {
                    hasNonDescriptorValueProfile.enter(inliningTarget);
                    return value;
                } else {
                    return dispatchValueGet(frame, object, value, valueGet);
                }
            }

            if (hasDescr) {
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return dispatchDescrGet(frame, object, type, descr, get);
                }
            }

            throw raiseNode.raiseAttributeError(inliningTarget, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, object, key);
        }

        private Object readAttributeOfClass(Object object, TruffleString key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object dispatchDescrGet(VirtualFrame frame, Object object, Object type, Object descr, TpSlot getSlot) {
            if (callSlotDescrGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSlotDescrGet = insert(CallSlotDescrGet.create());
            }
            return callSlotDescrGet.executeCached(frame, getSlot, descr, object, type);
        }

        private Object dispatchValueGet(VirtualFrame frame, Object type, Object descr, TpSlot getSlot) {
            if (callSlotValueGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSlotValueGet = insert(CallSlotDescrGet.create());
            }
            // NO_VALUE 2nd argument indicates the descriptor was found on the target object itself
            // (or a base)
            return callSlotValueGet.executeCached(frame, getSlot, descr, PNone.NO_VALUE, type);
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization(guards = "!isImmutable(object)")
        static void set(VirtualFrame frame, Object object, Object keyObject, Object value,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringChecked0Node castKeyNode,
                        @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Cached WriteAttributeToObjectNode write) {
            TruffleString key = castKeyNode.cast(inliningTarget, keyObject, ATTR_NAME_MUST_BE_STRING);
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
        }

        @TruffleBoundary
        @Specialization(guards = "isImmutable(object)")
        void setBuiltin(Object object, Object key, Object value) {
            if (PythonContext.get(this).isInitialized()) {
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), object);
            } else {
                set(null, object, key, value, null, CastToTruffleStringChecked0Node.getUncached(), ObjectNodes.GenericSetAttrNode.getUncached(), WriteAttributeToObjectNode.getUncached());
            }
        }

        protected static boolean isImmutable(Object type) {
            // TODO should also check Py_TPFLAGS_IMMUTABLETYPE
            return type instanceof PythonBuiltinClass || type instanceof PythonBuiltinClassType;
        }
    }

    @Builtin(name = J___PREPARE__, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs,
                        @Bind PythonLanguage language) {
            return PFactory.createDict(language, new DynamicObjectStorage(language));
        }
    }

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object getBases(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            return PFactory.createTuple(language, getBaseClassesNode.execute(inliningTarget, self));
        }

        @Specialization
        static Object setBases(VirtualFrame frame, PythonClass cls, PTuple value,
                        @Bind Node inliningTarget,
                        @Cached GetObjectArrayNode getArray,
                        @Cached GetBaseClassNode getBase,
                        @Cached GetBestBaseClassNode getBestBase,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigment,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetMroNode getMroNode,
                        @Cached PRaiseNode raiseNode) {

            Object[] a = getArray.execute(inliningTarget, value);
            if (a.length == 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_ASSIGN_NON_EMPTY_TUPLE_TO_P, cls);
            }
            PythonAbstractClass[] baseClasses = new PythonAbstractClass[a.length];
            for (int i = 0; i < a.length; i++) {
                if (PGuards.isPythonClass(a[i])) {
                    if (isSubtypeNode.execute(a[i], cls) ||
                                    hasMRO(inliningTarget, getMroNode, a[i]) && typeIsSubtypeBaseChain(inliningTarget, a[i], cls, getBase, isSameTypeNode)) {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.BASES_ITEM_CAUSES_INHERITANCE_CYCLE);
                    }
                    if (a[i] instanceof PythonBuiltinClassType) {
                        baseClasses[i] = PythonContext.get(inliningTarget).lookupType((PythonBuiltinClassType) a[i]);
                    } else {
                        baseClasses[i] = (PythonAbstractClass) a[i];
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_TUPLE_OF_CLASSES_NOT_P, cls, "__bases__", a[i]);
                }
            }

            Object newBestBase = getBestBase.execute(inliningTarget, baseClasses);
            if (newBestBase == null) {
                return null;
            }

            Object oldBase = getBase.execute(inliningTarget, cls);
            checkCompatibleForAssigment.execute(frame, oldBase, newBestBase);

            cls.setBases(inliningTarget, newBestBase, baseClasses);
            TpSlots.updateAllSlots(cls);

            return PNone.NONE;
        }

        private static boolean hasMRO(Node inliningTarget, GetMroNode getMroNode, Object i) {
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, i);
            return mro != null && mro.length > 0;
        }

        private static boolean typeIsSubtypeBaseChain(Node inliningTarget, Object a, Object b, GetBaseClassNode getBaseNode, IsSameTypeNode isSameTypeNode) {
            Object base = a;
            do {
                if (isSameTypeNode.execute(inliningTarget, base, b)) {
                    return true;
                }
                base = getBaseNode.execute(inliningTarget, base);
            } while (base != null);

            return (isSameTypeNode.execute(inliningTarget, b, PythonBuiltinClassType.PythonObject));
        }

        @Specialization(guards = "!isPTuple(value)")
        static Object setObject(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_S_S_NOT_P, "tuple", GetNameNode.executeUncached(cls), "__bases__", value);
        }

        @Specialization
        static Object setBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___BASES__, cls);
        }

    }

    @Builtin(name = J___BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        static Object base(Object self,
                        @Bind Node inliningTarget,
                        @Cached GetBaseClassNode getBaseClassNode) {
            Object baseClass = getBaseClassNode.execute(inliningTarget, self);
            return baseClass != null ? baseClass : PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doType(PythonBuiltinClassType self,
                        @Bind PythonLanguage language,
                        @Shared @Cached GetDictIfExistsNode getDict) {
            return doManaged(getContext().lookupType(self), language, getDict);
        }

        @Specialization
        static Object doManaged(PythonManagedClass self,
                        @Bind PythonLanguage language,
                        @Shared @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                dict = PFactory.createDictFixedStorage(language, self, self.getMethodResolutionOrder());
                // The mapping is unmodifiable, so we don't have to assign it back
            }
            return PFactory.createMappingproxy(language, dict);
        }

        @Specialization
        static Object doNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadObjectNode getTpDictNode) {
            return getTpDictNode.readFromObj(self, CFields.PyTypeObject__tp_dict);
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GenericInstanceCheckNode genericInstanceCheckNode) {
            return genericInstanceCheckNode.execute(frame, inliningTarget, instance, cls);
        }
    }

    @Builtin(name = J___SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean check(VirtualFrame frame, Object cls, Object derived,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GenericSubclassCheckNode genericSubclassCheckNode) {
            return genericSubclassCheckNode.execute(frame, inliningTarget, derived, cls);
        }
    }

    @Builtin(name = J___SUBCLASSHOOK__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class SubclassHookNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object hook(VirtualFrame frame, Object cls, Object subclass) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___SUBCLASSES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SubclassesNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        static PList getSubclasses(Object cls,
                        @Bind Node inliningTarget) {
            PythonAbstractClass[] array = GetSubclassesAsArrayNode.executeUncached(cls);
            Object[] classes = new Object[array.length];
            PythonUtils.arraycopy(array, 0, classes, 0, array.length);
            return PFactory.createList(PythonLanguage.get(inliningTarget), classes);
        }
    }

    @GenerateNodeFactory
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CheckSetSpecialTypeAttrNode extends Node {
        abstract void execute(Node inliningTarget, Object type, Object value, TruffleString name);

        @Specialization
        static void check(Node inliningTarget, Object type, Object value, TruffleString name,
                        @Cached PRaiseNode raiseNode,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            if (PGuards.isKindOfBuiltinClass(type) || (getTypeFlagsNode.execute(type) & TypeFlags.IMMUTABLETYPE) != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, name, type);
            }
            if (value == DescriptorDeleteMarker.INSTANCE) {
                // Sic, it's not immutable, but CPython has this message
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_DELETE_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, name, type);
            }
            auditNode.audit(inliningTarget, "object.__setattr__", type, name, value);
        }
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, //
                    allowsDelete = true /* Delete handled by CheckSetSpecialTypeAttrNode */)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNameType(Object cls, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached GetNameNode getNameNode) {
            return getNameNode.execute(inliningTarget, cls);
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class SetNameInnerNode extends Node {
            abstract void execute(Node inliningTarget, Object type, TruffleString value);

            @Specialization
            static void set(PythonClass type, TruffleString value) {
                type.setName(value);
            }

            @Specialization
            static void set(PythonAbstractNativeObject type, TruffleString value,
                            @Bind PythonLanguage language,
                            @Cached(inline = false) CStructAccess.WritePointerNode writePointerNode,
                            @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObject,
                            @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                            @Cached(inline = false) TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
                value = switchEncodingNode.execute(value, TruffleString.Encoding.UTF_8);
                byte[] bytes = copyToByteArrayNode.execute(value, TruffleString.Encoding.UTF_8);
                PBytes bytesObject = PFactory.createBytes(language, bytes);
                writePointerNode.writeToObj(type, PyTypeObject__tp_name, PySequenceArrayWrapper.ensureNativeSequence(bytesObject));
                PString pString = PFactory.createString(language, value);
                pString.setUtf8Bytes(bytesObject);
                writeObject.writeToObject(type, PyHeapTypeObject__ht_name, pString);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(VirtualFrame frame, Object cls, Object value,
                        @Bind Node inliningTarget,
                        @Cached CheckSetSpecialTypeAttrNode check,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached SetNameInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            check.execute(inliningTarget, cls, value, T___NAME__);
            TruffleString string;
            try {
                string = castToTruffleStringNode.execute(inliningTarget, value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_P_S_NOT_P, "string", cls, T___NAME__, value);
            }
            if (indexOfCodePointNode.execute(string, 0, 0, codePointLengthNode.execute(string, TS_ENCODING), TS_ENCODING) >= 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }
            if (!isValidNode.execute(string, TS_ENCODING)) {
                throw constructAndRaiseNode.get(inliningTarget).raiseUnicodeEncodeError(frame, "utf-8", string, 0, string.codePointLengthUncached(TS_ENCODING), "can't encode classname");
            }
            innerNode.execute(inliningTarget, cls, string);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ModuleNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getModuleType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            TruffleString module = cls.getModuleName();
            return module == null ? T_BUILTINS : module;
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getModuleBuiltin(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return getModuleType(cls.getType(), value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Shared @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object module = readAttrNode.execute(cls, T___MODULE__);
            if (module == NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModule(PythonClass cls, Object value,
                        @Shared @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Shared @Cached ReadAttributeFromObjectNode readAttrNode,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            // see function 'typeobject.c: type_module'
            if ((getFlags.execute(cls) & TypeFlags.HEAPTYPE) != 0) {
                Object module = readAttrNode.execute(cls, T___MODULE__);
                if (module == NO_VALUE) {
                    throw raiseNode.raise(inliningTarget, AttributeError);
                }
                return module;
            } else {
                // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
                TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
                int len = codePointLengthNode.execute(tpName, TS_ENCODING);
                int firstDot = indexOfCodePointNode.execute(tpName, '.', 0, len, TS_ENCODING);
                if (firstDot < 0) {
                    return T_BUILTINS;
                }
                return substringNode.execute(tpName, 0, firstDot, TS_ENCODING, true);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setNative(PythonAbstractNativeObject cls, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Shared @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            long flags = getFlags.execute(cls);
            if ((flags & TypeFlags.HEAPTYPE) == 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_N_S, cls, T___MODULE__);
            }
            writeAttrNode.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModuleType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModuleBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, //
                    allowsDelete = true /* Delete handled by CheckSetSpecialTypeAttrNode */)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualName();
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached GetTypeFlagsNode getTypeFlagsNode,
                        @Cached CStructAccess.ReadObjectNode getHtName,
                        @Cached GetNameNode getNameNode) {
            if ((getTypeFlagsNode.execute(cls) & TypeFlags.HEAPTYPE) != 0) {
                return getHtName.readFromObj(cls, PyHeapTypeObject__ht_qualname);
            } else {
                return getNameNode.execute(inliningTarget, cls);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class SetQualNameInnerNode extends Node {
            abstract void execute(Node inliningTarget, Object type, TruffleString value);

            @Specialization
            static void set(PythonClass type, TruffleString value) {
                type.setQualName(value);
            }

            @Specialization
            static void set(PythonAbstractNativeObject type, TruffleString value,
                            @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObject) {
                writeObject.writeToObject(type, PyHeapTypeObject__ht_qualname, value);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setName(Object cls, Object value,
                        @Bind Node inliningTarget,
                        @Cached CheckSetSpecialTypeAttrNode check,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached SetQualNameInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            check.execute(inliningTarget, cls, value, T___QUALNAME__);
            TruffleString stringValue;
            try {
                stringValue = castToStringNode.execute(inliningTarget, value);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_STR_TO_QUALNAME, cls, value);
            }
            innerNode.execute(inliningTarget, cls, stringValue);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICTOFFSET__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictoffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getDictoffsetType(Object cls,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetDictOffsetNode getDictOffsetNode) {
            return getDictOffsetNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___ITEMSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ItemsizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long getItemsizeType(Object cls,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetItemSizeNode getItemsizeNode) {
            return getItemsizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___BASICSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BasicsizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getBasicsizeType(Object cls,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetBasicSizeNode getBasicSizeNode) {
            return getBasicSizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___WEAKREFOFFSET__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class WeakrefOffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(Object cls,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetWeakListOffsetNode getWeakListOffsetNode) {
            return getWeakListOffsetNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___FLAGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object self,
                        @Bind Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetTypeFlagsNode getTypeFlagsNode,
                        @Cached PRaiseNode raiseNode) {
            if (PGuards.isClass(inliningTarget, self, isTypeNode)) {
                return getTypeFlagsNode.execute(self);
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.DESC_FLAG_FOR_TYPE_DOESNT_APPLY_TO_OBJ, self);
        }
    }

    @Builtin(name = J___ABSTRACTMETHODS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AbstractMethodsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(Object self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            // Avoid returning this descriptor
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                Object result = readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__);
                if (result != NO_VALUE) {
                    return result;
                }
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, self, T___ABSTRACTMETHODS__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object set(VirtualFrame frame, PythonClass self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, value);
                self.setAbstractClass(isTrueNode.execute(frame, value));
                return PNone.NONE;
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object delete(PythonClass self, @SuppressWarnings("unused") DescriptorDeleteMarker value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                if (readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__) != NO_VALUE) {
                    writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, NO_VALUE);
                    self.setAbstractClass(false);
                    return PNone.NONE;
                }
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object set(Object self, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for type objects\n\n\tThis includes all attributes of klass and all of the base\n\tclasses recursively.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Override
        public abstract PList execute(VirtualFrame frame, Object klass);

        @Specialization
        static PList dir(VirtualFrame frame, Object klass,
                        @Bind Node inliningTarget,
                        @Cached ConstructListNode constructListNode,
                        @Cached("createFor($node)") IndirectCallData indirectCallData) {
            PSet names = PFactory.createSet(PythonLanguage.get(inliningTarget));
            Object state = IndirectCallContext.enter(frame, inliningTarget, indirectCallData);
            try {
                dir(names, klass);
            } finally {
                IndirectCallContext.exit(frame, inliningTarget, indirectCallData, state);
            }
            return constructListNode.execute(frame, names);
        }

        @TruffleBoundary
        public static void dir(PSet names, Object klass) {
            Object ns = PyObjectLookupAttr.executeUncached(klass, T___DICT__);
            UpdateSingleNode updateSingleNode = UpdateSingleNode.getUncached();
            if (ns != NO_VALUE) {
                updateSingleNode.execute(null, names, ns);
            }
            Object basesAttr = PyObjectLookupAttr.executeUncached(klass, T___BASES__);
            if (basesAttr instanceof PTuple) {
                Object[] bases = ToArrayNode.executeUncached(((PTuple) basesAttr).getSequenceStorage());
                for (Object cls : bases) {
                    // Note that since we are only interested in the keys, the order
                    // we merge classes is unimportant
                    dir(names, cls);
                }
            }
        }

        @NeverDefault
        protected GetFixedAttributeNode createGetAttrNode() {
            return GetFixedAttributeNode.create(T___BASES__);
        }

        @NeverDefault
        public static DirNode create() {
            return TypeBuiltinsFactory.DirNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AnnotationsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile createDict,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == NO_VALUE) {
                createDict.enter(inliningTarget);
                annotations = PFactory.createDict(PythonLanguage.get(inliningTarget));
                try {
                    write.execute(self, T___ANNOTATIONS__, annotations);
                } catch (PException e) {
                    throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, T___ANNOTATIONS__);
                }
            }
            return annotations;
        }

        @Specialization(guards = "isDeleteMarker(value)")
        static Object delete(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            try {
                write.execute(self, T___ANNOTATIONS__, NO_VALUE);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            if (annotations == NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, new Object[]{T___ANNOTATIONS__});
            }
            return PNone.NONE;
        }

        @Fallback
        static Object set(Object self, Object value,
                        @Bind Node inliningTarget,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                write.execute(self, T___ANNOTATIONS__, value);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TYPE_PARAMS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class TypeParamsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached ReadAttributeFromObjectNode read,
                        @Cached IsBuiltinClassExactProfile isBuiltinClassProfile) {
            if (isBuiltinClassProfile.profileClass(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                return PFactory.createEmptyTuple(language);
            }
            Object typeParams = read.execute(self, T___TYPE_PARAMS__);
            if (typeParams == NO_VALUE) {
                return PFactory.createEmptyTuple(language);
            }
            return typeParams;
        }

        @Fallback
        static Object set(Object self, Object value,
                        @Bind Node inliningTarget,
                        @Cached WriteAttributeToObjectNode write,
                        @Cached PRaiseNode raiseNode) {
            try {
                write.execute(self, T___TYPE_PARAMS__, value);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___TYPE_PARAMS__, self);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        private static final String SIGNATURE_END_MARKER = ")\n--\n\n";
        private static final int SIGNATURE_END_MARKER_LENGTH = 6;

        @Specialization
        static Object signature(PythonBuiltinClass type) {
            return signature(type.getType());
        }

        @Specialization
        static Object signature(PythonBuiltinClassType type) {
            if (type.getDoc() == null) {
                return PNone.NONE;
            }
            return signature(type.getName(), type.getDoc());
        }

        @Fallback
        static Object noDocs(@SuppressWarnings("unused") Object type) {
            return PNone.NONE;
        }

        @TruffleBoundary
        static Object signature(TruffleString name, TruffleString internalDoc) {
            String n = name.toJavaStringUncached();
            String doc = internalDoc.toJavaStringUncached();
            int start = findSignature(n, doc);
            if (start < 0) {
                return PNone.NONE;
            }

            int end = -1;
            if (start > 0) {
                end = skipSignature(doc, start);
            }

            if (end <= 0) {
                return PNone.NONE;
            }

            /* back "end" up until it points just past the final ')' */
            end -= SIGNATURE_END_MARKER_LENGTH - 1;
            assert ((end - start) >= 2); /* should be "()" at least */
            assert (doc.charAt(end - 1) == ')');
            assert (doc.charAt(end) == '\n');
            return toTruffleStringUncached(doc.substring(start, end));

        }

        /*
         * finds the beginning of the docstring's introspection signature. if present, returns a
         * pointer pointing to the first '('. otherwise returns NULL.
         *
         * doesn't guarantee that the signature is valid, only that it has a valid prefix. (the
         * signature must also pass skip_signature.)
         */
        @TruffleBoundary
        static int findSignature(String n, String doc) {
            String name = n;
            /* for dotted names like classes, only use the last component */
            int dot = n.indexOf('.');
            if (dot != -1) {
                name = name.substring(dot + 1);
            }

            int length = name.length();
            if (!doc.startsWith(name)) {
                return -1;
            }

            if (doc.charAt(length) != '(') {
                return -1;
            }
            return length;
        }

        /*
         * skips past the end of the docstring's introspection signature. (assumes doc starts with a
         * valid signature prefix.)
         */
        @TruffleBoundary
        static int skipSignature(String doc, int start) {
            int end = doc.indexOf(SIGNATURE_END_MARKER, start);
            return end == -1 ? end : (end + SIGNATURE_END_MARKER_LENGTH);
        }

    }
}
