/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.superobject;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltinsFactory.GetObjectNodeGen;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltinsFactory.GetObjectTypeNodeGen;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltinsFactory.GetTypeNodeGen;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltinsFactory.SuperInitNodeFactory;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.Super)
public final class SuperBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SuperBuiltinsFactory.getFactories();
    }

    abstract static class GetTypeNode extends Node {
        abstract Object execute(SuperObject self);

        @Specialization(guards = "self == cachedSelf", assumptions = "cachedSelf.getNeverReinitializedAssumption()", limit = "1")
        Object cached(@SuppressWarnings("unused") SuperObject self,
                        @SuppressWarnings("unused") @Cached("self") SuperObject cachedSelf,
                        @Cached("self.getType()") Object type) {
            return type;
        }

        @Specialization(replaces = "cached")
        Object uncached(SuperObject self) {
            return self.getType();
        }
    }

    abstract static class GetObjectTypeNode extends Node {
        abstract PythonAbstractClass execute(SuperObject self);

        @Specialization(guards = "self == cachedSelf", assumptions = "cachedSelf.getNeverReinitializedAssumption()", limit = "1")
        PythonAbstractClass cached(@SuppressWarnings("unused") SuperObject self,
                        @SuppressWarnings("unused") @Cached("self") SuperObject cachedSelf,
                        @Cached("self.getObjectType()") PythonAbstractClass type) {
            return type;
        }

        @Specialization(replaces = "cached")
        PythonAbstractClass uncached(SuperObject self) {
            return self.getObjectType();
        }
    }

    abstract static class GetObjectNode extends Node {
        abstract Object execute(SuperObject self);

        @Specialization(guards = "self == cachedSelf", assumptions = "cachedSelf.getNeverReinitializedAssumption()", limit = "1")
        Object cached(@SuppressWarnings("unused") SuperObject self,
                        @SuppressWarnings("unused") @Cached("self") SuperObject cachedSelf,
                        @Cached("self.getObject()") Object object) {
            return object;
        }

        @Specialization(replaces = "cached")
        Object uncached(SuperObject self) {
            return self.getObject();
        }
    }

    @Builtin(name = SpecialMethodNames.__INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, alwaysNeedsCallerFrame = true)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonVarargsBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private IsInstanceNode isInstanceNode;
        @Child private GetClassNode getClassNode;
        @Child private LookupAndCallBinaryNode getAttrNode;
        @Child private CellBuiltins.GetRefNode getRefNode;
        @Child private TypeNodes.IsTypeNode isTypeNode;

        @Override
        public Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (keywords.length != 0) {
                throw raise(PythonErrorType.RuntimeError, "super(): unexpected keyword arguments");
            }
            if (arguments.length == 1) {
                return execute(frame, arguments[0], PNone.NO_VALUE, PNone.NO_VALUE);
            } else if (arguments.length == 2) {
                return execute(frame, arguments[0], arguments[1], PNone.NO_VALUE);
            } else if (arguments.length == 3) {
                return execute(frame, arguments[0], arguments[1], arguments[2]);
            } else {
                throw raise(PythonErrorType.RuntimeError, "super(): invalid number of arguments");
            }
        }

        @Override
        public final Object execute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            if (keywords.length != 0) {
                throw raise(PythonErrorType.RuntimeError, "super(): unexpected keyword arguments");
            }
            if (arguments.length == 0) {
                return execute(frame, self, PNone.NO_VALUE, PNone.NO_VALUE);
            } else if (arguments.length == 1) {
                return execute(frame, self, arguments[0], PNone.NO_VALUE);
            } else if (arguments.length == 2) {
                return execute(frame, self, arguments[0], arguments[1]);
            } else {
                throw raise(PythonErrorType.RuntimeError, "super(): too many arguments");
            }
        }

        protected abstract Object execute(VirtualFrame frame, Object self, Object cls, Object obj);

        @Specialization(guards = {"!isNoValue(cls)", "!isNoValue(obj)"})
        PNone init(VirtualFrame frame, SuperObject self, Object cls, Object obj) {
            if (obj != PNone.NONE) {
                PythonAbstractClass type = supercheck(frame, cls, obj);
                self.init(cls, type, obj);
            } else {
                self.init(cls, null, null);
            }
            return PNone.NONE;
        }

        protected boolean isInBuiltinFunctionRoot() {
            return getRootNode() instanceof BuiltinFunctionRootNode;
        }

        protected ReadLocalVariableNode createRead(VirtualFrame frame) {
            FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(SpecialAttributeNames.__CLASS__);
            if (slot == null) {
                throw raise(PythonErrorType.RuntimeError, "super(): empty __class__ cell");
            }
            return ReadLocalVariableNode.create(slot);
        }

        /**
         * Executed with the frame of the calling method - direct access to the frame.
         */
        @Specialization(guards = {"!isInBuiltinFunctionRoot()", "isNoValue(clsArg)", "isNoValue(objArg)"})
        PNone initInPlace(VirtualFrame frame, SuperObject self, @SuppressWarnings("unused") PNone clsArg, @SuppressWarnings("unused") PNone objArg,
                        @Cached("createRead(frame)") ReadLocalVariableNode readClass,
                        @Cached("create(0)") ReadIndexedArgumentNode readArgument,
                        @Cached("createBinaryProfile()") ConditionProfile isCellProfile) {
            Object obj = readArgument.execute(frame);
            if (obj == PNone.NONE) {
                throw raise(PythonErrorType.RuntimeError, "super(): no arguments");
            }
            Object cls = readClass.execute(frame);
            if (isCellProfile.profile(cls instanceof PCell)) {
                cls = getGetRefNode().execute((PCell) cls);
            }
            if (cls == PNone.NONE) {
                throw raise(PythonErrorType.RuntimeError, "super(): empty __class__ cell");
            }
            return init(frame, self, cls, obj);
        }

        /**
         * Executed within a {@link BuiltinFunctionRootNode} - indirect access to the frame.
         */
        @Specialization(guards = {"isInBuiltinFunctionRoot()", "isNoValue(clsArg)", "isNoValue(objArg)"})
        PNone init(VirtualFrame frame, SuperObject self, @SuppressWarnings("unused") PNone clsArg, @SuppressWarnings("unused") PNone objArg,
                        @Cached ReadCallerFrameNode readCaller,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib) {
            PFrame target = readCaller.executeWith(frame, 0);
            if (target == null) {
                throw raise(PythonErrorType.RuntimeError, "super(): no current frame");
            }
            Object[] arguments = target.getArguments();
            if (PArguments.getUserArgumentLength(arguments) == 0) {
                throw raise(PythonErrorType.RuntimeError, "super(): no arguments");
            }
            Object obj = PArguments.getArgument(arguments, 0);
            if (obj == PNone.NONE) {
                throw raise(PythonErrorType.RuntimeError, "super(): no arguments");
            }

            Object cls = getClassFromTarget(frame, target, hlib);
            return init(frame, self, cls, obj);
        }

        private Object getClassFromTarget(VirtualFrame frame, PFrame target, HashingStorageLibrary hlib) {
            // TODO: remove me
            // TODO: do it properly via the python API in super.__init__ :
            // sys._getframe(1).f_code.co_closure?
            PDict locals = (PDict) target.getLocalsDict();
            Object cls = hlib.getItemWithState(locals.getDictStorage(), SpecialAttributeNames.__CLASS__, PArguments.getThreadState(frame));
            if (cls instanceof PCell) {
                cls = getGetRefNode().execute((PCell) cls);
                if (cls == null) {
                    throw raise(PythonErrorType.RuntimeError, "super(): empty __class__ cell");
                }
            }
            return cls != null ? cls : PNone.NONE;
        }

        private CellBuiltins.GetRefNode getGetRefNode() {
            if (getRefNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRefNode = CellBuiltins.GetRefNode.create();
            }
            return getRefNode;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNone initFallback(Object self, Object cls, Object obj) {
            throw raise(PythonErrorType.RuntimeError, "super(): invalid arguments");
        }

        private IsSubtypeNode getIsSubtype() {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode;
        }

        private IsInstanceNode getIsInstance() {
            if (isInstanceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isInstanceNode = insert(IsInstanceNode.create());
            }
            return isInstanceNode;
        }

        private GetClassNode getGetClass() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }

        private TypeNodes.IsTypeNode ensureIsTypeNode() {
            if (isTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTypeNode = insert(TypeNodes.IsTypeNode.create());
            }
            return isTypeNode;
        }

        private LookupAndCallBinaryNode getGetAttr() {
            if (getAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttrNode = insert(LookupAndCallBinaryNode.create(SpecialMethodNames.__GETATTRIBUTE__));
            }
            return getAttrNode;
        }

        private PythonAbstractClass supercheck(VirtualFrame frame, Object cls, Object object) {
            /*
             * Check that a super() call makes sense. Return a type object.
             *
             * obj can be a class, or an instance of one:
             *
             * - If it is a class, it must be a subclass of 'type'. This case is used for class
             * methods; the return value is obj.
             *
             * - If it is an instance, it must be an instance of 'type'. This is the normal case;
             * the return value is obj.__class__.
             *
             * But... when obj is an instance, we want to allow for the case where Py_TYPE(obj) is
             * not a subclass of type, but obj.__class__ is! This will allow using super() with a
             * proxy for obj.
             */
            if (ensureIsTypeNode().execute(object)) {
                if (getIsSubtype().execute(frame, object, cls)) {
                    return (PythonAbstractClass) object;
                }
            }

            if (getIsInstance().executeWith(frame, object, cls)) {
                return getGetClass().execute(object);
            } else {
                try {
                    Object classObject = getGetAttr().executeObject(frame, object, SpecialAttributeNames.__CLASS__);
                    if (ensureIsTypeNode().execute(classObject)) {
                        if (getIsSubtype().execute(frame, classObject, cls)) {
                            return (PythonAbstractClass) classObject;
                        }
                    }
                } catch (PException e) {
                    // error is ignored
                }

                throw raise(PythonErrorType.TypeError, "super(type, obj): obj must be an instance or subtype of type");
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__GET__, minNumOfPositionalArgs = 2, parameterNames = {"self", "obj", "type"})
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Child GetObjectNode getObject = GetObjectNodeGen.create();
        @Child GetTypeNode getType;
        @Child SuperInitNode superInit;

        @Specialization
        public Object get(SuperObject self, Object obj, @SuppressWarnings("unused") Object type,
                        @Cached("create()") GetClassNode getClass) {
            if (obj == PNone.NONE || getObject.execute(self) != null) {
                // not binding to an object or already bound
                return this;
            } else {
                if (getType == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    superInit = insert(SuperInitNodeFactory.create());
                    getType = insert(GetTypeNodeGen.create());
                }
                SuperObject newSuper = factory().createSuperObject(getClass.execute(self));
                superInit.execute(null, newSuper, getType.execute(self), obj);
                return newSuper;
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBinaryBuiltinNode {
        @Child private ReadAttributeFromObjectNode readFromDict = ReadAttributeFromObjectNode.createForceType();
        @Child private LookupInheritedAttributeNode readGet = LookupInheritedAttributeNode.create(SpecialMethodNames.__GET__);
        @Child private GetObjectTypeNode getObjectType = GetObjectTypeNodeGen.create();
        @Child private GetTypeNode getType;
        @Child private GetObjectNode getObject;
        @Child private CallTernaryMethodNode callGet;
        @Child private ObjectBuiltins.GetAttributeNode objectGetattributeNode;
        @Child private GetMroNode getMroNode;
        @Child private IsSameTypeNode isSameTypeNode;

        private Object genericGetAttr(VirtualFrame frame, Object object, Object attr) {
            if (objectGetattributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectGetattributeNode = insert(ObjectBuiltinsFactory.GetAttributeNodeFactory.create());
            }
            return objectGetattributeNode.execute(frame, object, attr);
        }

        @Specialization
        public Object get(VirtualFrame frame, SuperObject self, Object attr) {
            PythonAbstractClass startType = getObjectType.execute(self);
            if (startType == null) {
                return genericGetAttr(frame, self, attr);
            }

            /*
             * We want __class__ to return the class of the super object (i.e. super, or a
             * subclass), not the class of su->obj.
             */
            String stringAttr = null;
            if (attr instanceof PString) {
                stringAttr = ((PString) attr).getValue();
            } else if (attr instanceof String) {
                stringAttr = (String) attr;
            }
            if (stringAttr != null) {
                if (stringAttr.equals(SpecialAttributeNames.__CLASS__)) {
                    return genericGetAttr(frame, self, SpecialAttributeNames.__CLASS__);
                }
            }

            // acts as a branch profile
            if (getType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getType = insert(GetTypeNodeGen.create());
            }

            PythonAbstractClass[] mro = getMro(startType);
            /* No need to check the last one: it's gonna be skipped anyway. */
            int i = 0;
            int n = mro.length;
            for (i = 0; i + 1 < n; i++) {
                if (isSameType(getType.execute(self), mro[i])) {
                    break;
                }
            }
            i++; /* skip su->type (if any) */
            if (i >= n) {
                return genericGetAttr(frame, self, attr);
            }

            for (; i < n; i++) {
                PythonAbstractClass tmp = mro[i];
                Object res = readFromDict.execute(tmp, attr);
                if (res != PNone.NO_VALUE) {
                    Object get = readGet.execute(res);
                    if (get != PNone.NO_VALUE) {
                        /*
                         * Only pass 'obj' param if this is instance-mode super (See SF ID #743627)
                         */
                        // acts as a branch profile
                        if (callGet == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            getObject = insert(GetObjectNodeGen.create());
                            callGet = insert(CallTernaryMethodNode.create());
                        }
                        res = callGet.execute(frame, get, res, getObject.execute(self) == startType ? PNone.NO_VALUE : self.getObject(), startType);
                    }
                    return res;
                }
            }

            return genericGetAttr(frame, self, attr);
        }

        private boolean isSameType(Object execute, PythonAbstractClass abstractPythonClass) {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(IsSameTypeNodeGen.create());
            }
            return isSameTypeNode.execute(execute, abstractPythonClass);
        }

        private PythonAbstractClass[] getMro(PythonAbstractClass clazz) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(clazz);
        }
    }

    @Builtin(name = "__thisclass__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ThisClassNode extends PythonUnaryBuiltinNode {
        @Child GetTypeNode getType = GetTypeNodeGen.create();

        @Specialization
        Object getClass(SuperObject self) {
            Object type = getType.execute(self);
            if (type == null) {
                return PNone.NONE;
            }
            return type;
        }
    }

    @Builtin(name = "__self__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SelfNode extends PythonUnaryBuiltinNode {
        @Child GetObjectNode getObject = GetObjectNodeGen.create();

        @Specialization
        Object getClass(SuperObject self) {
            Object object = getObject.execute(self);
            if (object == null) {
                return PNone.NONE;
            }
            return object;
        }
    }

    @Builtin(name = "__self_class__", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SelfClassNode extends PythonUnaryBuiltinNode {
        @Child GetObjectTypeNode getObjectType = GetObjectTypeNodeGen.create();

        @Specialization
        Object getClass(SuperObject self) {
            PythonAbstractClass objectType = getObjectType.execute(self);
            if (objectType == null) {
                return PNone.NONE;
            }
            return objectType;
        }
    }
}
