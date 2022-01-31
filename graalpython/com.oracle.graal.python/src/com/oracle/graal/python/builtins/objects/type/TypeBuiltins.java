/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.str.StringUtils.canEncodeUTF8;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.containsNullCharacter;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FLAGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MRO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSHOOK__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes.AbstractSetattrNode;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CheckCompatibleForAssigmentNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetItemsizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedSlotNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
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
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonClass)
public class TypeBuiltins extends PythonBuiltins {

    public static final HiddenKey TYPE_DICTOFFSET = new HiddenKey(__DICTOFFSET__);
    public static final HiddenKey TYPE_ITEMSIZE = new HiddenKey(__ITEMSIZE__);
    public static final HiddenKey TYPE_BASICSIZE = new HiddenKey(__BASICSIZE__);
    public static final HiddenKey TYPE_ALLOC = new HiddenKey(__ALLOC__);
    public static final HiddenKey TYPE_DEALLOC = new HiddenKey("__dealloc__");
    public static final HiddenKey TYPE_DEL = new HiddenKey("__del__");
    public static final HiddenKey TYPE_FREE = new HiddenKey("__free__");
    public static final HiddenKey TYPE_FLAGS = new HiddenKey(__FLAGS__);
    public static final HiddenKey TYPE_VECTORCALL_OFFSET = new HiddenKey("__vectorcall_offset__");
    public static final HiddenKey TYPE_GETBUFFER = new HiddenKey("__getbuffer__");
    public static final HiddenKey TYPE_RELEASEBUFFER = new HiddenKey("__releasebuffer__");
    private static final HiddenKey TYPE_DOC = new HiddenKey(__DOC__);

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
        builtinConstants.put(TYPE_DOC, //
                        "type(object_or_name, bases, dict)\n" + //
                                        "type(object) -> the object's type\n" + //
                                        "type(name, bases, dict) -> a new type");
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String repr(VirtualFrame frame, Object self,
                        @Cached("create(__MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            Object moduleName = readModuleNode.executeObject(frame, self);
            Object qualName = readQualNameNode.executeObject(frame, self);
            return concat(moduleName, qualName);
        }

        @TruffleBoundary
        private static String concat(Object moduleName, Object qualName) {
            if (moduleName != PNone.NO_VALUE && !moduleName.equals(BuiltinNames.BUILTINS)) {
                return String.format("<class '%s.%s'>", moduleName, qualName);
            }
            return String.format("<class '%s'>", qualName);
        }
    }

    @Builtin(name = __DOC__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class DocNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object getDoc(PythonBuiltinClassType self, @SuppressWarnings("unused") PNone value) {
            return getDoc(getCore().lookupType(self), value);
        }

        @Specialization
        @TruffleBoundary
        static Object getDoc(PythonBuiltinClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            if (IsBuiltinClassProfile.getUncached().profileClass(self, PythonBuiltinClassType.PythonClass)) {
                return self.getAttribute(TYPE_DOC);
            } else {
                return self.getAttribute(__DOC__);
            }
        }

        @Specialization(guards = "!isAnyBuiltinClass(self)")
        static Object getDoc(VirtualFrame frame, PythonClass self, @SuppressWarnings("unused") PNone value) {
            // see type.c#type_get_doc()
            Object res = self.getAttribute(__DOC__);
            Object resClass = GetClassNode.getUncached().execute(res);
            Object get = LookupAttributeInMRONode.Dynamic.getUncached().execute(resClass, __GET__);
            if (PGuards.isCallable(get)) {
                return CallTernaryMethodNode.getUncached().execute(frame, get, res, PNone.NONE, self);
            }
            return res;
        }

        protected boolean isAnyBuiltinClass(PythonClass klass) {
            return IsBuiltinClassProfile.getUncached().profileIsAnyBuiltinClass(klass);
        }

        @Specialization
        static Object getDoc(PythonNativeClass self, @SuppressWarnings("unused") PNone value) {
            return ReadAttributeFromObjectNode.getUncachedForceType().execute(self, __DOC__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object setDoc(PythonClass self, Object value) {
            self.setAttribute(__DOC__, value);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = {"!isNoValue(value)", "isBuiltin.profileIsAnyBuiltinClass(self)"})
        Object doc(Object self, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltin) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_N_S, self, __DOC__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object doc(PythonClass self, Object value) {
            self.setAttribute(__DOC__, value);
            return PNone.NO_VALUE;
        }

        @Specialization
        Object doc(Object self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Cached GetNameNode getName) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANNOT_DELETE_ATTRIBUTE, getName.execute(self), __DOC__);
        }
    }

    @Builtin(name = __MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonBuiltinNode {
        @Specialization
        Object doit(Object klass,
                        @Cached TypeNodes.GetMroNode getMroNode,
                        @Cached ConditionProfile notInitialized) {
            if (notInitialized.profile(klass instanceof PythonManagedClass && !((PythonManagedClass) klass).isMROInitialized())) {
                return PNone.NONE;
            }
            PythonAbstractClass[] mro = getMroNode.execute(klass);
            return factory().createTuple(mro);
        }
    }

    @Builtin(name = MRO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTypeNode.execute(klass)", limit = "1")
        Object doit(Object klass,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetMroNode getMroNode) {
            PythonAbstractClass[] mro = getMroNode.execute(klass);
            return factory().createList(Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doit(Object object) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, MRO, "type", object);
        }
    }

    @Builtin(name = __INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization
        Object init(@SuppressWarnings("unused") Object self, Object[] arguments, @SuppressWarnings("unused") PKeyword[] kwds) {
            if (arguments.length != 1 && arguments.length != 3) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type.__init__()", 1, 3);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {

        public static CallNode create() {
            return CallNodeFactory.create();
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, PNone.NO_VALUE, arguments, keywords);
        }

        @Specialization
        Object selfInArgs(VirtualFrame frame, @SuppressWarnings("unused") PNone self, Object[] arguments, PKeyword[] keywords,
                        @Shared("callNode") @Cached CallNodeHelper callNode) {
            return callNode.execute(frame, arguments[0], arguments, keywords, false);
        }

        @Fallback
        Object selfSeparate(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords,
                        @Shared("callNode") @Cached CallNodeHelper callNode) {
            return callNode.execute(frame, self, arguments, keywords, true);
        }
    }

    @ReportPolymorphism
    public abstract static class BindNew extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object descriptor, Object type);

        @Specialization
        static Object doBuiltin(PBuiltinFunction descriptor, @SuppressWarnings("unused") Object type) {
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
        static Object doBind(VirtualFrame frame, Object descriptor, Object type,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "Get") LookupCallableSlotInMRONode lookupGet,
                        @Cached CallTernaryMethodNode callGet) {
            Object getMethod = lookupGet.execute(getClassNode.execute(descriptor));
            if (getMethod != PNone.NO_VALUE) {
                return callGet.execute(frame, getMethod, descriptor, PNone.NONE, type);
            }
            return descriptor;
        }

        public static BindNew create() {
            return TypeBuiltinsFactory.BindNewNodeGen.create();
        }
    }

    @ReportPolymorphism
    protected abstract static class CallNodeHelper extends PNodeWithRaise {
        @Child private CallVarargsMethodNode dispatchNew = CallVarargsMethodNode.create();
        @Child private LookupCallableSlotInMRONode lookupNew = LookupCallableSlotInMRONode.create(SpecialMethodSlot.New);
        @Child private BindNew bindNew = BindNew.create();
        @Child private CallVarargsMethodNode dispatchInit;
        @Child private LookupSpecialMethodSlotNode lookupInit;
        @Child private IsSubtypeNode isSubTypeNode;
        @Child private TypeNodes.GetNameNode getNameNode;
        @Child private GetClassNode getClassNode;

        @CompilationFinal private ConditionProfile hasNew = ConditionProfile.createBinaryProfile();
        @CompilationFinal private ConditionProfile hasInit = ConditionProfile.createBinaryProfile();
        @CompilationFinal private ConditionProfile gotInitResult = ConditionProfile.createBinaryProfile();

        abstract Object execute(VirtualFrame frame, Object self, Object[] args, PKeyword[] keywords, boolean doCreateArgs);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf"})
        protected Object doIt0BuiltinSingle(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs,
                        @Cached("self") PythonBuiltinClass cachedSelf) {
            PythonBuiltinClassType type = cachedSelf.getType();
            if (!doCreateArgs) {
                arguments[0] = type;
            }
            return op(frame, type, arguments, keywords, doCreateArgs);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf", "isPythonClass(cachedSelf)",
                        "!isPythonBuiltinClass(cachedSelf)"})
        protected Object doIt0User(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs,
                        @Cached(value = "self", weak = true) Object cachedSelf) {
            return op(frame, cachedSelf, arguments, keywords, doCreateArgs);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self.getType() == cachedType"})
        protected Object doIt0BuiltinMulti(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs,
                        @Cached("self.getType()") PythonBuiltinClassType cachedType) {
            if (!doCreateArgs) {
                arguments[0] = cachedType;
            }
            return op(frame, cachedType, arguments, keywords, doCreateArgs);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedType"})
        protected Object doIt0BuiltinType(VirtualFrame frame, @SuppressWarnings("unused") PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs,
                        @Cached("self") PythonBuiltinClassType cachedType) {
            return op(frame, cachedType, arguments, keywords, doCreateArgs);
        }

        @Specialization(replaces = {"doIt0BuiltinSingle", "doIt0BuiltinMulti"})
        protected Object doItIndirect0Builtin(VirtualFrame frame, PythonBuiltinClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            PythonBuiltinClassType type = self.getType();
            if (!doCreateArgs) {
                arguments[0] = type;
            }
            return op(frame, type, arguments, keywords, doCreateArgs);
        }

        @Specialization(replaces = "doIt0BuiltinType")
        protected Object doItIndirect0BuiltinType(VirtualFrame frame, PythonBuiltinClassType self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            return op(frame, self, arguments, keywords, doCreateArgs);
        }

        @Specialization(replaces = {"doIt0User"}, guards = "!isPythonBuiltinClass(self)")
        protected Object doItIndirect0User(VirtualFrame frame, PythonAbstractClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            return op(frame, self, arguments, keywords, doCreateArgs);
        }

        /* self is native */
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"isSingleContext()", "self == cachedSelf"})
        protected Object doIt1(VirtualFrame frame, @SuppressWarnings("unused") PythonNativeObject self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs,
                        @Cached("self") PythonNativeObject cachedSelf) {
            return op(frame, PythonNativeClass.cast(cachedSelf), arguments, keywords, doCreateArgs);
        }

        @Specialization(replaces = "doIt1")
        protected Object doItIndirect1(VirtualFrame frame, PythonNativeObject self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            return op(frame, PythonNativeClass.cast(self), arguments, keywords, doCreateArgs);
        }

        private Object op(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            Object newMethod = lookupNew.execute(self);
            if (hasNew.profile(newMethod != PNone.NO_VALUE)) {
                CompilerAsserts.partialEvaluationConstant(doCreateArgs);
                Object[] newArgs = doCreateArgs ? PositionalArgumentsNode.prependArgument(self, arguments) : arguments;
                Object newInstance = dispatchNew.execute(frame, bindNew.execute(frame, newMethod, self), newArgs, keywords);

                // see typeobject.c#type_call()
                // Ugly exception: when the call was type(something),
                // don't call tp_init on the result.
                if (!(self == PythonBuiltinClassType.PythonClass && arguments.length == 2 && keywords.length == 0)) {
                    callInit(newInstance, self, frame, doCreateArgs, arguments, keywords);
                }
                return newInstance;
            } else {
                throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, getTypeName(self));
            }
        }

        private void callInit(Object newInstance, Object self, VirtualFrame frame, boolean doCreateArgs, Object[] arguments, PKeyword[] keywords) {
            Object newInstanceKlass = getInstanceClass(newInstance);
            if (isSubType(newInstanceKlass, self)) {
                Object initMethod = getInitNode().execute(frame, newInstanceKlass, newInstance);
                if (hasInit.profile(initMethod != PNone.NO_VALUE)) {
                    Object[] initArgs;
                    if (doCreateArgs) {
                        initArgs = PositionalArgumentsNode.prependArgument(newInstance, arguments);
                    } else {
                        // XXX: (tfel) is this valid? I think it should be fine...
                        arguments[0] = newInstance;
                        initArgs = arguments;
                    }
                    Object initResult = getDispatchNode().execute(frame, initMethod, initArgs, keywords);
                    if (gotInitResult.profile(initResult != PNone.NONE && initResult != PNone.NO_VALUE)) {
                        throw raise(TypeError, ErrorMessages.SHOULD_RETURN_NONE, "__init__()");
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

        private String getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }

        private Object getInstanceClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(object);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        public static GetattributeNode create() {
            return TypeBuiltinsFactory.GetattributeNodeFactory.create();
        }

        private final BranchProfile hasDescProfile = BranchProfile.create();
        private final BranchProfile isDescProfile = BranchProfile.create();
        private final BranchProfile hasValueProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();

        @Child private LookupInheritedSlotNode valueGetLookup;
        @Child private LookupCallableSlotInMRONode lookupGetNode;
        @Child private LookupCallableSlotInMRONode lookupSetNode;
        @Child private LookupCallableSlotInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode invokeGet;
        @Child private CallTernaryMethodNode invokeValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;
        @Child private TypeNodes.GetNameNode getNameNode;
        @Child private GetClassNode getDescClassNode;

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object keyObj,
                        @Cached GetClassNode getClassNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached CastToJavaStringNode castToString) {
            String key;
            try {
                key = castToString.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            Object type = getClassNode.execute(object);
            Object descr = lookup.execute(type, key);
            Object get = null;
            if (descr != PNone.NO_VALUE) {
                // acts as a branch profile
                Object dataDescClass = getDescClass(descr);
                get = lookupGet(dataDescClass);
                if (PGuards.isCallableOrDescriptor(get)) {
                    Object delete = PNone.NO_VALUE;
                    Object set = lookupSet(dataDescClass);
                    if (set == PNone.NO_VALUE) {
                        delete = lookupDelete(dataDescClass);
                    }
                    if (set != PNone.NO_VALUE || delete != PNone.NO_VALUE) {
                        isDescProfile.enter();
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
                hasValueProfile.enter();
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
                hasDescProfile.enter();
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
            errorProfile.enter();
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, getTypeName(object), key);
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

        private String getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }

        private Object getDescClass(Object desc) {
            if (getDescClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDescClassNode = insert(GetClassNode.create());
            }
            return getDescClassNode.execute(desc);
        }
    }

    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends AbstractSetattrNode {
        @Child WriteAttributeToObjectNode writeNode;

        @Override
        protected boolean writeAttribute(Object object, String key, Object value) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(WriteAttributeToObjectNode.createForceType());
            }
            return writeNode.execute(object, key, value);
        }
    }

    @Builtin(name = __PREPARE__, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs) {
            return factory().createDict(new DynamicObjectStorage(PythonLanguage.get(this)));
        }
    }

    @Builtin(name = __BASES__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object getBases(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            return factory().createTuple(getBaseClassesNode.execute(self));
        }

        @Specialization
        Object setBases(VirtualFrame frame, PythonClass cls, PTuple value,
                        @Cached GetNameNode getName,
                        @Cached GetObjectArrayNode getArray,
                        @Cached GetBaseClassNode getBase,
                        @Cached GetBestBaseClassNode getBestBase,
                        @Cached CheckCompatibleForAssigmentNode checkCompatibleForAssigment,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetMroNode getMroNode) {

            Object[] a = getArray.execute(value);
            if (a.length == 0) {
                throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_NON_EMPTY_TUPLE_TO_P, cls);
            }
            PythonAbstractClass[] baseClasses = new PythonAbstractClass[a.length];
            for (int i = 0; i < a.length; i++) {
                if (PGuards.isPythonClass(a[i])) {
                    if (isSubtypeNode.execute(frame, a[i], cls) ||
                                    hasMRO(getMroNode, a[i]) && typeIsSubtypeBaseChain(a[i], cls, getBase, isSameTypeNode)) {
                        throw raise(TypeError, ErrorMessages.BASES_ITEM_CAUSES_INHERITANCE_CYCLE);
                    }
                    if (a[i] instanceof PythonBuiltinClassType) {
                        baseClasses[i] = getCore().lookupType((PythonBuiltinClassType) a[i]);
                    } else {
                        baseClasses[i] = (PythonAbstractClass) a[i];
                    }
                } else {
                    throw raise(TypeError, ErrorMessages.MUST_BE_TUPLE_OF_CLASSES_NOT_P, getName.execute(cls), "__bases__", a[i]);
                }
            }

            Object newBestBase = getBestBase.execute(baseClasses);
            if (newBestBase == null) {
                return null;
            }

            Object oldBase = getBase.execute(cls);
            checkCompatibleForAssigment.execute(frame, oldBase, newBestBase);

            cls.setSuperClass(baseClasses);
            SpecialMethodSlot.reinitializeSpecialMethodSlots(cls, getLanguage());

            return PNone.NONE;
        }

        private static boolean hasMRO(GetMroNode getMroNode, Object i) {
            PythonAbstractClass[] mro = getMroNode.execute(i);
            return mro != null && mro.length > 0;
        }

        private static boolean typeIsSubtypeBaseChain(Object a, Object b, GetBaseClassNode getBaseNode, IsSameTypeNode isSameTypeNode) {
            Object base = a;
            do {
                if (isSameTypeNode.execute(base, b)) {
                    return true;
                }
                base = getBaseNode.execute(base);
            } while (base != null);

            return (isSameTypeNode.execute(b, PythonBuiltinClassType.PythonObject));
        }

        @Specialization(guards = "!isPTuple(value)")
        Object setObject(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value,
                        @Cached GetNameNode getName) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_S_S_NOT_P, "tuple", getName.execute(cls), "__bases__", value);
        }

        @Specialization
        Object setBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value,
                        @Cached GetNameNode getName) {
            throw raise(TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE_S, getName.execute(cls));
        }

    }

    @Builtin(name = __BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        static Object base(Object self,
                        @Cached TypeNodes.GetBaseClassNode getBaseClassNode) {
            Object baseClass = getBaseClassNode.execute(self);
            return baseClass != null ? baseClass : PNone.NONE;
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doType(PythonBuiltinClassType self,
                        @Cached GetDictIfExistsNode getDict) {
            return doManaged(getCore().lookupType(self), getDict);
        }

        @Specialization
        Object doManaged(PythonManagedClass self,
                        @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self, self.getMethodResolutionOrder());
                // The mapping is unmodifiable, so we don't have to assign it back
            }
            return factory().createMappingproxy(dict);
        }

        @Specialization
        static Object doNative(PythonNativeClass self,
                        @Cached CExtNodes.GetTypeMemberNode getTpDictNode) {
            return getTpDictNode.execute(self, NativeMember.TP_DICT);
        }
    }

    @Builtin(name = __INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode getAttributeNode = LookupAndCallBinaryNode.create(SpecialMethodSlot.GetAttribute);
        @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();
        @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();

        private final ConditionProfile typeErrorProfile = ConditionProfile.createBinaryProfile();

        public abstract boolean executeWith(VirtualFrame frame, Object cls, Object instance);

        public static InstanceCheckNode create() {
            return TypeBuiltinsFactory.InstanceCheckNodeFactory.create();
        }

        private PythonObject getInstanceClassAttr(VirtualFrame frame, Object instance) {
            Object classAttr = getAttributeNode.executeObject(frame, instance, __CLASS__);
            if (classAttr instanceof PythonObject) {
                return (PythonObject) classAttr;
            }
            return null;
        }

        @Specialization(guards = "isTypeNode.execute(cls)", limit = "1")
        boolean isInstance(VirtualFrame frame, Object cls, Object instance,
                        @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(frame, getClassNode.execute(instance), cls)) {
                return true;
            }

            Object instanceClass = getAttributeNode.executeObject(frame, instance, __CLASS__);
            return PGuards.isManagedClass(instanceClass) && isSubtypeNode.execute(frame, instanceClass, cls);
        }

        @Fallback
        boolean isInstance(VirtualFrame frame, Object cls, Object instance) {
            if (typeErrorProfile.profile(getBasesNode.execute(frame, cls) == null)) {
                throw raise(TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_TYPE, instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(frame, instance);
            return instanceClass != null && abstractIsSubclassNode.execute(frame, instanceClass, cls);
        }
    }

    @Builtin(name = __SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private TypeNodes.IsSameTypeNode isSameTypeNode = IsSameTypeNodeGen.create();
        @Child private GetFixedAttributeNode getBasesAttrNode;
        @Child private ObjectNodes.FastIsTupleSubClassNode isTupleSubClassNode;

        @Child private IsBuiltinClassProfile isAttrErrorProfile;

        @Specialization(guards = {"!isNativeClass(cls)", "!isNativeClass(derived)"})
        boolean doManagedManaged(VirtualFrame frame, Object cls, Object derived) {
            return isSameType(cls, derived) || isSubtypeNode.execute(frame, derived, cls);
        }

        @Specialization
        boolean doObjectObject(VirtualFrame frame, Object cls, Object derived,
                        @Cached TypeNodes.IsTypeNode isClsTypeNode,
                        @Cached TypeNodes.IsTypeNode isDerivedTypeNode) {
            if (isSameType(cls, derived)) {
                return true;
            }

            // no profiles required because IsTypeNode profiles already
            if (isClsTypeNode.execute(cls) && isDerivedTypeNode.execute(derived)) {
                return isSubtypeNode.execute(frame, derived, cls);
            }
            if (!checkClass(frame, derived)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
            }
            if (!checkClass(frame, cls)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_CLSS_WAS);
            }
            return false;
        }

        // checks if object has '__bases__' (see CPython 'abstract.c' function
        // 'recursive_issubclass')
        private boolean checkClass(VirtualFrame frame, Object obj) {
            if (getBasesAttrNode == null || isAttrErrorProfile == null || isTupleSubClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBasesAttrNode = insert(GetFixedAttributeNode.create(SpecialAttributeNames.__BASES__));
                isAttrErrorProfile = insert(IsBuiltinClassProfile.create());
                isTupleSubClassNode = insert(ObjectNodes.FastIsTupleSubClassNode.create());
            }
            Object basesObj;
            try {
                basesObj = getBasesAttrNode.executeObject(frame, obj);
            } catch (PException e) {
                e.expectAttributeError(isAttrErrorProfile);
                return false;
            }
            return isTupleSubClassNode.execute(frame, basesObj);
        }

        protected boolean isSameType(Object a, Object b) {
            return isSameTypeNode.execute(a, b);
        }
    }

    @Builtin(name = __SUBCLASSHOOK__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class SubclassHookNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object hook(VirtualFrame frame, Object cls, Object subclass) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __SUBCLASSES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SubclassesNode extends PythonUnaryBuiltinNode {

        @Specialization
        PList getSubclasses(Object cls,
                        @Cached GetSubclassesNode getSubclassesNode) {
            // TODO: missing: keep track of subclasses
            return factory().createList(toArray(getSubclassesNode.execute(cls)));
        }

        @TruffleBoundary
        private static <T> Object[] toArray(Set<T> subclasses) {
            return subclasses.toArray();
        }
    }

    @GenerateNodeFactory
    @ImportStatic(NativeMember.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static String getNameType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static String getNameBuiltin(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
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
        Object setName(VirtualFrame frame, PythonClass cls, Object value,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode) {
            try {
                String string = castToJavaStringNode.execute(value);
                if (containsNullCharacter(string)) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
                }
                if (!canEncodeUTF8(string)) {
                    throw constructAndRaiseNode.raiseUnicodeEncodeError(frame, "utf-8", string, 0, string.length(), "can't encode classname");
                }
                cls.setName(string);
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_ASSIGN_S_TO_P_S_NOT_P, "string", cls, __NAME__, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode,
                        @Exclusive @Cached CastToJavaStringNode castToJavaStringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = castToJavaStringNode.execute(getTpNameNode.execute(cls, NativeMember.TP_NAME));
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object getModule(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }

        @TruffleBoundary
        private static String getQualName(String fqname) {
            int firstDot = fqname.indexOf('.');
            if (firstDot != -1) {
                return fqname.substring(firstDot + 1);
            }
            return fqname;
        }

    }

    @Builtin(name = __MODULE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ModuleNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        static Object getModuleType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            String module = cls.getModuleName();
            return module == null ? BuiltinNames.BUILTINS : module;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModuleBuiltin(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return getModuleType(cls.getType(), value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached ReadAttributeFromObjectNode readAttrNode) {
            Object module = readAttrNode.execute(cls, __MODULE__);
            if (module == PNone.NO_VALUE) {
                throw raise(AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object setModule(PythonClass cls, Object value,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, __MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getModule(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttr,
                        @Cached GetTypeMemberNode getTpNameNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            Object module = readAttr.execute(cls, __MODULE__);
            if (module != PNone.NO_VALUE) {
                return module;
            } else {
                // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
                String tpName = castToJavaStringNode.execute(getTpNameNode.execute(cls, NativeMember.TP_NAME));
                return getModuleName(tpName);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(PythonNativeClass cls, Object value,
                        @Cached GetTypeFlagsNode getFlags,
                        @Cached("createForceType()") WriteAttributeToObjectNode writeAttr) {
            long flags = getFlags.execute(cls);
            if ((flags & TypeFlags.HEAPTYPE) == 0) {
                throw raise(TypeError, ErrorMessages.CANT_SET_N_S, cls, __MODULE__);
            }
            writeAttr.execute(cls, __MODULE__, value);
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

        @TruffleBoundary
        private static Object getModuleName(String fqname) {
            int firstDotIdx = fqname.indexOf('.');
            if (firstDotIdx != -1) {
                return fqname.substring(0, firstDotIdx);
            }
            return BuiltinNames.BUILTINS;
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        static String getName(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = "isNoValue(value)")
        static String getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getQualName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                cls.setQualName(castToJavaStringNode.execute(value));
                return PNone.NONE;
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, "can only assign string to %p.__qualname__, not '%p'", cls, value);
            }
        }

        @Specialization(guards = "isNoValue(value)")
        static String getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = castToJavaStringNode.execute(getTpNameNode.execute(cls, NativeMember.TP_NAME));
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }

        @TruffleBoundary
        private static String getQualName(String fqname) {
            int firstDot = fqname.indexOf('.');
            if (firstDot != -1) {
                return fqname.substring(firstDot + 1);
            }
            return fqname;
        }
    }

    @Builtin(name = __DICTOFFSET__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class DictoffsetNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        Object getDictoffsetType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached ReadAttributeFromObjectNode getName) {
            return getDictoffsetManaged(getCore().lookupType(cls), value, profile, getName);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getDictoffsetManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached ReadAttributeFromObjectNode getName) {
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getName.execute(cls, TYPE_DICTOFFSET);
            }
            return getName.execute(cls, __DICTOFFSET__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setDictoffsetType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setDictoffsetBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        static Object setDictoffsetClass(PythonClass cls, Object value,
                        @Cached WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __DICTOFFSET__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMember.TP_DICTOFFSET);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __ITEMSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ItemsizeNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        static long getItemsizeType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetItemsizeNode getItemsizeNode) {
            return getItemsizeNode.execute(cls);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getItemsizeManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetItemsizeNode getItemsizeNode) {
            return getItemsizeNode.execute(cls);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetItemsizeNode getItemsizeNode) {
            return getItemsizeNode.execute(cls);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setItemsizeType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setItemsizeBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isPythonBuiltinClass(cls)", "!isNoValue(value)"})
        Object setItemsize(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __BASICSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class BasicsizeNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        Object getBasicsizeType(PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone value,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached ReadAttributeFromObjectNode getName) {
            return getBasicsizeManaged(getCore().lookupType(cls), value, profile, getName);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getBasicsizeManaged(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached ReadAttributeFromObjectNode getName) {
            Object basicsize;
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                basicsize = getName.execute(cls, TYPE_BASICSIZE);
            } else {
                basicsize = getName.execute(cls, __BASICSIZE__);
            }
            return basicsize != PNone.NO_VALUE ? basicsize : 0;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setBasicsizeType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setBasicsizeBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        static Object setBasicsize(PythonClass cls, Object value,
                        @Cached WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __BASICSIZE__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMember.TP_BASICSIZE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE, "native type");
        }
    }

    @Builtin(name = __FLAGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        Object doGeneric(Object self,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            if (PGuards.isClass(self, lib)) {
                return getTypeFlagsNode.execute(self);
            }
            throw raise(PythonErrorType.TypeError, "descriptor '__flags__' for 'type' objects doesn't apply to '%p' object", self);
        }
    }

    @Builtin(name = __ABSTRACTMETHODS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AbstractMethodsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(Object self, @SuppressWarnings("unused") PNone none,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode) {
            // Avoid returning this descriptor
            if (!isSameTypeNode.execute(self, PythonBuiltinClassType.PythonClass)) {
                Object result = readAttributeFromObjectNode.execute(self, __ABSTRACTMETHODS__);
                if (result != PNone.NO_VALUE) {
                    return result;
                }
            }
            throw raise(AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, GetNameNode.getUncached().execute(self), __ABSTRACTMETHODS__);
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        Object set(VirtualFrame frame, PythonClass self, Object value,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode) {
            if (!isSameTypeNode.execute(self, PythonBuiltinClassType.PythonClass)) {
                writeAttributeToObjectNode.execute(self, __ABSTRACTMETHODS__, value);
                self.setAbstractClass(isTrueNode.execute(frame, value));
                return PNone.NONE;
            }
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE_S, GetNameNode.getUncached().execute(self));
        }

        @Specialization(guards = "!isNoValue(value)")
        Object delete(PythonClass self, @SuppressWarnings("unused") DescriptorDeleteMarker value,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached WriteAttributeToObjectNode writeAttributeToObjectNode) {
            if (!isSameTypeNode.execute(self, PythonBuiltinClassType.PythonClass)) {
                if (readAttributeFromObjectNode.execute(self, __ABSTRACTMETHODS__) != PNone.NO_VALUE) {
                    writeAttributeToObjectNode.execute(self, __ABSTRACTMETHODS__, PNone.NO_VALUE);
                    self.setAbstractClass(false);
                    return PNone.NONE;
                }
            }
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE_S, GetNameNode.getUncached().execute(self));
        }

        @Fallback
        @SuppressWarnings("unused")
        Object set(Object self, Object value) {
            throw raise(AttributeError, ErrorMessages.CANT_SET_ATTRIBUTES_OF_TYPE_S, GetNameNode.getUncached().execute(self));
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1, doc = "__dir__ for type objects\n\n\tThis includes all attributes of klass and all of the base\n\tclasses recursively.")
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dir(VirtualFrame frame, Object klass,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached("createGetAttrNode()") GetFixedAttributeNode getBasesNode,
                        @Cached DirNode dirNode) {
            PSet names = dir(frame, klass, lookupAttrNode, callNode, getBasesNode, toArrayNode, dirNode);
            return names;
        }

        private PSet dir(VirtualFrame frame, Object klass, PyObjectLookupAttr lookupAttrNode, com.oracle.graal.python.nodes.call.CallNode callNode, GetFixedAttributeNode getBasesNode,
                        ToArrayNode toArrayNode, DirNode dirNode) {
            PSet names = factory().createSet();
            Object updateCallable = lookupAttrNode.execute(frame, names, "update");
            Object ns = lookupAttrNode.execute(frame, klass, __DICT__);
            if (ns != PNone.NO_VALUE) {
                callNode.execute(frame, updateCallable, ns);
            }
            Object basesAttr = getBasesNode.execute(frame, klass);
            if (basesAttr instanceof PTuple) {
                Object[] bases = toArrayNode.execute(((PTuple) basesAttr).getSequenceStorage());
                for (Object cls : bases) {
                    // Note that since we are only interested in the keys, the order
                    // we merge classes is unimportant
                    Object baseNames = dir(frame, cls, lookupAttrNode, callNode, getBasesNode, toArrayNode, dirNode);
                    callNode.execute(frame, updateCallable, baseNames);
                }
            }
            return names;
        }

        protected GetFixedAttributeNode createGetAttrNode() {
            return GetFixedAttributeNode.create(__BASES__);
        }
    }

}
