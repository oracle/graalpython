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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
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
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
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

@CoreFunctions(extendClasses = PythonObject.class)
public class ObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return ObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = __CLASS__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClassNode extends PythonBuiltinNode {
        @Specialization
        Object getClass(Object self,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(self);
        }
    }

    @Builtin(name = __INIT__, takesVariableArguments = true, minNumOfArguments = 1, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {
        @Override
        public final Object execute(Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return PNone.NONE;
        }

        @Specialization
        @SuppressWarnings("unused")
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __HASH__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        public int hash(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(Object self, Object other) {
            return self == other;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(Object self, Object other) {
            return self != other;
        }
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(Object self,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode) {
            return reprNode.executeObject(self);
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object repr(PythonNativeObject self,
                        @Cached("create()") GetClassNode getClass) {
            return "<" + getClass.execute(self).getName() + " object at 0x" + self.hashCode() + ">";
        }

        @Fallback
        public Object repr(Object self) {
            return self.toString();
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        public boolean repr(Object self) {
            assert self != PNone.NO_VALUE;
            return self != PNone.NONE;
        }
    }

    @Builtin(name = __GETATTRIBUTE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        private final BranchProfile hasDescProfile = BranchProfile.create();
        private final BranchProfile isDescProfile = BranchProfile.create();
        private final BranchProfile hasValueProfile = BranchProfile.create();
        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile typeIsObjectProfile = ConditionProfile.createBinaryProfile();

        @Child private LookupInheritedAttributeNode lookup = LookupInheritedAttributeNode.create();
        private final ValueProfile typeProfile = ValueProfile.createIdentityProfile();
        @Child private GetClassNode getObjectClassNode;
        @Child private GetClassNode getDataClassNode;
        @Child private LookupAttributeInMRONode lookupGetNode;
        @Child private LookupAttributeInMRONode lookupSetNode;
        @Child private LookupAttributeInMRONode lookupDeleteNode;
        @Child private CallTernaryMethodNode dispatchGet;
        @Child private ReadAttributeFromObjectNode attrRead;
        @Child private LookupAndCallBinaryNode getattrNode;

        @Specialization
        protected Object doIt(Object object, Object key) {
            Object descr = lookup.execute(object, key);
            PythonClass dataDescClass = null;
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
                        return dispatch(object, descr, get);
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
                    return dispatch(object, descr, get);
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

        private Object dispatch(Object object, Object descr, Object get) {
            if (dispatchGet == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatchGet = insert(CallTernaryMethodNode.create());
            }
            PythonClass type = getObjectClass(object);
            return dispatchGet.execute(get, descr, typeIsObjectProfile.profile(type == object) ? PNone.NONE : object, type);
        }

        private Object lookupGet(PythonClass dataDescClass) {
            if (lookupGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetNode = insert(LookupAttributeInMRONode.create());
            }
            return lookupGetNode.execute(dataDescClass, __GET__);
        }

        private Object lookupDelete(PythonClass dataDescClass) {
            if (lookupDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupDeleteNode = insert(LookupAttributeInMRONode.create());
            }
            return lookupDeleteNode.execute(dataDescClass, __DELETE__);
        }

        private Object lookupSet(PythonClass dataDescClass) {
            if (lookupSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSetNode = insert(LookupAttributeInMRONode.create());
            }
            return lookupSetNode.execute(dataDescClass, __SET__);
        }

        private PythonClass getObjectClass(Object object) {
            if (getObjectClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectClassNode = insert(GetClassNode.create());
            }
            return typeProfile.profile(getObjectClassNode.execute(object));
        }

        private PythonClass getDataClass(Object descr) {
            if (getDataClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDataClassNode = insert(GetClassNode.create());
            }
            return getDataClassNode.execute(descr);
        }
    }

    @Builtin(name = SpecialMethodNames.__GETATTR__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattr(Object object, Object key) {
            throw raise(AttributeError, "'%p' object has no attribute %s", object, key);
        }
    }

    @Builtin(name = SpecialMethodNames.__SETATTR__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object, Object key, Object value,
                        @Cached("create()") LookupInheritedAttributeNode getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create()") LookupAttributeInMRONode lookupSetNode,
                        @Cached("create()") CallTernaryMethodNode callSetNode,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            Object descr = getExisting.execute(object, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupSetNode.execute(dataDescClass, __SET__);
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

    @Builtin(name = __DELATTR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object, Object key,
                        @Cached("create()") LookupInheritedAttributeNode getExisting,
                        @Cached("create()") GetClassNode getDataClassNode,
                        @Cached("create()") LookupAttributeInMRONode lookupSetNode,
                        @Cached("create()") CallBinaryMethodNode callSetNode,
                        @Cached("create()") ReadAttributeFromObjectNode attrRead,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            Object descr = getExisting.execute(object, key);
            if (descr != PNone.NO_VALUE) {
                Object dataDescClass = getDataClassNode.execute(descr);
                Object set = lookupSetNode.execute(dataDescClass, __DELETE__);
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
                throw raise(AttributeError, "%s object has no attribute %s", object, key);
            }
        }
    }

    @Builtin(name = __DICT__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dict(PythonClass self) {
            return factory().createMappingproxy(self);
        }

        @Specialization(guards = {"!isBuiltinObject(self)", "!isClass(self)"})
        Object dict(PythonObject self) {
            PDict dict = self.getDict();
            if (dict == null) {
                dict = factory().createDictFixedStorage(self);
                self.setDict(dict);
            }
            return dict;
        }

        @Fallback
        Object dict(Object self) {
            throw raise(AttributeError, "'%p' object has no attribute '__dict__'", self);
        }
    }

    @Builtin(name = __FORMAT__, fixedNumOfArguments = 2)
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

    @Builtin(name = RICHCMP, fixedNumOfArguments = 3)
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
