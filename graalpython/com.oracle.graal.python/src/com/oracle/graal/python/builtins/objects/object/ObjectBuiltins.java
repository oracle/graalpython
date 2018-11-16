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

package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
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
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
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
        private static final String ERROR_MESSAGE = "__class__ assignment only supported for heap types or ModuleType subclasses";

        @Specialization(guards = "isNoValue(value)")
        PythonClass getClass(Object self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(self);
        }

        @Specialization
        PythonClass setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonBuiltinClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        PythonClass setClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonNativeClass klass) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Specialization
        PNone setClass(PythonObject self, PythonClass value,
                        @Cached("create()") BranchProfile errorValueBranch,
                        @Cached("create()") BranchProfile errorSelfBranch,
                        @Cached("create()") GetLazyClassNode getLazyClass) {
            if (value instanceof PythonBuiltinClass || value instanceof PythonNativeClass) {
                errorValueBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            LazyPythonClass lazyClass = getLazyClass.execute(self);
            if (lazyClass instanceof PythonBuiltinClassType || lazyClass instanceof PythonBuiltinClass || lazyClass instanceof PythonNativeClass) {
                errorSelfBranch.enter();
                throw raise(TypeError, ERROR_MESSAGE);
            }
            self.setLazyPythonClass(value);
            return PNone.NONE;
        }

        @Specialization(guards = "!isPythonObject(self)")
        PythonClass getClass(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") PythonClass value) {
            throw raise(TypeError, ERROR_MESSAGE);
        }

        @Fallback
        PythonClass getClass(@SuppressWarnings("unused") Object self, Object value) {
            throw raise(TypeError, "__class__ must be set to a class, not '%p' object", value);
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

    @Builtin(name = __HASH__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        public int hash(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(PythonNativeObject self, PythonNativeObject other,
                        @Cached("create()") CExtNodes.IsNode nativeIsNode) {
            return nativeIsNode.execute(self, other);
        }

        @Fallback
        public Object eq(Object self, Object other) {
            return self == other;
        }
    }

    @Builtin(name = __NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Child private LookupAndCallBinaryNode eqNode;
        @Child private CastToBooleanNode ifFalseNode;

        @Specialization
        public boolean ne(PythonNativeObject self, PythonNativeObject other,
                        @Cached("create()") CExtNodes.IsNode nativeIsNode) {
            return !nativeIsNode.execute(self, other);
        }

        @Fallback
        public Object ne(Object self, Object other) {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(LookupAndCallBinaryNode.create(__EQ__));
            }
            Object result = eqNode.executeObject(self, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            if (ifFalseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ifFalseNode = insert(CastToBooleanNode.createIfFalseNode());
            }
            return ifFalseNode.executeWith(result);
        }
    }

    @Builtin(name = __STR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(Object self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode) {
            return reprNode.executeObject(self);
        }
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String repr(Object self,
                        @Cached("create()") GetClassNode getClass,
                        @Cached("create()") ReadAttributeFromObjectNode readModuleNode,
                        @Cached("create()") ReadAttributeFromObjectNode readQualNameNode) {
            if (self == PNone.NONE) {
                return "None";
            }
            PythonClass type = getClass.execute(self);
            Object moduleName = readModuleNode.execute(type, __MODULE__);
            Object qualName = readQualNameNode.execute(type, __QUALNAME__);
            if (moduleName != PNone.NO_VALUE && !moduleName.equals(getCore().getBuiltins().getModuleName())) {
                return String.format("<%s.%s object at 0x%x>", moduleName, qualName, self.hashCode());
            }
            return String.format("<%s object at 0x%x>", qualName, self.hashCode());
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Child private LookupAndCallUnaryNode callLenNode;
        @Child private LookupAndCallBinaryNode callEqNode;

        @Specialization
        public boolean doGeneric(Object self) {
            assert self != PNone.NO_VALUE;
            if (self == PNone.NONE) {
                return false;
            }
            Object len = getCallLenNode().executeObject(self);
            if (len != PNone.NO_VALUE) {
                try {
                    return getCallEqNode().executeBool(0, len);
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

    @Builtin(name = __GETATTRIBUTE__, fixedNumOfPositionalArgs = 2)
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
        @Child private LookupAndCallBinaryNode getattrNode;

        @Specialization
        protected Object doIt(Object object, Object key) {
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
                    if (get instanceof PythonCallable) {
                        // Only override if __get__ is defined, too, for compatibility with CPython.
                        return dispatch(object, getPythonClass(type, getClassProfile), descr, get);
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
                } else if (get instanceof PythonCallable) {
                    return dispatch(object, getPythonClass(type, getClassProfile), descr, get);
                }
            }
            errorProfile.enter();
            return fallbackGetattr(object, key);
        }

        private Object fallbackGetattr(Object object, Object key) {
            if (getattrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getattrNode = insert(LookupAndCallBinaryNode.create(SpecialMethodNames.__GETATTR__));
            }
            return getattrNode.executeObject(object, key);
        }

        private Object readAttribute(Object object, Object key) {
            if (attrRead == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attrRead = insert(ReadAttributeFromObjectNode.create());
            }
            return attrRead.execute(object, key);
        }

        private Object dispatch(Object object, Object type, Object descr, Object get) {
            if (dispatchGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatchGet = insert(CallTernaryMethodNode.create());
            }
            return dispatchGet.execute(get, descr, typeIsObjectProfile.profile(type == object) ? PNone.NONE : object, type);
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
    }

    @Builtin(name = SpecialMethodNames.__GETATTR__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattr(Object object, Object key) {
            throw raise(AttributeError, "'%p' object has no attribute '%s'", object, key);
        }
    }

    @Builtin(name = SpecialMethodNames.__SETATTR__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, Object key, Object value,
                        @Cached("create()") GetLazyClassNode getObjectClassNode,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create(__SET__)") LookupAttributeInMRONode lookupSetNode,
                        @Cached("create()") CallTernaryMethodNode callSetNode,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            LazyPythonClass type = getObjectClassNode.execute(object);
            Object descr = getExisting.execute(type, key);
            if (descr != PNone.NO_VALUE) {
                PythonClass dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupSetNode.execute(dataDescClass);
                if (set instanceof PythonCallable) {
                    callSetNode.execute(set, descr, object, value);
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

    @Builtin(name = __DELATTR__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doIt(Object object, Object key,
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
                PythonClass dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupDeleteNode.execute(dataDescClass);
                if (set instanceof PythonCallable) {
                    callSetNode.executeObject(set, descr, object);
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
    static abstract class DictNode extends PythonBinaryBuiltinNode {
        private final IsBuiltinClassProfile exactObjInstanceProfile = IsBuiltinClassProfile.create();
        private final IsBuiltinClassProfile exactBuiltinInstanceProfile = IsBuiltinClassProfile.create();
        @Child private Node readNode;

        protected boolean isExactObjectInstance(PythonObject self) {
            return exactObjInstanceProfile.profileObject(self, PythonBuiltinClassType.PythonObject);
        }

        protected boolean isBuiltinObjectExact(PythonObject self) {
            // any builtin class except Modules
            return exactBuiltinInstanceProfile.profileIsOtherBuiltinObject(self, PythonBuiltinClassType.PythonModule);
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self)", "!isExactObjectInstance(self)", "isNoValue(none)"})
        Object dict(PythonObject self, @SuppressWarnings("unused") PNone none) {
            PHashingCollection dict = self.getDict();
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                self.setDict(dict);
            }
            return dict;
        }

        @Specialization(guards = {"!isBuiltinObjectExact(self)", "!isClass(self)", "!isExactObjectInstance(self)"})
        Object dict(PythonObject self, PDict dict) {
            self.getDictUnsetOrSameAsStorageAssumption().invalidate();
            self.setDict(dict);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)")
        Object dict(PythonNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Cached("create(__DICTOFFSET__)") LookupInheritedAttributeNode getDictoffset,
                        @Cached("create()") BranchProfile noOffset,
                        @Cached("create()") BranchProfile wrongType,
                        @Cached("create()") CExtNodes.ToJavaNode toJava) {
            Object dictoffset = getDictoffset.execute(self);
            int offset;
            if (dictoffset instanceof Long) {
                offset = ((Long) dictoffset).intValue();
            } else if (dictoffset instanceof Integer) {
                offset = (Integer) dictoffset;
            } else if (dictoffset instanceof PInt) {
                offset = ((PInt) dictoffset).intValue();
            } else if (dictoffset instanceof PNone) {
                noOffset.enter();
                throw raise(AttributeError, "'%p' object has no attribute '__dict__'", self);
            } else {
                wrongType.enter();
                throw raise(TypeError, "tp_dictoffset of native type is not an integer, got '%p'", dictoffset);
            }
            if (readNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNode = insert(Message.READ.createNode());
            }
            try {
                return toJava.execute(ForeignAccess.sendRead(readNode, self.object, offset));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(AttributeError, "'%p' object has no native '__dict__'", self);
            }
        }

        @Fallback
        Object dict(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(AttributeError, "'%p' object has no attribute '__dict__'", self);
        }

    }

    @Builtin(name = __FORMAT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    static abstract class FormatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isString(formatString)")
        Object format(Object self, @SuppressWarnings("unused") Object formatString,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode strCall) {
            return strCall.executeObject(self);
        }

        @Fallback
        Object formatFail(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object formatSpec) {
            throw raise(TypeError, "format_spec must be a string");
        }
    }

    @Builtin(name = RICHCMP, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    static abstract class RichCompareNode extends PythonTernaryBuiltinNode {
        protected static final int NO_SLOW_PATH = Integer.MAX_VALUE;

        protected BinaryComparisonNode createOp(String op) {
            return (BinaryComparisonNode) getContext().getLanguage().getNodeFactory().createComparisonOperation(op, null, null);
        }

        @Specialization(guards = "op.equals(cachedOp)", limit = "NO_SLOW_PATH")
        boolean richcmp(Object left, Object right, @SuppressWarnings("unused") String op,
                        @SuppressWarnings("unused") @Cached("op") String cachedOp,
                        @Cached("createOp(op)") BinaryComparisonNode node) {
            return node.executeBool(left, right);
        }
    }
}
