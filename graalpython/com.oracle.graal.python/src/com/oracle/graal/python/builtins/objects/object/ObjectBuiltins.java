/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
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
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.GetAttributeNodeFactory;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
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
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
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
        @Child private TypeNodes.GetNameNode getTypeNameNode;

        private static final String ERROR_MESSAGE = "__class__ assignment only supported for heap types or ModuleType subclasses";

        @Specialization(guards = "isNoValue(value)")
        Object getClass(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(self);
        }

        @Specialization
        Object setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonBuiltinClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        Object setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonNativeClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        PNone setClass(PythonObject self, PythonAbstractClass value,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib1,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib2,
                        @Cached("create()") BranchProfile errorValueBranch,
                        @Cached("create()") BranchProfile errorSelfBranch,
                        @Cached("create()") BranchProfile errorSlotsBranch) {
            if (value instanceof PythonBuiltinClass || PGuards.isNativeClass(value)) {
                errorValueBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            Object lazyClass = lib1.getLazyPythonClass(self);
            if (lazyClass instanceof PythonBuiltinClassType || lazyClass instanceof PythonBuiltinClass || PGuards.isNativeClass(lazyClass)) {
                errorSelfBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            Object selfSlots = getLookupSlotsInSelf().execute(lazyClass);
            if (selfSlots != PNone.NO_VALUE) {
                Object otherSlots = getLookupSlotsInOther().execute(value);
                if (otherSlots == PNone.NO_VALUE || !lib2.equals(selfSlots, otherSlots, lib2)) {
                    errorSlotsBranch.enter();
                    throw raise(TypeError, ErrorMessages.CLASS_ASIGMENT_D_LAYOUT_DIFFERS_FROM_S, getTypeName(value), getTypeName(lazyClass));
                }
            }
            lib1.setLazyPythonClass(self, value);
            return PNone.NONE;
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
        Object getClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonAbstractClass value) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Fallback
        Object getClass(@SuppressWarnings("unused") Object self, Object value) {
            throw raise(TypeError, ErrorMessages.CLASS_MUST_BE_SET_TO_CLASS, value);
        }

        private String getTypeName(Object clazz) {
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
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
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
                        @Cached ConditionProfile isEq,
                        @Cached IsNode isNode) {
            if (isEq.profile(isNode.execute(self, other))) {
                return true;
            } else {
                // Return NotImplemented instead of False, so if two objects are compared, both get
                // a chance at the comparison
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Child private LookupAndCallBinaryNode eqNode;
        @Child private CoerceToBooleanNode ifFalseNode;

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
                ifFalseNode = insert(CoerceToBooleanNode.createIfFalseNode());
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
            Object type = getClass.execute(self);
            Object moduleName = readModuleNode.executeObject(frame, type);
            Object qualName = readQualNameNode.executeObject(frame, type);
            if (moduleName != PNone.NO_VALUE && !BuiltinNames.BUILTINS.equals(moduleName)) {
                return strFormat("<%s.%s object at 0x%x>", moduleName, qualName, System.identityHashCode(self));
            }
            return strFormat("<%s object at 0x%x>", qualName, System.identityHashCode(self));
        }

        @TruffleBoundary
        private static String strFormat(String fmt, Object... objects) {
            return String.format(fmt, objects);
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
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode dispatchGet;
        @Child private ReadAttributeFromObjectNode attrRead;

        @Specialization
        protected Object doIt(VirtualFrame frame, Object object, Object key,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib) {
            Object type = lib.getLazyPythonClass(object);
            Object descr = lookup.execute(type, key);
            Object dataDescClass = null;
            if (descr != PNone.NO_VALUE) {
                hasDescProfile.enter();
                dataDescClass = lib.getLazyPythonClass(descr);
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
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
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

        private Object lookupGet(Object dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupAttributeInMRONode.create(__GET__));
            }
            return lookupGetNode.execute(dataDescClass);
        }

        private Object lookupDelete(Object dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupAttributeInMRONode.create(__DELETE__));
            }
            return lookupDeleteNode.execute(dataDescClass);
        }

        private Object lookupSet(Object dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupAttributeInMRONode.create(__SET__));
            }
            return lookupSetNode.execute(dataDescClass);
        }

        public static GetAttributeNode create() {
            return GetAttributeNodeFactory.create();
        }
    }

    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization(limit = "3")
        protected PNone doIt(VirtualFrame frame, Object object, Object key, Object value,
                        @CachedLibrary("object") PythonObjectLibrary libObj,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create(__SET__)") LookupAttributeInMRONode lookupSetNode,
                        @Cached("create()") CallTernaryMethodNode callSetNode,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            Object type = libObj.getLazyPythonClass(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDataClassNode.execute(descr);
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
                throw raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raise(AttributeError, ErrorMessages.HAS_NO_ATTR, object, key);
            }
        }
    }

    @Builtin(name = __DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected PNone doIt(VirtualFrame frame, Object object, Object key,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create(__DELETE__)") LookupAttributeInMRONode lookupDeleteNode,
                        @Cached("create()") CallBinaryMethodNode callSetNode,
                        @Cached("create()") ReadAttributeFromObjectNode attrRead,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            Object type = lib.getLazyPythonClass(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDataClassNode.execute(descr);
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
                throw raise(AttributeError, ErrorMessages.ATTR_S_READONLY, key);
            } else {
                throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
            }
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {
        @Child private IsBuiltinClassProfile exactObjInstanceProfile = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile exactBuiltinInstanceProfile = IsBuiltinClassProfile.create();

        protected boolean isExactObjectInstance(PythonObject self) {
            return exactObjInstanceProfile.profileObject(self, PythonBuiltinClassType.PythonObject);
        }

        protected boolean isBuiltinObjectExact(PythonObject self) {
            // any builtin class except Modules
            return exactBuiltinInstanceProfile.profileIsOtherBuiltinObject(self, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self, iLib)", "!isExactObjectInstance(self)", "isNoValue(none)"}, limit = "1")
        Object dict(PythonObject self, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @SuppressWarnings("unused") @CachedLibrary("self") InteropLibrary iLib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self, iLib)", "!isExactObjectInstance(self)"}, limit = "1")
        Object dict(PythonObject self, PDict dict,
                        @CachedLibrary("self") PythonObjectLibrary lib,
                        @SuppressWarnings("unused") @CachedLibrary("self") InteropLibrary iLib) {
            try {
                lib.setDict(self, dict);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)", limit = "1")
        Object dict(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PHashingCollection dict = lib.getDict(self);
            if (dict == null) {
                raise(self, none);
            }
            return dict;
        }

        @Fallback
        Object raise(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, "__dict__");
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
            throw raise(TypeError, ErrorMessages.FORMAT_SPEC_MUST_BE_STRING);
        }
    }

    @Builtin(name = RICHCMP, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends PythonTernaryBuiltinNode {
        protected static final int NO_SLOW_PATH = Integer.MAX_VALUE;
        @CompilationFinal private boolean seenNonBoolean = false;

        protected BinaryComparisonNode createOp(String op) {
            return (BinaryComparisonNode) PythonLanguage.getCurrent().getNodeFactory().createComparisonOperation(op, null, null);
        }

        @Specialization(guards = "op.equals(cachedOp)", limit = "NO_SLOW_PATH")
        boolean richcmp(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") String op,
                        @SuppressWarnings("unused") @Cached("op") String cachedOp,
                        @Cached("createOp(op)") BinaryComparisonNode node,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode) {
            if (!seenNonBoolean) {
                try {
                    return node.executeBool(frame, left, right);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    seenNonBoolean = true;
                    return castToBooleanNode.executeBoolean(frame, e.getResult());
                }
            } else {
                return castToBooleanNode.executeBoolean(frame, node.executeWith(frame, left, right));
            }
        }
    }

    @Builtin(name = __INIT_SUBCLASS__, minNumOfPositionalArgs = 1, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class InitSubclass extends PythonUnaryBuiltinNode {
        @Specialization
        PNone initSubclass(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }
}
