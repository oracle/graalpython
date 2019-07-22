/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MRO__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ALLOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSES__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeFactory;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectGetBasesNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectIsSubclassNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        String repr(VirtualFrame frame, PythonAbstractClass self,
                        @Cached("create(__MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            Object moduleName = readModuleNode.executeObject(frame, self);
            Object qualName = readQualNameNode.executeObject(frame, self);
            return concat(moduleName, qualName);
        }

        @TruffleBoundary
        private String concat(Object moduleName, Object qualName) {
            if (moduleName != PNone.NO_VALUE && !moduleName.equals(getCore().getBuiltins().getModuleName())) {
                return String.format("<class '%s.%s'>", moduleName, qualName);
            }
            return String.format("<class '%s'>", qualName);
        }
    }

    @Builtin(name = __MRO__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonBuiltinNode {
        @Specialization
        Object doit(LazyPythonClass klass,
                        @Cached("create()") TypeNodes.GetMroNode getMroNode) {
            return factory().createTuple(getMroNode.execute(klass));
        }
    }

    @Builtin(name = "mro", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonBuiltinNode {
        @Specialization
        Object doit(PythonAbstractClass klass,
                        @Cached("create()") GetMroNode getMroNode) {
            PythonAbstractClass[] mro = getMroNode.execute(klass);
            return factory().createList(Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Specialization(guards = "!isClass(object)")
        Object doit(Object object) {
            throw raise(TypeError, "descriptor 'mro' requires a 'type' object but received an '%p'", object);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child private CallVarargsMethodNode dispatchNew = CallVarargsMethodNode.create();
        @Child private LookupAttributeInMRONode lookupNew = LookupAttributeInMRONode.create(__NEW__);
        @Child private CallVarargsMethodNode dispatchInit = CallVarargsMethodNode.create();
        @Child private LookupAttributeInMRONode lookupInit = LookupAttributeInMRONode.create(__INIT__);
        @Child private GetClassNode getClass = GetClassNode.create();
        @Child private TypeNodes.IsSameTypeNode isSameTypeNode;
        @Child private TypeNodes.GetNameNode getNameNode;

        private final IsBuiltinClassProfile isClassClassProfile = IsBuiltinClassProfile.create();

        public static CallNode create() {
            return CallNodeFactory.create();
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, PNone.NO_VALUE, arguments, keywords);
        }

        protected static Object first(Object[] ary) {
            return ary[0];
        }

        protected static boolean accept(Object[] ary, Object cachedSelf) {
            Object first = first(ary);
            return first == cachedSelf && PGuards.isClass(first);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"accept(arguments, cachedSelf)"})
        protected Object doItUnboxed(VirtualFrame frame, @SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @Cached("first(arguments)") Object cachedSelf) {
            return op(frame, (PythonAbstractClass) cachedSelf, arguments, keywords, false);

        }

        @Specialization(replaces = "doItUnboxed")
        protected Object doItUnboxedIndirect(VirtualFrame frame, @SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords) {
            Object self = arguments[0];
            if (PGuards.isClass(self)) {
                return op(frame, (PythonAbstractClass) self, arguments, keywords, false);
            } else if (self instanceof PythonBuiltinClassType) {
                PythonBuiltinClass actual = getBuiltinPythonClass((PythonBuiltinClassType) self);
                return op(frame, actual, arguments, keywords, false);
            } else {
                throw raise(TypeError, "descriptor '__call__' requires a 'type' object but received a '%p'", self);
            }
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"})
        protected Object doIt0(VirtualFrame frame, @SuppressWarnings("unused") PythonAbstractClass self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonAbstractClass cachedSelf) {
            return op(frame, cachedSelf, arguments, keywords, true);
        }

        @Specialization(replaces = "doIt0")
        protected Object doItIndirect0(VirtualFrame frame, PythonAbstractClass self, Object[] arguments, PKeyword[] keywords) {
            return op(frame, self, arguments, keywords, true);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"})
        protected Object doIt1(VirtualFrame frame, @SuppressWarnings("unused") PythonNativeObject self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonNativeObject cachedSelf) {
            return op(frame, PythonNativeClass.cast(cachedSelf), arguments, keywords, true);
        }

        @Specialization(replaces = "doIt1")
        protected Object doItIndirect1(VirtualFrame frame, PythonNativeObject self, Object[] arguments, PKeyword[] keywords) {
            return op(frame, PythonNativeClass.cast(self), arguments, keywords, true);
        }

        private Object op(VirtualFrame frame, PythonAbstractClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            Object newMethod = lookupNew.execute(self);
            if (newMethod != PNone.NO_VALUE) {
                CompilerAsserts.partialEvaluationConstant(doCreateArgs);
                Object[] newArgs = doCreateArgs ? PositionalArgumentsNode.prependArgument(self, arguments) : arguments;
                Object newInstance = dispatchNew.execute(frame, newMethod, newArgs, keywords);
                PythonAbstractClass newInstanceKlass = getClass.execute(newInstance);
                if (isSameType(newInstanceKlass, self)) {
                    if (arguments.length == 2 && isClassClassProfile.profileClass(self, PythonBuiltinClassType.PythonClass)) {
                        // do not call init if we are creating a new instance of type and we are
                        // passing keywords or more than one argument see:
                        // https://github.com/python/cpython/blob/2102c789035ccacbac4362589402ac68baa2cd29/Objects/typeobject.c#L3538
                    } else {
                        Object initMethod = lookupInit.execute(newInstanceKlass);
                        if (initMethod != PNone.NO_VALUE) {
                            Object[] initArgs;
                            if (doCreateArgs) {
                                initArgs = PositionalArgumentsNode.prependArgument(newInstance, arguments);
                            } else {
                                // XXX: (tfel) is this valid? I think it should be fine...
                                arguments[0] = newInstance;
                                initArgs = arguments;
                            }
                            Object initResult = dispatchInit.execute(frame, initMethod, initArgs, keywords);
                            if (initResult != PNone.NONE && initResult != PNone.NO_VALUE) {
                                throw raise(TypeError, "__init__() should return None");
                            }
                        }
                    }
                }
                return newInstance;
            } else {
                throw raise(TypeError, "cannot create '%s' instances", getTypeName(self));
            }
        }

        private boolean isSameType(PythonAbstractClass left, PythonAbstractClass right) {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(TypeNodes.IsSameTypeNode.create());
            }
            return isSameTypeNode.execute(left, right);
        }

        private String getTypeName(PythonAbstractClass clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }
    }

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
        private final ConditionProfile getClassProfile = ConditionProfile.createBinaryProfile();

        @Child private LookupAttributeInMRONode.Dynamic lookup = LookupAttributeInMRONode.Dynamic.create();
        @Child private GetLazyClassNode getObjectClassNode = GetLazyClassNode.create();
        @Child private GetLazyClassNode getDataClassNode;
        @Child private LookupInheritedAttributeNode valueGetLookup;
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode invokeGet;
        @Child private CallTernaryMethodNode invokeValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;
        @Child private TypeNodes.GetNameNode getNameNode;

        @Specialization
        protected Object doIt(VirtualFrame frame, LazyPythonClass object, Object key) {
            LazyPythonClass type = getObjectClassNode.execute(object);
            Object descr = lookup.execute(type, key);
            Object get = null;
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                LazyPythonClass dataDescClass = getDataClass(descr);
                get = lookupGet(dataDescClass);
                if (PGuards.isCallable(get)) {
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
                        return invokeGet.execute(frame, get, descr, object, getPythonClass(type, getClassProfile));
                    }
                }
            }
            Object value = readAttribute(object, key);
            if (value != PNone.NO_VALUE) {
                hasValueProfile.enter();
                Object valueGet = lookupValueGet(value);
                if (valueGet == PNone.NO_VALUE) {
                    return value;
                } else if (PGuards.isCallable(valueGet)) {
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
                } else if (PGuards.isCallable(get)) {
                    if (invokeGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeGet.execute(frame, get, descr, object, getPythonClass(type, getClassProfile));
                }
            }
            errorProfile.enter();
            throw raise(AttributeError, "type object '%s' has no attribute %s", getTypeName(object), key);
        }

        private Object readAttribute(LazyPythonClass object, Object key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object lookupDelete(LazyPythonClass dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupAttributeInMRONode.create(__DELETE__));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(LazyPythonClass dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupAttributeInMRONode.create(__SET__));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        private Object lookupGet(LazyPythonClass dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupAttributeInMRONode.create(__GET__));
            }
            return lookupGetNode.execute(dataDescClass);
        }

        private Object lookupValueGet(Object value) {
            if (valueGetLookup == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                valueGetLookup = insert(LookupInheritedAttributeNode.create(__GET__));
            }
            return valueGetLookup.execute(value);
        }

        private LazyPythonClass getDataClass(Object descr) {
            if (getDataClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDataClassNode = insert(GetLazyClassNode.create());
            }
            return getDataClassNode.execute(descr);
        }

        private String getTypeName(Object clazz) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(clazz);
        }
    }

    @Builtin(name = __PREPARE__, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs) {
            return factory().createDict();
        }
    }

    @Builtin(name = __BASES__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BasesNode extends PythonBuiltinNode {
        @Specialization
        Object bases(LazyPythonClass self,
                        @Cached("create()") TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            return factory().createTuple(getBaseClassesNode.execute(self));
        }
    }

    @Builtin(name = __BASE__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BaseNode extends PythonBuiltinNode {
        @Specialization
        Object base(LazyPythonClass self,
                        @Cached("create()") TypeNodes.GetBaseClassNode getBaseClassNode) {
            return getBaseClassNode.execute(self);
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    @ImportStatic(NativeMemberNames.class)
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "1")
        Object doManaged(PythonManagedClass self,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createMappingproxy(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            } else if (dict instanceof PDict) {
                // this is the case for types defined in native code
                dict = factory().createMappingproxy(new DynamicObjectStorage.PythonObjectHybridDictStorage(self.getStorage()));
            }
            assert dict instanceof PMappingproxy;
            return dict;
        }

        @Specialization
        Object doNative(PythonNativeClass self,
                        @Cached CExtNodes.GetTypeMemberNode getTpDictNode) {
            return getTpDictNode.execute(self, NativeMemberNames.TP_DICT);
        }
    }

    @Builtin(name = __INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode getAttributeNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
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

        @Specialization
        boolean isInstance(VirtualFrame frame, PythonAbstractClass cls, Object instance,
                        @Cached("create()") IsSubtypeNode isSubtypeNode,
                        @Cached("create()") GetClassNode getClass) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(frame, getClass.execute(instance), cls)) {
                return true;
            }

            Object instanceClass = getAttributeNode.executeObject(frame, instance, __CLASS__);
            return PGuards.isManagedClass(instanceClass) && isSubtypeNode.execute(frame, instanceClass, cls);
        }

        @Fallback
        boolean isInstance(VirtualFrame frame, Object cls, Object instance) {
            if (typeErrorProfile.profile(getBasesNode.execute(frame, cls) == null)) {
                throw raise(TypeError, "isinstance() arg 2 must be a type or tuple of types (was: %s)", instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(frame, instance);
            return instanceClass != null && abstractIsSubclassNode.execute(frame, instanceClass, cls);
        }
    }

    @Builtin(name = __SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();
        @Child private TypeNodes.IsSameTypeNode isSameTypeNode = TypeNodes.IsSameTypeNode.create();
        @Child private GetFixedAttributeNode getBasesAttrNode;
        @Child private GetLazyClassNode getClassNode;

        @CompilationFinal private IsBuiltinClassProfile isAttrErrorProfile;
        @CompilationFinal private IsBuiltinClassProfile isTupleProfile;

        @Specialization(guards = {"!isNativeClass(cls)", "!isNativeClass(derived)"})
        boolean doManagedManaged(VirtualFrame frame, LazyPythonClass cls, LazyPythonClass derived) {
            return isSameType(cls, derived) || isSubtypeNode.execute(frame, derived, cls);
        }

        @Specialization
        boolean doObjectObject(VirtualFrame frame, Object cls, Object derived,
                        @Cached("create()") TypeNodes.IsTypeNode isClsTypeNode,
                        @Cached("create()") TypeNodes.IsTypeNode isDerivedTypeNode) {
            if (isSameType(cls, derived)) {
                return true;
            }

            // no profiles required because IsTypeNode profiles already
            if (isClsTypeNode.execute(cls) && isDerivedTypeNode.execute(derived)) {
                return isSubtypeNode.execute(frame, derived, cls);
            }
            if (!checkClass(frame, derived)) {
                throw raise(PythonBuiltinClassType.TypeError, "issubclass() arg 1 must be a class");
            }
            if (!checkClass(frame, cls)) {
                throw raise(PythonBuiltinClassType.TypeError, "issubclass() arg 2 must be a class or tuple of classes");
            }
            return false;
        }

        // checks if object has '__bases__' (see CPython 'abstract.c' function
        // 'recursive_issubclass')
        private boolean checkClass(VirtualFrame frame, Object obj) {
            if (getBasesAttrNode == null || isAttrErrorProfile == null || isTupleProfile == null || getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBasesAttrNode = insert(GetFixedAttributeNode.create(SpecialAttributeNames.__BASES__));
                isAttrErrorProfile = IsBuiltinClassProfile.create();
                isTupleProfile = IsBuiltinClassProfile.create();
                getClassNode = insert(GetLazyClassNode.create());
            }
            Object basesObj;
            try {
                basesObj = getBasesAttrNode.executeObject(frame, obj);
            } catch (PException e) {
                e.expectAttributeError(isAttrErrorProfile);
                return false;
            }
            return isTupleProfile.profileClass(getClassNode.execute(basesObj), PythonBuiltinClassType.PTuple);
        }

        protected boolean isSameType(Object a, Object b) {
            return isSameTypeNode.execute(a, b);
        }
    }

    @Builtin(name = __SUBCLASSES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SubclassesNode extends PythonUnaryBuiltinNode {

        @Specialization
        PList getSubclasses(LazyPythonClass cls,
                        @Cached("create()") GetSubclassesNode getSubclassesNode) {
            // TODO: missing: keep track of subclasses
            return factory().createList(toArray(getSubclassesNode.execute(cls)));
        }

        @TruffleBoundary
        private static <T> Object[] toArray(Set<T> subclasses) {
            return subclasses.toArray();
        }
    }

    @GenerateNodeFactory
    @ImportStatic(NativeMemberNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class AbstractSlotNode extends PythonBinaryBuiltinNode {
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class NameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        String getName(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = {"isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object getName(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            return getName.execute(cls, __NAME__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __NAME__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getModule(PythonAbstractNativeObject cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMemberNames.TP_NAME);
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object getModule(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
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
        Object getModule(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            String module = cls.getType().getPublicInModule();
            return module == null ? "builtins" : module;
        }

        @Specialization(guards = "isNoValue(value)")
        Object getModule(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode readAttrNode) {
            Object module = readAttrNode.execute(cls, __MODULE__);
            if (module == PNone.NO_VALUE) {
                throw raise(AttributeError);
            }
            return module;
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setModule(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(cls, __MODULE__, value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        Object getModule(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMemberNames.TP_NAME);
            return getModuleName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
        }

        @TruffleBoundary
        private Object getModuleName(String fqname) {
            int firstDotIdx = fqname.indexOf('.');
            if (firstDotIdx != -1) {
                return fqname.substring(0, firstDotIdx);
            }
            return getBuiltinsName();
        }

        protected String getBuiltinsName() {
            return getCore().getBuiltins().getModuleName();
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class QualNameNode extends AbstractSlotNode {
        @Specialization(guards = "isNoValue(value)")
        String getName(PythonBuiltinClass cls, @SuppressWarnings("unused") PNone value) {
            return cls.getName();
        }

        @Specialization(guards = {"isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object getName(PythonClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            return getName.execute(cls, __QUALNAME__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __QUALNAME__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        String getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpNameNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            String tpName = (String) getTpNameNode.execute(cls, NativeMemberNames.TP_NAME);
            return getQualName(tpName);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonNativeClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
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
        Object getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getName.execute(cls, TYPE_DICTOFFSET);
            }
            return getName.execute(cls, __DICTOFFSET__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __DICTOFFSET__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMemberNames.TP_DICTOFFSET);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
        }
    }

    @Builtin(name = __ITEMSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class ItemsizeNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        Object getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getName.execute(cls, TYPE_ITEMSIZE);
            }
            return getName.execute(cls, __ITEMSIZE__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __ITEMSIZE__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMemberNames.TP_ITEMSIZE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
        }
    }

    @Builtin(name = __BASICSIZE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    abstract static class BasicsizeNode extends AbstractSlotNode {

        @Specialization(guards = "isNoValue(value)")
        Object getName(PythonManagedClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") IsBuiltinClassProfile profile,
                        @Cached("create()") ReadAttributeFromObjectNode getName) {
            // recursion anchor; since the metaclass of 'type' is 'type'
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getName.execute(cls, TYPE_BASICSIZE);
            }
            return getName.execute(cls, __BASICSIZE__);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PythonBuiltinClass cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of built-in/extension 'type'");
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPythonBuiltinClass(cls)"})
        Object setName(PythonClass cls, Object value,
                        @Cached("create()") WriteAttributeToObjectNode setName) {
            return setName.execute(cls, __BASICSIZE__, value);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getNative(PythonNativeClass cls, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") GetTypeMemberNode getTpDictoffsetNode) {
            return getTpDictoffsetNode.execute(cls, NativeMemberNames.TP_BASICSIZE);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setNative(@SuppressWarnings("unused") PythonAbstractNativeObject cls, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.RuntimeError, "can't set attributes of native type");
        }
    }

    @Builtin(name = "__flags__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FlagsNode extends PythonUnaryBuiltinNode {
        @Child TypeNodes.GetTypeFlagsNode getFlagsNode = TypeNodes.GetTypeFlagsNode.create();

        @Specialization
        Object flags(PythonAbstractClass self) {
            return getFlagsNode.execute(self);
        }
    }
}
