/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeGen.GetAnyAttributeNodeGen;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeGen.GetFixedAttributeNodeGen;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChild(value = "object", type = ExpressionNode.class)
public abstract class GetAttributeNode extends ExpressionNode implements ReadNode {

    @Child private GetFixedAttributeNode getFixedAttributeNode;

    public abstract int executeInt(VirtualFrame frame, Object object);

    public abstract boolean executeBoolean(VirtualFrame frame, Object object);

    public abstract Object executeObject(VirtualFrame frame, Object object);

    protected GetAttributeNode(String key) {
        getFixedAttributeNode = GetFixedAttributeNode.create(key);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    protected int doItInt(VirtualFrame frame, Object object) throws UnexpectedResultException {
        return getFixedAttributeNode.executeInt(frame, object);
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    protected boolean doItBoolean(VirtualFrame frame, Object object) throws UnexpectedResultException {
        return getFixedAttributeNode.executeBoolean(frame, object);
    }

    @Specialization(replaces = {"doItInt", "doItBoolean"})
    protected Object doIt(VirtualFrame frame, Object object) {
        return getFixedAttributeNode.executeObject(frame, object);
    }

    public final String getKey() {
        return getFixedAttributeNode.key;
    }

    public static GetAttributeNode create(String key, ExpressionNode object) {
        return GetAttributeNodeGen.create(key, object);
    }

    public static GetAttributeNode create(String key) {
        return GetAttributeNodeGen.create(key, null);
    }

    public final StatementNode makeWriteNode(ExpressionNode rhs) {
        return SetAttributeNode.create(getFixedAttributeNode.key, getObject(), rhs);
    }

    public abstract ExpressionNode getObject();

    abstract static class GetAttributeBaseNode extends Node {

        @Child private LookupInheritedAttributeNode lookupGetattrNode;
        @Child private CallBinaryMethodNode callBinaryMethodNode;

        @CompilationFinal private ConditionProfile hasGetattrProfile;

        int dispatchGetAttrOrRethrowInt(VirtualFrame frame, Object object, Object key, PException pe) throws UnexpectedResultException {
            return ensureCallGetattrNode().executeInt(frame, lookupGetattrOrRethrow(object, pe), object, key);
        }

        long dispatchGetAttrOrRethrowLong(VirtualFrame frame, Object object, Object key, PException pe) throws UnexpectedResultException {
            return ensureCallGetattrNode().executeLong(frame, lookupGetattrOrRethrow(object, pe), object, key);
        }

        boolean dispatchGetAttrOrRethrowBool(VirtualFrame frame, Object object, Object key, PException pe) throws UnexpectedResultException {
            return ensureCallGetattrNode().executeBool(frame, lookupGetattrOrRethrow(object, pe), object, key);
        }

        Object dispatchGetAttrOrRethrowObject(VirtualFrame frame, Object object, Object key, PException pe) {
            return ensureCallGetattrNode().executeObject(frame, lookupGetattrOrRethrow(object, pe), object, key);
        }

        /** Lookup {@code __getattr__} or rethrow {@code pe} if it does not exist. */
        private Object lookupGetattrOrRethrow(Object object, PException pe) {
            Object getattrAttribute = ensureLookupGetattrNode().execute(object);
            if (ensureHasGetattrProfile().profile(getattrAttribute == PNone.NO_VALUE)) {
                throw pe;
            }
            return getattrAttribute;
        }

        private LookupInheritedAttributeNode ensureLookupGetattrNode() {
            if (lookupGetattrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetattrNode = insert(LookupInheritedAttributeNode.create(__GETATTR__));
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
    }

    public abstract static class GetFixedAttributeNode extends GetAttributeBaseNode {

        private final String key;

        @Child private LookupAndCallBinaryNode dispatchNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

        public GetFixedAttributeNode(String key) {
            this.key = key;
        }

        public final String getKey() {
            return key;
        }

        public abstract int executeInt(VirtualFrame frame, Object object) throws UnexpectedResultException;

        public abstract long executeLong(VirtualFrame frame, Object object) throws UnexpectedResultException;

        public abstract boolean executeBoolean(VirtualFrame frame, Object object) throws UnexpectedResultException;

        public abstract Object executeObject(VirtualFrame frame, Object object);

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected int doItInt(VirtualFrame frame, Object object) throws UnexpectedResultException {
            try {
                return dispatchNode.executeInt(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowInt(frame, object, key, pe);
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long doItLong(VirtualFrame frame, Object object) throws UnexpectedResultException {
            try {
                return dispatchNode.executeLong(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowLong(frame, object, key, pe);
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected boolean doItBoolean(VirtualFrame frame, Object object) throws UnexpectedResultException {
            try {
                return dispatchNode.executeBool(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowBool(frame, object, key, pe);
            }
        }

        @Specialization(replaces = {"doItInt", "doItBoolean"})
        protected Object doIt(VirtualFrame frame, Object object) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        public static GetFixedAttributeNode create(String key) {
            return GetFixedAttributeNodeGen.create(key);
        }
    }

    public abstract static class GetAnyAttributeNode extends GetAttributeBaseNode {

        @Child private LookupAndCallBinaryNode dispatchNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

        public abstract int executeInt(VirtualFrame frame, Object object, Object key) throws UnexpectedResultException;

        public abstract long executeLong(VirtualFrame frame, Object object, Object key) throws UnexpectedResultException;

        public abstract boolean executeBoolean(VirtualFrame frame, Object object, Object key) throws UnexpectedResultException;

        public abstract Object executeObject(VirtualFrame frame, Object object, Object key);

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected int doItInt(VirtualFrame frame, Object object, String key) throws UnexpectedResultException {
            try {
                return dispatchNode.executeInt(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowInt(frame, object, key, pe);
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected long doItLong(VirtualFrame frame, Object object, Object key) throws UnexpectedResultException {
            try {
                return dispatchNode.executeLong(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowLong(frame, object, key, pe);
            }
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected boolean doItBoolean(VirtualFrame frame, Object object, Object key) throws UnexpectedResultException {
            try {
                return dispatchNode.executeBool(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowBool(frame, object, key, pe);
            }
        }

        @Specialization(replaces = {"doItInt", "doItBoolean"})
        protected Object doIt(VirtualFrame frame, Object object, Object key) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(AttributeError, isBuiltinClassProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        public static GetAnyAttributeNode create() {
            return GetAnyAttributeNodeGen.create();
        }
    }
}
