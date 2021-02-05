/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.attributes;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodInfo.BinaryBuiltinInfo;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltinsFactory;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeFactory.GetFixedAttributeNodeGen;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class GetAttributeNode extends ExpressionNode implements ReadNode {

    @Child private GetFixedAttributeNode getFixedAttributeNode;
    @Child private ExpressionNode objectExpression;

    @Override
    public Object execute(VirtualFrame frame) {
        return executeObject(frame, objectExpression.execute(frame));
    }

    public Object executeObject(VirtualFrame frame, Object object) {
        return getFixedAttributeNode.executeObject(frame, object);
    }

    protected GetAttributeNode(String key, ExpressionNode object) {
        getFixedAttributeNode = GetFixedAttributeNode.create(key);
        objectExpression = object;
    }

    public final String getKey() {
        return getFixedAttributeNode.key;
    }

    public static GetAttributeNode create(String key, ExpressionNode object) {
        return new GetAttributeNode(key, object);
    }

    public static GetAttributeNode create(String key) {
        return new GetAttributeNode(key, null);
    }

    @Override
    public final StatementNode makeWriteNode(ExpressionNode rhs) {
        return SetAttributeNode.create(getFixedAttributeNode.key, getObject(), rhs);
    }

    public ExpressionNode getObject() {
        return objectExpression;
    }

    abstract static class GetAttributeBaseNode extends Node {

        @Child protected LookupAndCallBinaryNode dispatchNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child protected IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

        @Child private LookupSpecialMethodNode lookupGetattrNode;
        @Child private CallBinaryMethodNode callBinaryMethodNode;
        @Child private PythonObjectLibrary lib;

        @CompilationFinal private ConditionProfile hasGetattrProfile;

        Object dispatchGetAttrOrRethrowObject(VirtualFrame frame, Object object, Object key, PException pe) {
            return ensureCallGetattrNode().executeObject(frame, lookupGetattrOrRethrow(frame, object, pe), object, key);
        }

        Object dispatchGetAttrOrRethrowObject(VirtualFrame frame, Object object, Object objectLazyClass, Object key, PException pe) {
            return ensureCallGetattrNode().executeObject(frame, lookupGetattrOrRethrow(frame, object, objectLazyClass, pe), object, key);
        }

        private Object lookupGetattrOrRethrow(VirtualFrame frame, Object object, PException pe) {
            return lookupGetattrOrRethrow(frame, object, ensurePythonObjLib().getLazyPythonClass(object), pe);
        }

        /** Lookup {@code __getattr__} or rethrow {@code pe} if it does not exist. */
        private Object lookupGetattrOrRethrow(VirtualFrame frame, Object object, Object objectLazyClass, PException pe) {
            Object getattrAttribute = ensureLookupGetattrNode().execute(frame, objectLazyClass, object);
            if (ensureHasGetattrProfile().profile(getattrAttribute == PNone.NO_VALUE)) {
                throw pe;
            }
            return getattrAttribute;
        }

        private LookupSpecialMethodNode ensureLookupGetattrNode() {
            if (lookupGetattrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetattrNode = insert(LookupSpecialMethodNode.create(__GETATTR__));
            }
            return lookupGetattrNode;
        }

        private CallBinaryMethodNode ensureCallGetattrNode() {
            if (callBinaryMethodNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBinaryMethodNode = insert(CallBinaryMethodNode.create());
            }
            return callBinaryMethodNode;
        }

        private ConditionProfile ensureHasGetattrProfile() {
            if (hasGetattrProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasGetattrProfile = ConditionProfile.createBinaryProfile();
            }
            return hasGetattrProfile;
        }

        public PythonObjectLibrary ensurePythonObjLib() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(1));
            }
            return lib;
        }
    }

    public abstract static class GetFixedAttributeNode extends GetAttributeBaseNode {
        private final String key;

        public GetFixedAttributeNode(String key) {
            this.key = key;
        }

        public final String getKey() {
            return key;
        }

        public final Object executeObject(VirtualFrame frame, Object object) {
            Object klass = ensurePythonObjLib().getLazyPythonClass(object);
            return execute(frame, object, klass);
        }

        public abstract Object execute(VirtualFrame frame, Object object, Object klass);

        protected static boolean isObjectGetAttribute(PythonBuiltinClassType klassType, NodeFactory<? extends PythonBinaryBuiltinNode> factory) {
            Object slot = SpecialMethodSlot.GetAttribute.getValue(klassType);
            return slot instanceof BinaryBuiltinInfo && ((BinaryBuiltinInfo) slot).getFactory() == factory;
        }

        protected static boolean isObjectGetAttribute(PythonManagedClass klass, NodeFactory<? extends PythonBinaryBuiltinNode> factory) {
            Object slot = SpecialMethodSlot.GetAttribute.getValue(klass);
            return slot instanceof PBuiltinFunction && ((PBuiltinFunction) slot).getBuiltinNodeFactory() == factory;
        }

        protected static boolean getAttributeIs(Object lazyClass, NodeFactory<? extends PythonBinaryBuiltinNode> factory) {
            if (lazyClass instanceof PythonBuiltinClassType) {
                return isObjectGetAttribute((PythonBuiltinClassType) lazyClass, factory);
            } else if (lazyClass instanceof PythonManagedClass) {
                return isObjectGetAttribute((PythonManagedClass) lazyClass, factory);
            }
            return false;
        }

        protected static boolean isObjectGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, ObjectBuiltinsFactory.GetAttributeNodeFactory.getInstance());
        }

        protected static boolean isModuleGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, ModuleBuiltinsFactory.ModuleGetattritbuteNodeFactory.getInstance());
        }

        protected static boolean isTypeGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, TypeBuiltinsFactory.GetattributeNodeFactory.getInstance());
        }

        @Specialization(guards = "isObjectGetAttribute(lazyClass)")
        final Object doBuiltinObject(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass,
                        @Cached ObjectBuiltins.GetAttributeNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(guards = "isTypeGetAttribute(lazyClass)")
        final Object doBuiltinType(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass,
                        @Cached TypeBuiltins.GetattributeNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(guards = "isModuleGetAttribute(lazyClass)")
        final Object doBuiltinModule(VirtualFrame frame, Object object, @SuppressWarnings("unused") Object lazyClass,
                        @Cached ModuleBuiltins.ModuleGetattritbuteNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(replaces = {"doBuiltinObject", "doBuiltinType", "doBuiltinModule"})
        final Object doGeneric(VirtualFrame frame, Object object, Object lazyKlass) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, lazyKlass, key, pe);
            }
        }

        public static GetFixedAttributeNode create(String key) {
            return GetFixedAttributeNodeGen.create(key);
        }
    }

    public static final class GetAnyAttributeNode extends GetAttributeBaseNode {

        public Object executeObject(VirtualFrame frame, Object object, Object key) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        public static GetAnyAttributeNode create() {
            return new GetAnyAttributeNode();
        }
    }
}
