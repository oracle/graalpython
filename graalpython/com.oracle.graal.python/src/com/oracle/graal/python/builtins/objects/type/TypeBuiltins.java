/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;
import static com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.InitNode.overridesBuiltinMethod;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BUILTINS;
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___VECTORCALLOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSHOOK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_UPDATE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructorsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeHelperNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
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
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedSlotNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectGetBasesNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectIsSubclassNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
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
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonClass)
public final class TypeBuiltins extends PythonBuiltins {

    public static final HiddenKey TYPE_DICTOFFSET = new HiddenKey(J___DICTOFFSET__);
    public static final HiddenKey TYPE_WEAKLISTOFFSET = new HiddenKey(J___WEAKLISTOFFSET__);
    public static final HiddenKey TYPE_ITEMSIZE = new HiddenKey(J___ITEMSIZE__);
    public static final HiddenKey TYPE_BASICSIZE = new HiddenKey(J___BASICSIZE__);
    public static final HiddenKey TYPE_ALLOC = new HiddenKey(J___ALLOC__);
    public static final HiddenKey TYPE_DEALLOC = new HiddenKey("__dealloc__");
    public static final HiddenKey TYPE_DEL = new HiddenKey("__del__");
    public static final HiddenKey TYPE_FREE = new HiddenKey("__free__");
    public static final HiddenKey TYPE_AS_BUFFER = new HiddenKey("__tp_as_buffer__");
    public static final HiddenKey TYPE_FLAGS = new HiddenKey(J___FLAGS__);
    public static final HiddenKey TYPE_VECTORCALL_OFFSET = new HiddenKey(J___VECTORCALLOFFSET__);
    public static final HiddenKey TYPE_GETBUFFER = new HiddenKey("__getbuffer__");
    public static final HiddenKey TYPE_RELEASEBUFFER = new HiddenKey("__releasebuffer__");
    private static final HiddenKey TYPE_DOC = new HiddenKey(J___DOC__);

    public static final HashMap<String, HiddenKey> INITIAL_HIDDEN_TYPE_KEYS = new HashMap<>();

    static {
        for (HiddenKey key : new HiddenKey[]{TYPE_DICTOFFSET, TYPE_ITEMSIZE, TYPE_BASICSIZE, TYPE_ALLOC, TYPE_DEALLOC, TYPE_DEL, TYPE_FREE, TYPE_FLAGS, TYPE_VECTORCALL_OFFSET, TYPE_DOC}) {
            INITIAL_HIDDEN_TYPE_KEYS.put(key.getName(), key);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(TYPE_DOC, //
                        "type(object_or_name, bases, dict)\n" + //
                                        "type(object) -> the object's type\n" + //
                                        "type(name, bases, dict) -> a new type");
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T___MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(T___QUALNAME__)") GetFixedAttributeNode readQualNameNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object moduleNameObj = readModuleNode.executeObject(frame, self);
            Object qualNameObj = readQualNameNode.executeObject(frame, self);
            TruffleString moduleName = null;
            if (moduleNameObj != PNone.NO_VALUE) {
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
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class DocNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object getDoc(PythonBuiltinClassType self, @SuppressWarnings("unused") PNone value) {
            return getDoc(getContext().lookupType(self), value);
        }

        @Specialization(guards = "isNoValue(value)")
        @TruffleBoundary
        static Object getDoc(PythonBuiltinClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            if (InlineIsBuiltinClassProfile.profileClassSlowPath(self, PythonBuiltinClassType.PythonClass)) {
                return self.getAttribute(TYPE_DOC);
            } else {
                return self.getAttribute(T___DOC__);
            }
        }

        @Specialization(guards = {"isNoValue(value)", "!isPythonBuiltinClass(self)"})
        static Object getDoc(VirtualFrame frame, PythonClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            Object res = self.getAttribute(T___DOC__);
            Object resClass = GetClassNode.executeUncached(res);
            Object get = LookupAttributeInMRONode.Dynamic.getUncached().execute(resClass, T___GET__);
            if (PGuards.isCallable(get)) {
                return CallTernaryMethodNode.getUncached().execute(frame, get, res, PNone.NONE, self);
            }
            return res;
        }

        @Specialization
        static Object getDoc(PythonNativeClass self, @SuppressWarnings("unused") PNone value) {
            return ReadAttributeFromObjectNode.getUncachedForceType().execute(self, T___DOC__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "!isPythonBuiltinClass(self)"})
        static Object setDoc(PythonClass self, Object value) {
            self.setAttribute(T___DOC__, value);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)", "isKindOfBuiltinClass(self)"})
        Object doc(Object self, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }

        @Specialization
        Object doc(Object self, @SuppressWarnings("unused") DescriptorDeleteMarker marker) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_DELETE_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___DOC__, self);
        }
    }

    @Builtin(name = J___MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonBuiltinNode {
        @Specialization
        static Object doit(Object klass,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetMroNode getMroNode,
                        @Cached InlinedConditionProfile notInitialized,
                        @Cached PythonObjectFactory factory) {
            if (notInitialized.profile(inliningTarget, klass instanceof PythonManagedClass && !((PythonManagedClass) klass).isMROInitialized())) {
                return PNone.NONE;
            }
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return factory.createTuple(mro);
        }
    }

    @Builtin(name = J_MRO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTypeNode.execute(inliningTarget, klass)", limit = "1")
        static Object doit(Object klass,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetMroNode getMroNode,
                        @Cached PythonObjectFactory factory) {
            PythonAbstractClass[] mro = getMroNode.execute(inliningTarget, klass);
            return factory.createList(Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doit(Object object) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T_MRO, "type", object);
        }
    }

    @Builtin(name = J___INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.executeCached(arguments), keywords);
        }

        @Specialization
        Object init(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] kwds) {
            if (arguments.length != 1 && arguments.length != 3) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type.__init__()", 1, 3);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child CallNodeHelper callNodeHelper;

        @NeverDefault
        public static CallNode create() {
            return CallNodeFactory.create();
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        public CallNodeHelper getCallNodeHelper() {
            if (callNodeHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNodeHelper = insert(CallNodeHelperNodeGen.create());
            }
            return callNodeHelper;
        }

        @Specialization(guards = "isNoValue(self)")
        Object selfInArgs(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNodeHelper callNodeHelper,
                        @Cached SplitArgsNode splitArgsNode,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached GetClassNode getClassNode) {
            if (isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PythonClass, arguments[0])) {
                if (arguments.length == 2 && keywords.length == 0) {
                    return getClassNode.execute(inliningTarget, arguments[1]);
                } else if (arguments.length != 4) {
                    throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
                }
            }
            return callNodeHelper.execute(frame, arguments[0], splitArgsNode.execute(inliningTarget, arguments), keywords);
        }

        @Fallback
        Object selfSeparate(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached GetClassNode getClassNode) {
            if (isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PythonClass, self)) {
                if (arguments.length == 1 && keywords.length == 0) {
                    return getClassNode.execute(inliningTarget, arguments[0]);
                } else if (arguments.length != 3) {
                    throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
                }
            }
            return getCallNodeHelper().execute(frame, self, arguments, keywords);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class BindNew extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type);

        @Specialization
        static Object doBuiltinMethod(PBuiltinMethod descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Specialization
        static Object doBuiltinDescriptor(BuiltinMethodDescriptor descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Specialization
        static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Fallback
        static Object doBind(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Get", inline = false) LookupCallableSlotInMRONode lookupGet,
                        @Cached(inline = false) CallTernaryMethodNode callGet) {
            Object getMethod = lookupGet.execute(getClassNode.execute(inliningTarget, descriptor));
            if (getMethod != PNone.NO_VALUE) {
                return callGet.execute(frame, getMethod, descriptor, PNone.NONE, type);
            }
            return descriptor;
        }
    }

    @ReportPolymorphism
    protected abstract static class CallNodeHelper extends PNodeWithContext {
        @Child private CallVarargsMethodNode dispatchNew = CallVarargsMethodNode.create();
        @Child private LookupCallableSlotInMRONode lookupNew = LookupCallableSlotInMRONode.create(SpecialMethodSlot.New);
        @Child private CallVarargsMethodNode dispatchInit;
        @Child private LookupSpecialMethodSlotNode lookupInit;
        @Child private IsSubtypeNode isSubTypeNode;
        @Child private TypeNodes.GetNameNode getNameNode;

        abstract Object execute(VirtualFrame frame, Object self, Object[] args, PKeyword[] keywords);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf"})
        protected Object doIt0BuiltinSingle(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached("self") PythonBuiltinClass cachedSelf,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PythonBuiltinClassType type = cachedSelf.getType();
            return op(frame, inliningTarget, type, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf", "isPythonClass(cachedSelf)",
                        "!isPythonBuiltinClass(cachedSelf)"})
        protected Object doIt0User(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(value = "self", weak = true) Object cachedSelf,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, cachedSelf, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self.getType() == cachedType"})
        protected Object doIt0BuiltinMulti(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached("self.getType()") PythonBuiltinClassType cachedType,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, cachedType, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedType"})
        protected Object doIt0BuiltinType(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached("self") PythonBuiltinClassType cachedType,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, cachedType, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(replaces = {"doIt0BuiltinSingle", "doIt0BuiltinMulti"})
        protected Object doItIndirect0Builtin(VirtualFrame frame, PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PythonBuiltinClassType type = self.getType();
            return op(frame, inliningTarget, type, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(replaces = "doIt0BuiltinType")
        protected Object doItIndirect0BuiltinType(VirtualFrame frame, PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, self, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(replaces = {"doIt0User"}, guards = "!isPythonBuiltinClass(self)")
        protected Object doItIndirect0User(VirtualFrame frame, PythonAbstractClass self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, self, arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        /* self is native */
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf"})
        protected Object doIt1(VirtualFrame frame, @SuppressWarnings("unused") PythonNativeObject self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached("self") PythonNativeObject cachedSelf,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, PythonNativeClass.cast(cachedSelf), arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        @Specialization(replaces = "doIt1")
        protected Object doItIndirect1(VirtualFrame frame, PythonNativeObject self, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getInstanceClassNode,
                        @Shared @Cached InlinedConditionProfile hasNew,
                        @Shared @Cached InlinedConditionProfile hasInit,
                        @Shared @Cached InlinedConditionProfile gotInitResult,
                        @Shared @Cached BindNew bindNew,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return op(frame, inliningTarget, PythonNativeClass.cast(self), arguments, keywords, getInstanceClassNode, hasNew, hasInit, gotInitResult, bindNew, raiseNode);
        }

        private Object op(VirtualFrame frame, Node inliningTarget, Object self, Object[] arguments, PKeyword[] keywords, GetClassNode getInstanceClassNode, InlinedConditionProfile hasNew,
                        InlinedConditionProfile hasInit, InlinedConditionProfile gotInitResult, BindNew bindNew, PRaiseNode.Lazy raiseNode) {
            Object newMethod = lookupNew.execute(self);
            if (hasNew.profile(inliningTarget, newMethod != PNone.NO_VALUE)) {
                Object[] newArgs = PythonUtils.prependArgument(self, arguments);
                Object newInstance = dispatchNew.execute(frame, bindNew.execute(frame, inliningTarget, newMethod, self), newArgs, keywords);
                callInit(inliningTarget, newInstance, self, frame, arguments, keywords, getInstanceClassNode, hasInit, gotInitResult, raiseNode);
                return newInstance;
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, getTypeName(self));
            }
        }

        private void callInit(Node inliningTarget, Object newInstance, Object self, VirtualFrame frame, Object[] arguments, PKeyword[] keywords, GetClassNode getInstanceClassNode,
                        InlinedConditionProfile hasInit, InlinedConditionProfile gotInitResult, PRaiseNode.Lazy raiseNode) {
            Object newInstanceKlass = getInstanceClassNode.execute(inliningTarget, newInstance);
            if (isSubType(newInstanceKlass, self)) {
                Object initMethod = getInitNode().execute(frame, newInstanceKlass, newInstance);
                if (hasInit.profile(inliningTarget, initMethod != PNone.NO_VALUE)) {
                    Object[] initArgs = PythonUtils.prependArgument(newInstance, arguments);
                    Object initResult = getDispatchNode().execute(frame, initMethod, initArgs, keywords);
                    if (gotInitResult.profile(inliningTarget, initResult != PNone.NONE && initResult != PNone.NO_VALUE)) {
                        throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.SHOULD_RETURN_NONE, "__init__()");
                    }
                }
            }
        }

        private LookupSpecialMethodSlotNode getInitNode() {
            if (lookupInit == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupInit = insert(LookupSpecialMethodSlotNode.create(SpecialMethodSlot.Init));
            }
            return lookupInit;
        }

        private CallVarargsMethodNode getDispatchNode() {
            if (dispatchInit == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatchInit = insert(CallVarargsMethodNode.create());
            }
            return dispatchInit;
        }

        private boolean isSubType(Object left, Object right) {
            if (isSubTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubTypeNode = insert(IsSubtypeNode.create());
            }
            return isSubTypeNode.execute(left, right);
        }

        private TruffleString getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.executeCached(clazz);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        @Child private LookupInheritedSlotNode valueGetLookup;
        @Child private LookupCallableSlotInMRONode lookupGetNode;
        @Child private LookupCallableSlotInMRONode lookupSetNode;
        @Child private LookupCallableSlotInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode invokeGet;
        @Child private CallTernaryMethodNode invokeValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetClassNode getDescClassNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached CastToTruffleStringNode castToString,
                        @Cached InlinedBranchProfile hasDescProfile,
                        @Cached InlinedBranchProfile isDescProfile,
                        @Cached InlinedBranchProfile hasValueProfile,
                        @Cached InlinedBranchProfile errorProfile) {
            TruffleString key;
            try {
                key = castToString.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = lookup.execute(type, key);
            Object get = null;
            if (descr != PNone.NO_VALUE) {
                // acts as a branch profile
                Object dataDescClass = getDescClassNode.execute(inliningTarget, descr);
                get = lookupGet(dataDescClass);
                if (PGuards.isCallableOrDescriptor(get)) {
                    Object delete = PNone.NO_VALUE;
                    Object set = lookupSet(dataDescClass);
                    if (set == PNone.NO_VALUE) {
                        delete = lookupDelete(dataDescClass);
                    }
                    if (set != PNone.NO_VALUE || delete != PNone.NO_VALUE) {
                        isDescProfile.enter(inliningTarget);
                        // Only override if __get__ is defined, too, for compatibility with CPython.
                        if (invokeGet == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            invokeGet = insert(CallTernaryMethodNode.create());
                        }
                        return invokeGet.execute(frame, get, descr, object, type);
                    }
                }
            }
            Object value = readAttribute(object, key);
            if (value != PNone.NO_VALUE) {
                hasValueProfile.enter(inliningTarget);
                Object valueGet = lookupValueGet(value);
                if (valueGet == PNone.NO_VALUE) {
                    return value;
                } else if (PGuards.isCallableOrDescriptor(valueGet)) {
                    if (invokeValueGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeValueGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeValueGet.execute(frame, valueGet, value, PNone.NONE, object);
                }
            }
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter(inliningTarget);
                if (get == PNone.NO_VALUE) {
                    return descr;
                } else if (PGuards.isCallableOrDescriptor(get)) {
                    if (invokeGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeGet.execute(frame, get, descr, object, type);
                }
            }
            errorProfile.enter(inliningTarget);
            throw raise(AttributeError, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, object, key);
        }

        private Object readAttribute(Object object, Object key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object lookupDelete(Object dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Delete));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(Object dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Set));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        private Object lookupGet(Object dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Get));
            }
            return lookupGetNode.execute(dataDescClass);
        }

        private Object lookupValueGet(Object value) {
            if (valueGetLookup == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                valueGetLookup = insert(LookupInheritedSlotNode.create(SpecialMethodSlot.Get));
            }
            return valueGetLookup.execute(value);
        }
    }

    @Builtin(name = J___SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "!isImmutable(object)")
        static Object set(VirtualFrame frame, Object object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Cached("createForceType()") WriteAttributeToObjectNode write) {
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
            return PNone.NONE;
        }

        @Specialization(guards = "isImmutable(object)")
        @TruffleBoundary
        Object setBuiltin(Object object, Object key, Object value) {
            if (PythonContext.get(this).isInitialized()) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(key), object);
            } else {
                set(null, object, key, value, null, ObjectNodes.GenericSetAttrNode.getUncached(), WriteAttributeToObjectNode.getUncached(true));
                return PNone.NONE;
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
                        @Cached PythonObjectFactory factory) {
            return factory.createDict(new DynamicObjectStorage(PythonLanguage.get(this)));
        }
    }

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object getBases(Object self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetBaseClassesNode getBaseClassesNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createTuple(getBaseClassesNode.execute(inliningTarget, self));
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object setBases(VirtualFrame frame, PythonClass cls, PTuple value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNameNode getName,
                        @Cached GetObjectArrayNode getArray,
                        @Cached GetBaseClassNode getBase,
                        @Cached GetBestBaseClassNode getBestBase,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigment,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetMroNode getMroNode) {

            Object[] a = getArray.execute(inliningTarget, value);
            if (a.length == 0) {
                throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_NON_EMPTY_TUPLE_TO_P, cls);
            }
            PythonAbstractClass[] baseClasses = new PythonAbstractClass[a.length];
            for (int i = 0; i < a.length; i++) {
                if (PGuards.isPythonClass(a[i])) {
                    if (isSubtypeNode.execute(frame, a[i], cls) ||
                                    hasMRO(inliningTarget, getMroNode, a[i]) && typeIsSubtypeBaseChain(inliningTarget, a[i], cls, getBase, isSameTypeNode)) {
                        throw raise(TypeError, ErrorMessages.BASES_ITEM_CAUSES_INHERITANCE_CYCLE);
                    }
                    if (a[i] instanceof PythonBuiltinClassType) {
                        baseClasses[i] = getContext().lookupType((PythonBuiltinClassType) a[i]);
                    } else {
                        baseClasses[i] = (PythonAbstractClass) a[i];
                    }
                } else {
                    throw raise(TypeError, ErrorMessages.MUST_BE_TUPLE_OF_CLASSES_NOT_P, getName.execute(inliningTarget, cls), "__bases__", a[i]);
                }
            }

            Object newBestBase = getBestBase.execute(inliningTarget, baseClasses);
            if (newBestBase == null) {
                return null;
            }

            Object oldBase = getBase.execute(inliningTarget, cls);
            checkCompatibleForAssigment.execute(frame, oldBase, newBestBase);

            cls.setBases(newBestBase, baseClasses);
            SpecialMethodSlot.reinitializeSpecialMethodSlots(cls, getLanguage());

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
        Object setObject(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_S_S_NOT_P, "tuple", GetNameNode.executeUncached(cls), "__bases__", value);
        }

        @Specialization
        Object setBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___BASES__, cls);
        }

    }

    @Builtin(name = J___BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        static Object base(Object self,
                        @Bind("this") Node inliningTarget,
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
                        @Shared @Cached GetDictIfExistsNode getDict,
                        @Shared @Cached PythonObjectFactory factory) {
            return doManaged(getContext().lookupType(self), getDict, factory);
        }

        @Specialization
        static Object doManaged(PythonManagedClass self,
                        @Shared @Cached GetDictIfExistsNode getDict,
                        @Shared @Cached PythonObjectFactory factory) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                dict = factory.createDictFixedStorage(self, self.getMethodResolutionOrder());
                // The mapping is unmodifiable, so we don't have to assign it back
            }
            return factory.createMappingproxy(dict);
        }

        @Specialization
        static Object doNative(PythonNativeClass self,
                        @Cached CStructAccess.ReadObjectNode getTpDictNode) {
            return getTpDictNode.readFromObj(self, CFields.PyTypeObject__tp_dict);
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode getAttributeNode;

        public abstract boolean executeWith(VirtualFrame frame, Object cls, Object instance);

        public LookupAndCallBinaryNode getGetAttributeNode() {
            if (getAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttributeNode = insert(LookupAndCallBinaryNode.create(SpecialMethodSlot.GetAttribute));
            }
            return getAttributeNode;
        }

        private PythonObject getInstanceClassAttr(VirtualFrame frame, Object instance) {
            Object classAttr = getGetAttributeNode().executeObject(frame, instance, T___CLASS__);
            if (classAttr instanceof PythonObject) {
                return (PythonObject) classAttr;
            }
            return null;
        }

        @Specialization(guards = "isTypeNode.execute(inliningTarget, cls)", limit = "1")
        @SuppressWarnings("truffle-static-method")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(frame, getClassNode.execute(inliningTarget, instance), cls)) {
                return true;
            }

            Object instanceClass = getGetAttributeNode().executeObject(frame, instance, T___CLASS__);
            return PGuards.isManagedClass(instanceClass) && isSubtypeNode.execute(frame, instanceClass, cls);
        }

        @Fallback
        @SuppressWarnings("truffle-static-method")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile typeErrorProfile,
                        @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                        @Cached AbstractObjectGetBasesNode getBasesNode) {
            if (typeErrorProfile.profile(inliningTarget, getBasesNode.execute(frame, cls) == null)) {
                throw raise(TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_TYPE, instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(frame, instance);
            return instanceClass != null && abstractIsSubclassNode.execute(frame, instanceClass, cls);
        }
    }

    @Builtin(name = J___SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private GetFixedAttributeNode getBasesAttrNode;
        @Child private ObjectNodes.FastIsTupleSubClassNode isTupleSubClassNode;

        @Specialization(guards = {"!isNativeClass(cls)", "!isNativeClass(derived)"})
        boolean doManagedManaged(VirtualFrame frame, Object cls, Object derived,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode) {
            return isSameType(inliningTarget, cls, derived, isSameTypeNode) || isSubtypeNode.execute(frame, derived, cls);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        boolean doObjectObject(VirtualFrame frame, Object cls, Object derived,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsBuiltinObjectProfile isAttrErrorProfile,
                        @Cached TypeNodes.IsTypeNode isClsTypeNode,
                        @Cached TypeNodes.IsTypeNode isDerivedTypeNode) {
            if (isSameType(inliningTarget, cls, derived, isSameTypeNode)) {
                return true;
            }

            // no profiles required because IsTypeNode profiles already
            if (isClsTypeNode.execute(inliningTarget, cls) && isDerivedTypeNode.execute(inliningTarget, derived)) {
                return isSubtypeNode.execute(frame, derived, cls);
            }
            if (!checkClass(frame, inliningTarget, derived, isAttrErrorProfile)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
            }
            if (!checkClass(frame, inliningTarget, cls, isAttrErrorProfile)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ISSUBCLASS_MUST_BE_CLASS_OR_TUPLE);
            }
            return false;
        }

        // checks if object has '__bases__' (see CPython 'abstract.c' function
        // 'recursive_issubclass')
        private boolean checkClass(VirtualFrame frame, Node inliningTarget, Object obj, IsBuiltinObjectProfile isAttrErrorProfile) {
            if (getBasesAttrNode == null || isTupleSubClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBasesAttrNode = insert(GetFixedAttributeNode.create(T___BASES__));
                isTupleSubClassNode = insert(ObjectNodes.FastIsTupleSubClassNode.create());
            }
            Object basesObj;
            try {
                basesObj = getBasesAttrNode.executeObject(frame, obj);
            } catch (PException e) {
                e.expectAttributeError(inliningTarget, isAttrErrorProfile);
                return false;
            }
            return isTupleSubClassNode.executeCached(frame, basesObj);
        }

        protected static boolean isSameType(Node inliningTarget, Object a, Object b, IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(inliningTarget, a, b);
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

        @Specialization
        static PList getSubclasses(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = true) GetSubclassesAsArrayNode getSubclassesNode,
                        @Cached PythonObjectFactory factory) {
            // TODO: missing: keep track of subclasses
            PythonAbstractClass[] array = getSubclassesNode.execute(inliningTarget, cls);
            Object[] classes = new Object[array.length];
            PythonUtils.arraycopy(array, 0, classes, 0, array.length);
            return factory.createList(classes);
        }
    }

    @GenerateNodeFactory
    @TypeSystemReference(PythonTypes.class)
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = J___NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNameType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNameBuiltin(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        @SuppressWarnings("truffle-static-method")
        Object setName(VirtualFrame frame, PythonClass cls, Object value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode) {
            try {
                TruffleString string = castToTruffleStringNode.execute(inliningTarget, value);
                if (indexOfCodePointNode.execute(string, 0, 0, codePointLengthNode.execute(string, TS_ENCODING), TS_ENCODING) >= 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
                }
                if (!isValidNode.execute(string, TS_ENCODING)) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseUnicodeEncodeError(frame, "utf-8", string, 0, string.codePointLengthUncached(TS_ENCODING), "can't encode classname");
                }
                cls.setName(string);
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_P_S_NOT_P, "string", cls, T___NAME__, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Shared("cpLen") @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared("indexOf") @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
            int nameLen = codePointLengthNode.execute(tpName, TS_ENCODING);
            int firstDot = indexOfCodePointNode.execute(tpName, '.', 0, nameLen, TS_ENCODING);
            if (firstDot < 0) {
                return tpName;
            }
            return substringNode.execute(tpName, firstDot + 1, nameLen - firstDot - 1, TS_ENCODING, true);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object getModule(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
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
        Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            Object module = readAttrNode.execute(cls, T___MODULE__);
            if (module == PNone.NO_VALUE) {
                throw raise(AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModule(PythonClass cls, Object value,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        @SuppressWarnings("truffle-static-method")
        Object getModule(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttr,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // see function 'typeobject.c: type_module'
            if ((getFlags.execute(cls) & TypeFlags.HEAPTYPE) != 0) {
                Object module = readAttr.execute(cls, T___MODULE__);
                if (module == PNone.NO_VALUE) {
                    throw raise(AttributeError);
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
        Object setNative(PythonNativeClass cls, Object value,
                        @Shared @Cached GetTypeFlagsNode getFlags,
                        @Cached("createForceType()") WriteAttributeToObjectNode writeAttr) {
            long flags = getFlags.execute(cls);
            if ((flags & TypeFlags.HEAPTYPE) == 0) {
                throw raise(TypeError, ErrorMessages.CANT_SET_N_S, cls, T___MODULE__);
            }
            writeAttr.execute(cls, T___MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setModuleType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setModuleBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }
    }

    @Builtin(name = J___QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        @SuppressWarnings("truffle-static-method")
        Object setName(PythonClass cls, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            try {
                cls.setQualName(castToStringNode.execute(inliningTarget, value));
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_STR_TO_QUALNAME, cls, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static TruffleString getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            TruffleString tpName = getTpNameNode.readFromObj(cls, PyTypeObject__tp_name);
            int nameLen = codePointLengthNode.execute(tpName, TS_ENCODING);
            int firstDot = indexOfCodePointNode.execute(tpName, '.', 0, nameLen, TS_ENCODING);
            if (firstDot < 0) {
                return tpName;
            }
            return substringNode.execute(tpName, firstDot + 1, nameLen - firstDot - 1, TS_ENCODING, true);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = J___DICTOFFSET__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictoffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getDictoffsetType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetDictOffsetNode getDictOffsetNode) {
            return getDictOffsetNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___ITEMSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ItemsizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long getItemsizeType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetItemSizeNode getItemsizeNode) {
            return getItemsizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___BASICSIZE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BasicsizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getBasicsizeType(Object cls,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.GetBasicSizeNode getBasicSizeNode) {
            return getBasicSizeNode.execute(inliningTarget, cls);
        }
    }

    @Builtin(name = J___FLAGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            if (PGuards.isClass(inliningTarget, self, isTypeNode)) {
                return getTypeFlagsNode.execute(self);
            }
            throw raise(PythonErrorType.TypeError, ErrorMessages.DESC_FLAG_FOR_TYPE_DOESNT_APPLY_TO_OBJ, self);
        }
    }

    @Builtin(name = J___ABSTRACTMETHODS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AbstractMethodsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(Object self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode) {
            // Avoid returning this descriptor
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                Object result = readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__);
                if (result != PNone.NO_VALUE) {
                    return result;
                }
            }
            throw raise(AttributeError, ErrorMessages.OBJ_N_HAS_NO_ATTR_S, self, T___ABSTRACTMETHODS__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        @SuppressWarnings("truffle-static-method")
        Object set(VirtualFrame frame, PythonClass self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, value);
                self.setAbstractClass(isTrueNode.execute(frame, inliningTarget, value));
                return PNone.NONE;
            }
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Specialization(guards = "!isNoValue(value)")
        @SuppressWarnings("truffle-static-method")
        Object delete(PythonClass self, @SuppressWarnings("unused") DescriptorDeleteMarker value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Exclusive @Cached WriteAttributeToObjectNode writeAttributeToObjectNode) {
            if (!isSameTypeNode.execute(inliningTarget, self, PythonBuiltinClassType.PythonClass)) {
                if (readAttributeFromObjectNode.execute(self, T___ABSTRACTMETHODS__) != PNone.NO_VALUE) {
                    writeAttributeToObjectNode.execute(self, T___ABSTRACTMETHODS__, PNone.NO_VALUE);
                    self.setAbstractClass(false);
                    return PNone.NONE;
                }
            }
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object set(Object self, Object value) {
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, J___ABSTRACTMETHODS__, self);
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for type objects\n\n\tThis includes all attributes of klass and all of the base\n\tclasses recursively.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object dir(VirtualFrame frame, Object klass,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached("createGetAttrNode()") GetFixedAttributeNode getBasesNode,
                        @Cached PythonObjectFactory factory) {
            PSet names = dir(frame, inliningTarget, klass, lookupAttrNode, callNode, getBasesNode, toArrayNode, factory);
            return names;
        }

        private static PSet dir(VirtualFrame frame, Node inliningTarget, Object klass, PyObjectLookupAttr lookupAttrNode, com.oracle.graal.python.nodes.call.CallNode callNode,
                        GetFixedAttributeNode getBasesNode, ToArrayNode toArrayNode, PythonObjectFactory factory) {
            PSet names = factory.createSet();
            Object updateCallable = lookupAttrNode.execute(frame, inliningTarget, names, T_UPDATE);
            Object ns = lookupAttrNode.execute(frame, inliningTarget, klass, T___DICT__);
            if (ns != PNone.NO_VALUE) {
                callNode.execute(frame, updateCallable, ns);
            }
            Object basesAttr = getBasesNode.execute(frame, klass);
            if (basesAttr instanceof PTuple) {
                Object[] bases = toArrayNode.execute(inliningTarget, ((PTuple) basesAttr).getSequenceStorage());
                for (Object cls : bases) {
                    // Note that since we are only interested in the keys, the order
                    // we merge classes is unimportant
                    Object baseNames = dir(frame, inliningTarget, cls, lookupAttrNode, callNode, getBasesNode, toArrayNode, factory);
                    callNode.execute(frame, updateCallable, baseNames);
                }
            }
            return names;
        }

        @NeverDefault
        protected GetFixedAttributeNode createGetAttrNode() {
            return GetFixedAttributeNode.create(T___BASES__);
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
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
        Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Cached PythonObjectFactory.Lazy factory) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                annotations = factory.get(inliningTarget).createDict();
                try {
                    write.execute(self, T___ANNOTATIONS__, annotations);
                } catch (PException e) {
                    throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, T___ANNOTATIONS__);
                }
            }
            return annotations;
        }

        @Specialization(guards = "isDeleteMarker(value)")
        Object delete(Object self, @SuppressWarnings("unused") Object value,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            try {
                write.execute(self, T___ANNOTATIONS__, PNone.NO_VALUE);
            } catch (PException e) {
                throw raise(TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            if (annotations == PNone.NO_VALUE) {
                throw raise(AttributeError, new Object[]{T___ANNOTATIONS__});
            }
            return PNone.NONE;
        }

        @Fallback
        Object set(Object self, Object value,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            try {
                write.execute(self, T___ANNOTATIONS__, value);
            } catch (PException e) {
                throw raise(TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_S_OF_IMMUTABLE_TYPE_N, T___ANNOTATIONS__, self);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___TEXT_SIGNATURE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TextSignatureNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object signature(Object type) {
            if (!(type instanceof PythonBuiltinClassType || type instanceof PythonBuiltinClass)) {
                return PNone.NONE;
            }
            /* Best effort at getting at least something */
            ValueProfile profile = ValueProfile.getUncached();
            if (overridesBuiltinMethod(type, profile, LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.New), profile,
                            BuiltinConstructorsFactory.ObjectNodeFactory.class)) {
                return fromMethod(LookupAttributeInMRONode.Dynamic.getUncached().execute(type, T___NEW__));
            } else if (overridesBuiltinMethod(type, profile, LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.Init), profile, ObjectBuiltinsFactory.InitNodeFactory.class)) {
                return fromMethod(LookupAttributeInMRONode.Dynamic.getUncached().execute(type, T___INIT__));
            }
            // object() signature
            return StringLiterals.T_EMPTY_PARENS;
        }

        private static Object fromMethod(Object method) {
            if (method instanceof PBuiltinFunction || method instanceof PBuiltinMethod || method instanceof PFunction || method instanceof PMethod) {
                Signature signature = FunctionNodes.GetSignatureNode.executeUncached(method);
                return AbstractFunctionBuiltins.TextSignatureNode.signatureToText(signature, true);
            }
            return PNone.NONE;
        }
    }
}
