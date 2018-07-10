/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MRO__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__PREPARE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUBCLASSES__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory.CallNodeFactory;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
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
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = PythonClass.class)
public class TypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = SpecialMethodNames.__REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String repr(PythonClass self) {
            return self.toString();
        }
    }

    @Builtin(name = __MRO__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MroAttrNode extends PythonBuiltinNode {
        @Specialization
        Object doit(PythonClass klass) {
            return factory().createTuple(klass.getMethodResolutionOrder());
        }
    }

    @Builtin(name = "mro", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class MroNode extends PythonBuiltinNode {
        @Specialization
        Object doit(PythonClass klass) {
            PythonClass[] mro = klass.getMethodResolutionOrder();
            return factory().createList(Arrays.copyOf(mro, mro.length, Object[].class));
        }

        @Specialization(guards = "!isClass(object)")
        Object doit(Object object) {
            throw raise(TypeError, "descriptor 'mro' requires a 'type' object but received an '%p'", object);
        }
    }

    @Builtin(name = __CALL__, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child CallVarargsMethodNode dispatchNew = CallVarargsMethodNode.create();
        @Child LookupAttributeInMRONode lookupNew = LookupAttributeInMRONode.create(__NEW__);
        @Child CallVarargsMethodNode dispatchInit = CallVarargsMethodNode.create();
        @Child LookupAttributeInMRONode lookupInit = LookupAttributeInMRONode.create(__INIT__);
        @Child GetClassNode getClass = GetClassNode.create();
        @Child PositionalArgumentsNode createArgs = PositionalArgumentsNode.create();

        public static CallNode create() {
            return CallNodeFactory.create();
        }

        @Override
        public final Object varArgExecute(Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(PNone.NO_VALUE, arguments, keywords);
        }

        protected PythonClass first(Object[] ary) {
            return (PythonClass) ary[0];
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"first(arguments) == cachedSelf"})
        protected Object doItUnboxed(@SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords,
                        @Cached("first(arguments)") PythonClass cachedSelf) {
            return op(cachedSelf, arguments, keywords, false);
        }

        @Specialization(replaces = "doItUnboxed")
        protected Object doItUnboxedIndirect(@SuppressWarnings("unused") PNone noSelf, Object[] arguments, PKeyword[] keywords) {
            PythonClass self = (PythonClass) arguments[0];
            return op(self, arguments, keywords, false);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"})
        protected Object doIt(@SuppressWarnings("unused") PythonClass self, Object[] arguments, PKeyword[] keywords,
                        @Cached("self") PythonClass cachedSelf) {
            return op(cachedSelf, arguments, keywords, true);
        }

        @Specialization(replaces = "doIt")
        protected Object doItIndirect(PythonClass self, Object[] arguments, PKeyword[] keywords) {
            return op(self, arguments, keywords, true);
        }

        private Object op(PythonClass self, Object[] arguments, PKeyword[] keywords, boolean doCreateArgs) {
            Object newMethod = lookupNew.execute(self);
            if (newMethod != PNone.NO_VALUE) {
                CompilerAsserts.partialEvaluationConstant(doCreateArgs);
                Object[] newArgs = doCreateArgs ? createArgs.executeWithArguments(self, arguments) : arguments;
                Object newInstance = dispatchNew.execute(newMethod, newArgs, keywords);
                PythonClass newInstanceKlass = getClass.execute(newInstance);
                if (newInstanceKlass == self) {
                    if (self == getCore().getTypeClass() && arguments.length == 2) {
                        // do not call init if we are creating a new instance of type and we are
                        // passing keywords or more than one argument see:
                        // https://github.com/python/cpython/blob/2102c789035ccacbac4362589402ac68baa2cd29/Objects/typeobject.c#L3538
                    } else {
                        Object initMethod = lookupInit.execute(newInstanceKlass);
                        if (newMethod != PNone.NO_VALUE) {
                            Object[] initArgs;
                            if (doCreateArgs) {
                                initArgs = createArgs.executeWithArguments(newInstance, arguments);
                            } else {
                                // XXX: (tfel) is this valid? I think it should be fine...
                                arguments[0] = newInstance;
                                initArgs = arguments;
                            }
                            Object initResult = dispatchInit.execute(initMethod, initArgs, keywords);
                            if (initResult != PNone.NONE && initResult != PNone.NO_VALUE) {
                                throw raise(TypeError, "__init__() should return None");
                            }
                        }
                    }
                }
                return newInstance;
            } else {
                throw raise(TypeError, "cannot create '%s' instances", self.getName());
            }
        }
    }

    @Builtin(name = __GETATTRIBUTE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        public static GetattributeNode create() {
            return TypeBuiltinsFactory.GetattributeNodeFactory.create();
        }

        private final BranchProfile hasDescProfile = BranchProfile.create();
        private final BranchProfile isDescProfile = BranchProfile.create();
        private final BranchProfile hasValueProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();

        @Child private LookupAttributeInMRONode.Dynamic lookup = LookupAttributeInMRONode.Dynamic.create();
        private final ValueProfile typeProfile = ValueProfile.createIdentityProfile();
        @Child private GetClassNode getDataClassNode;
        @Child private GetClassNode getObjectClassNode;
        @Child private LookupInheritedAttributeNode valueGetLookup;
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode invokeGet;
        @Child private CallTernaryMethodNode invokeValueGet;
        @Child private LookupAttributeInMRONode.Dynamic lookupAsClass;

        @Specialization
        protected Object doIt(PythonClass object, Object key) {
            PythonClass type = typeProfile.profile(getObjectClass(object));
            Object descr = lookup.execute(type, key);
            PythonClass dataDescClass = null;
            Object get = null;
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                dataDescClass = getDataClass(descr);
                get = lookupGet(dataDescClass);
                if (get instanceof PythonCallable) {
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
                        return invokeGet.execute(get, descr, object, type);
                    }
                }
            }
            Object value = readAttribute(object, key);
            if (value != PNone.NO_VALUE) {
                hasValueProfile.enter();
                Object valueGet = lookupValueGet(value);
                if (valueGet == PNone.NO_VALUE) {
                    return value;
                } else if (valueGet instanceof PythonCallable) {
                    if (invokeValueGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeValueGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeValueGet.execute(valueGet, value, PNone.NONE, object);
                }
            }
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                if (get == PNone.NO_VALUE) {
                    return descr;
                } else if (get instanceof PythonCallable) {
                    if (invokeGet == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        invokeGet = insert(CallTernaryMethodNode.create());
                    }
                    return invokeGet.execute(get, descr, object, type);
                }
            }
            errorProfile.enter();
            throw raise(AttributeError, "type object '%s' has no attribute %s", object.getName(), key);
        }

        private Object readAttribute(Object object, Object key) {
            if (lookupAsClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAsClass = insert(LookupAttributeInMRONode.Dynamic.create());
            }
            return lookupAsClass.execute(object, key);
        }

        private Object lookupDelete(PythonClass dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupAttributeInMRONode.create(__DELETE__));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(PythonClass dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupAttributeInMRONode.create(__SET__));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        private Object lookupGet(PythonClass dataDescClass) {
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

        private PythonClass getDataClass(Object descr) {
            if (getDataClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDataClassNode = insert(GetClassNode.create());
            }
            return getDataClassNode.execute(descr);
        }

        private PythonClass getObjectClass(Object descr) {
            if (getObjectClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectClassNode = insert(GetClassNode.create());
            }
            return typeProfile.profile(getObjectClassNode.execute(descr));
        }

    }

    @Builtin(name = __PREPARE__, takesVariableArguments = true, takesVariableKeywords = true)
    @GenerateNodeFactory
    public static abstract class PrepareNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object doIt(Object args, Object kwargs) {
            return factory().createDict();
        }
    }

    @Builtin(name = __BASES__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class BasesNode extends PythonBuiltinNode {
        @Specialization
        Object bases(PythonClass self) {
            return factory().createTuple(self.getBaseClasses());
        }
    }

    @Builtin(name = __DICT__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dict(PythonClass self) {
            return factory().createMappingproxy(self);
        }
    }

    @Builtin(name = __INSTANCECHECK__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public static abstract class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Child private LookupAndCallBinaryNode getAttributeNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private AbstractObjectIsSubclassNode abstractIsSubclassNode = AbstractObjectIsSubclassNode.create();
        @Child private AbstractObjectGetBasesNode getBasesNode = AbstractObjectGetBasesNode.create();

        private ConditionProfile typeErrorProfile = ConditionProfile.createBinaryProfile();

        public static InstanceCheckNode create() {
            return TypeBuiltinsFactory.InstanceCheckNodeFactory.create();
        }

        private PythonObject getInstanceClassAttr(Object instance) {
            Object classAttr = getAttributeNode.executeObject(instance, __CLASS__);
            if (classAttr instanceof PythonObject) {
                return (PythonObject) classAttr;
            }
            return null;
        }

        public abstract boolean executeWith(Object cls, Object instance);

        @Specialization
        public boolean isInstance(PythonClass cls, Object instance,
                        @Cached("create()") IsSubtypeNode isSubtypeNode) {
            if (instance instanceof PythonObject && isSubtypeNode.execute(((PythonObject) instance).getPythonClass(), cls)) {
                return true;
            }

            Object instanceClass = getAttributeNode.executeObject(instance, __CLASS__);
            return instanceClass instanceof PythonClass && isSubtypeNode.execute(instanceClass, cls);
        }

        @Fallback
        public boolean isInstance(Object cls, Object instance) {
            if (typeErrorProfile.profile(getBasesNode.execute(cls) == null)) {
                throw raise(TypeError, "isinstance() arg 2 must be a type or tuple of types (was: %s)", instance);
            }

            PythonObject instanceClass = getInstanceClassAttr(instance);
            return instanceClass != null && abstractIsSubclassNode.execute(instanceClass, cls);
        }
    }

    @Builtin(name = __SUBCLASSCHECK__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    static abstract class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();

        @Specialization
        boolean instanceCheck(PythonClass cls, Object derived) {
            return cls == derived || isSubtypeNode.execute(derived, cls);
        }
    }

    @Builtin(name = __SUBCLASSES__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    static abstract class SubclassesNode extends PythonUnaryBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode = IsSubtypeNode.create();

        @Specialization
        PList getSubclasses(PythonClass cls) {
            // TODO: missing: keep track of subclasses
            return factory().createList(cls.getSubClasses().toArray());
        }
    }
}
