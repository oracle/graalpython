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

package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.GetAttributeNodeFactory;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonObject)
public class ObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = __CLASS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ClassNode extends PythonBinaryBuiltinNode {
        @Child private LookupAttributeInMRONode lookupSlotsInSelf;
        @Child private LookupAttributeInMRONode lookupSlotsInOther;
        @Child private BinaryComparisonNode slotsAreEqual;
        @Child private TypeNodes.GetNameNode getTypeNameNode;

        private static final String ERROR_MESSAGE = "__class__ assignment only supported for heap types or ModuleType subclasses";

        @Specialization(guards = "isNoValue(value)")
        PythonAbstractClass getClass(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(self);
        }

        @Specialization
        LazyPythonClass setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonBuiltinClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        LazyPythonClass setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonNativeClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        PNone setClass(VirtualFrame frame, PythonObject self, PythonAbstractClass value,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @Cached("create()") BranchProfile errorValueBranch,
                        @Cached("create()") BranchProfile errorSelfBranch,
                        @Cached("create()") BranchProfile errorSlotsBranch,
                        @Cached("create()") GetLazyClassNode getLazyClass) {
            if (value instanceof PythonBuiltinClass || PGuards.isNativeClass(value)) {
                errorValueBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            LazyPythonClass lazyClass = getLazyClass.execute(self);
            if (lazyClass instanceof PythonBuiltinClassType || lazyClass instanceof PythonBuiltinClass || PGuards.isNativeClass(lazyClass)) {
                errorSelfBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            Object selfSlots = getLookupSlotsInSelf().execute(lazyClass);
            if (selfSlots != PNone.NO_VALUE) {
                Object otherSlots = getLookupSlotsInOther().execute(value);
                if (otherSlots == PNone.NO_VALUE || !getSlotsAreEqual().executeBool(frame, selfSlots, otherSlots)) {
                    errorSlotsBranch.enter();
                    throw raise(TypeError, "__class__ assignment: '%s' object layout differs from '%s'", getTypeName(value), getTypeName(lazyClass));
                }
            }
            lib.setLazyPythonClass(self, value);
            return PNone.NONE;
        }

        private BinaryComparisonNode getSlotsAreEqual() {
            if (slotsAreEqual == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slotsAreEqual = insert(BinaryComparisonNode.create(__EQ__, null, "=="));
            }
            return slotsAreEqual;
        }

        private LookupAttributeInMRONode getLookupSlotsInSelf() {
            if (lookupSlotsInSelf == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSlotsInSelf = insert(LookupAttributeInMRONode.create(__SLOTS__));
            }
            return lookupSlotsInSelf;
        }

        private LookupAttributeInMRONode getLookupSlotsInOther() {
            if (lookupSlotsInOther == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSlotsInOther = insert(LookupAttributeInMRONode.create(__SLOTS__));
            }
            return lookupSlotsInOther;
        }

        @Specialization(guards = "!isPythonObject(self)")
        LazyPythonClass getClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonAbstractClass value) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Fallback
        LazyPythonClass getClass(@SuppressWarnings("unused") Object self, Object value) {
            throw raise(TypeError, "__class__ must be set to a class, not '%p' object", value);
        }

        private String getTypeName(LazyPythonClass clazz) {
            if (getTypeNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getTypeNameNode.execute(clazz);
        }
    }

    @Builtin(name = __INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Override
        public final Object varArgExecute(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        public PNone init(Object self, Object[] arguments, PKeyword[] keywords) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        public int hash(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object eq(Object self, Object other,
                        @Cached IsNode isNode) {
            return isNode.execute(self, other);
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Child private LookupAndCallBinaryNode eqNode;
        @Child private CastToBooleanNode ifFalseNode;

        @Specialization
        boolean ne(PythonAbstractNativeObject self, PythonAbstractNativeObject other,
                        @Cached CExtNodes.PointerCompareNode nativeNeNode) {
            return nativeNeNode.execute(__NE__, self, other);
        }

        @Fallback
        Object ne(VirtualFrame frame, Object self, Object other) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(LookupAndCallBinaryNode.create(__EQ__));
            }
            Object result = eqNode.executeObject(frame, self, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            if (ifFalseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ifFalseNode = insert(CastToBooleanNode.createIfFalseNode());
            }
            return ifFalseNode.executeBoolean(frame, result);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(VirtualFrame frame, Object self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode) {
            return reprNode.executeObject(frame, self);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialAttributeNames.class)
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        String repr(VirtualFrame frame, Object self,
                        @Cached("create()") GetClassNode getClass,
                        @Cached("create(__MODULE__)") GetFixedAttributeNode readModuleNode,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            if (self == PNone.NONE) {
                return "None";
            }
            PythonAbstractClass type = getClass.execute(self);
            Object moduleName = readModuleNode.executeObject(frame, type);
            Object qualName = readQualNameNode.executeObject(frame, type);
            if (moduleName != PNone.NO_VALUE && !moduleName.equals(getCore().getBuiltins().getModuleName())) {
                return strFormat("<%s.%s object at 0x%x>", moduleName, qualName, self.hashCode());
            }
            return strFormat("<%s object at 0x%x>", qualName, self.hashCode());
        }

        @TruffleBoundary
        private static String strFormat(String fmt, Object... objects) {
            return String.format(fmt, objects);
        }
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Child private LookupAndCallUnaryNode callLenNode;
        @Child private LookupAndCallBinaryNode callEqNode;

        @Specialization
        public boolean doGeneric(VirtualFrame frame, Object self) {
            assert self != PNone.NO_VALUE;
            if (self == PNone.NONE) {
                return false;
            }
            Object len = getCallLenNode().executeObject(frame, self);
            if (len != PNone.NO_VALUE) {
                try {
                    return getCallEqNode().executeBool(frame, 0, len);
                } catch (UnexpectedResultException e) {
                    throw raise(TypeError, "'%p' object cannot be interpreted as an integer", len);
                }
            }
            return true;
        }

        private LookupAndCallUnaryNode getCallLenNode() {
            if (callLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callLenNode = insert(LookupAndCallUnaryNode.create(__LEN__));
            }
            return callLenNode;
        }

        private LookupAndCallBinaryNode getCallEqNode() {
            if (callEqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callEqNode = insert(LookupAndCallBinaryNode.create(__NE__, __NE__));
            }
            return callEqNode;
        }
    }

    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends PythonBinaryBuiltinNode {
        private final BranchProfile hasDescProfile = BranchProfile.create();
        private final BranchProfile isDescProfile = BranchProfile.create();
        private final BranchProfile hasValueProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile typeIsObjectProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile getClassProfile = ConditionProfile.createBinaryProfile();

        @Child private LookupAttributeInMRONode.Dynamic lookup = LookupAttributeInMRONode.Dynamic.create();
        @Child private GetLazyClassNode getObjectClassNode = GetLazyClassNode.create();
        @Child private GetLazyClassNode getDataClassNode;
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode dispatchGet;
        @Child private ReadAttributeFromObjectNode attrRead;

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object key) {
            LazyPythonClass type = getObjectClassNode.execute(object);
            Object descr = lookup.execute(type, key);
            LazyPythonClass dataDescClass = null;
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                dataDescClass = getDataClass(descr);
                Object delete = PNone.NO_VALUE;
                Object set = lookupSet(dataDescClass);
                if (set == PNone.NO_VALUE) {
                    delete = lookupDelete(dataDescClass);
                }
                if (set != PNone.NO_VALUE || delete != PNone.NO_VALUE) {
                    isDescProfile.enter();
                    Object get = lookupGet(dataDescClass);
                    if (PGuards.isCallable(get)) {
                        // Only override if __get__ is defined, too, for compatibility with CPython.
                        return dispatch(frame, object, getPythonClass(type, getClassProfile), descr, get);
                    }
                }
            }
            Object value = readAttribute(object, key);
            if (value != PNone.NO_VALUE) {
                hasValueProfile.enter();
                return value;
            }
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                if (object == PNone.NONE) {
                    if (descr instanceof PBuiltinFunction) {
                        // Special case for None object. We cannot call function.__get__(None,
                        // type(None)),
                        // because that would return an unbound method
                        return factory().createBuiltinMethod(PNone.NONE, (PBuiltinFunction) descr);
                    }
                }
                Object get = lookupGet(dataDescClass);
                if (get == PNone.NO_VALUE) {
                    return descr;
                } else if (PGuards.isCallable(get)) {
                    return dispatch(frame, object, getPythonClass(type, getClassProfile), descr, get);
                }
            }
            errorProfile.enter();
            throw raise(AttributeError, "'%p' object has no attribute '%s'", object, key);
        }

        private Object readAttribute(Object object, Object key) {
            if (attrRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attrRead = insert(ReadAttributeFromObjectNode.create());
            }
            return attrRead.execute(object, key);
        }

        private Object dispatch(VirtualFrame frame, Object object, Object type, Object descr, Object get) {
            if (dispatchGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatchGet = insert(CallTernaryMethodNode.create());
            }
            return dispatchGet.execute(frame, get, descr, typeIsObjectProfile.profile(type == object) ? PNone.NONE : object, type);
        }

        private Object lookupGet(LazyPythonClass dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupAttributeInMRONode.create(__GET__));
            }
            return lookupGetNode.execute(dataDescClass);
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

        private LazyPythonClass getDataClass(Object descr) {
            if (getDataClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDataClassNode = insert(GetLazyClassNode.create());
            }
            return getDataClassNode.execute(descr);
        }

        public static GetAttributeNode create() {
            return GetAttributeNodeFactory.create();
        }
    }

    @Builtin(name = __GETATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattr(Object object, Object key) {
            throw raise(AttributeError, "'%p' object has no attribute '%s'", object, key);
        }
    }

    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected PNone doIt(VirtualFrame frame, Object object, Object key, Object value,
                        @Cached("create()") GetLazyClassNode getObjectClassNode,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create(__SET__)") LookupAttributeInMRONode lookupSetNode,
                        @Cached("create()") CallTernaryMethodNode callSetNode,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            LazyPythonClass type = getObjectClassNode.execute(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                PythonAbstractClass dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupSetNode.execute(dataDescClass);
                if (PGuards.isCallable(set)) {
                    callSetNode.execute(frame, set, descr, object, value);
                    return PNone.NONE;
                }
            }
            if (writeNode.execute(object, key, value)) {
                return PNone.NONE;
            }
            if (descr != PNone.NO_VALUE) {
                throw raise(AttributeError, "attribute %s is read-only", key);
            } else {
                throw raise(AttributeError, "%s has no attribute %s", object, key);
            }
        }
    }

    @Builtin(name = __DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doIt(VirtualFrame frame, Object object, Object key,
                        @Cached("create()") GetLazyClassNode getObjectClassNode,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create(__DELETE__)") LookupAttributeInMRONode lookupDeleteNode,
                        @Cached("create()") CallBinaryMethodNode callSetNode,
                        @Cached("create()") ReadAttributeFromObjectNode attrRead,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            LazyPythonClass type = getObjectClassNode.execute(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                PythonAbstractClass dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupDeleteNode.execute(dataDescClass);
                if (PGuards.isCallable(set)) {
                    callSetNode.executeObject(frame, set, descr, object);
                    return PNone.NONE;
                }
            }
            Object currentValue = attrRead.execute(object, key);
            if (currentValue != PNone.NO_VALUE) {
                if (writeNode.execute(object, key, PNone.NO_VALUE)) {
                    return PNone.NONE;
                }
            }
            if (descr != PNone.NO_VALUE) {
                throw raise(AttributeError, "attribute % is read-only", key);
            } else {
                throw raise(AttributeError, "%s object has no attribute '%s'", object, key);
            }
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {
        private final IsBuiltinClassProfile exactObjInstanceProfile = IsBuiltinClassProfile.create();
        private final IsBuiltinClassProfile exactBuiltinInstanceProfile = IsBuiltinClassProfile.create();

        protected boolean isExactObjectInstance(PythonObject self) {
            return exactObjInstanceProfile.profileObject(self, PythonBuiltinClassType.PythonObject);
        }

        protected boolean isBuiltinObjectExact(PythonObject self) {
            // any builtin class except Modules
            return exactBuiltinInstanceProfile.profileIsOtherBuiltinObject(self, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self)", "!isExactObjectInstance(self)", "isNoValue(none)"}, limit = "1")
        Object dict(PythonObject self, @SuppressWarnings("unused") PNone none,
                    @CachedLibrary("self") PythonObjectLibrary lib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self)", "!isExactObjectInstance(self)"}, limit = "1")
        Object dict(PythonObject self, PDict dict,
                    @CachedLibrary("self") PythonObjectLibrary lib) {
            try {
                lib.setDict(self, dict);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        Object dict(PythonNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") CExtNodes.GetObjectDictNode getDictNode) {
            Object dict = getDictNode.execute(self);
            if (dict == PNone.NO_VALUE) {
                raise(self, none);
            }
            return dict;
        }

        @Fallback
        Object raise(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(AttributeError, "'%p' object has no attribute '__dict__'", self);
        }

    }

    @Builtin(name = __FORMAT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FormatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isString(formatString)")
        Object format(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object formatString,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode strCall) {
            return strCall.executeObject(frame, self);
        }

        @Fallback
        Object formatFail(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object formatSpec) {
            throw raise(TypeError, "format_spec must be a string");
        }
    }

    @Builtin(name = RICHCMP, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {
        protected static final int NO_SLOW_PATH = Integer.MAX_VALUE;

        protected BinaryComparisonNode createOp(String op) {
            return (BinaryComparisonNode) PythonLanguage.getCurrent().getNodeFactory().createComparisonOperation(op, null, null);
        }

        @Specialization(guards = "op.equals(cachedOp)", limit = "NO_SLOW_PATH")
        boolean richcmp(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") String op,
                        @SuppressWarnings("unused") @Cached("op") String cachedOp,
                        @Cached("createOp(op)") BinaryComparisonNode node) {
            return node.executeBool(frame, left, right);
        }
    }

    @Builtin(name = __INIT_SUBCLASS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InitSubclass extends PythonUnaryBuiltinNode {
        @Specialization
        PNone initSubclass(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }
}
