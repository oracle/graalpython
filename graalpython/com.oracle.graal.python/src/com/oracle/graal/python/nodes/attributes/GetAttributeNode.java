/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeFactory.GetAnyAttributeNodeGen;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeFactory.GetFixedAttributeNodeGen;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class GetAttributeNode extends PNodeWithContext {

    @Child private GetFixedAttributeNode getFixedAttributeNode;

    public Object executeObject(VirtualFrame frame, Object object) {
        return getFixedAttributeNode.executeObject(frame, object);
    }

    protected GetAttributeNode(TruffleString key) {
        getFixedAttributeNode = GetFixedAttributeNode.create(key);
    }

    @NeverDefault
    public static GetAttributeNode create(TruffleString key) {
        return new GetAttributeNode(key);
    }

    abstract static class GetAttributeBaseNode extends PNodeWithContext {

        @Child protected LookupAndCallBinaryNode dispatchNode = LookupAndCallBinaryNode.create(SpecialMethodSlot.GetAttribute);

        @Child private LookupSpecialMethodSlotNode lookupGetattrNode;
        @Child private CallBinaryMethodNode callBinaryMethodNode;
        @Child private GetClassNode getClassNode;

        @CompilationFinal private ConditionProfile hasGetattrProfile;

        Object dispatchGetAttrOrRethrowObject(VirtualFrame frame, Object object, Object key, PException pe) {
            return ensureCallGetattrNode().executeObject(frame, lookupGetattrOrRethrow(frame, object, pe), object, key);
        }

        Object dispatchGetAttrOrRethrowObject(VirtualFrame frame, Object object, Object objectLazyClass, Object key, PException pe) {
            return ensureCallGetattrNode().executeObject(frame, lookupGetattrOrRethrow(frame, object, objectLazyClass, pe), object, key);
        }

        private Object lookupGetattrOrRethrow(VirtualFrame frame, Object object, PException pe) {
            return lookupGetattrOrRethrow(frame, object, getPythonClass(object), pe);
        }

        /** Lookup {@code __getattr__} or rethrow {@code pe} if it does not exist. */
        private Object lookupGetattrOrRethrow(VirtualFrame frame, Object object, Object objectLazyClass, PException pe) {
            Object getattrAttribute = ensureLookupGetattrNode().execute(frame, objectLazyClass, object);
            if (ensureHasGetattrProfile().profile(getattrAttribute == PNone.NO_VALUE)) {
                throw pe;
            }
            return getattrAttribute;
        }

        private LookupSpecialMethodSlotNode ensureLookupGetattrNode() {
            if (lookupGetattrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGetattrNode = insert(LookupSpecialMethodSlotNode.create(SpecialMethodSlot.GetAttr));
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
                hasGetattrProfile = ConditionProfile.create();
            }
            return hasGetattrProfile;
        }

        protected Object getPythonClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.executeCached(object);
        }
    }

    public abstract static class GetFixedAttributeNode extends GetAttributeBaseNode {
        private final TruffleString key;

        public GetFixedAttributeNode(TruffleString key) {
            this.key = key;
        }

        public final TruffleString getKey() {
            return key;
        }

        public final Object executeObject(VirtualFrame frame, Object object) {
            return execute(frame, object);
        }

        public abstract Object execute(VirtualFrame frame, Object object);

        protected static boolean getAttributeIs(Object lazyClass, BuiltinMethodDescriptor expected) {
            Object slotValue = null;
            if (lazyClass instanceof PythonBuiltinClassType) {
                slotValue = SpecialMethodSlot.GetAttribute.getValue((PythonBuiltinClassType) lazyClass);
            } else if (lazyClass instanceof PythonManagedClass) {
                slotValue = SpecialMethodSlot.GetAttribute.getValue((PythonManagedClass) lazyClass);
            }
            return slotValue == expected;
        }

        protected static boolean isObjectGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, BuiltinMethodDescriptors.OBJ_GET_ATTRIBUTE);
        }

        protected static boolean isModuleGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, BuiltinMethodDescriptors.MODULE_GET_ATTRIBUTE);
        }

        protected static boolean isTypeGetAttribute(Object lazyClass) {
            return getAttributeIs(lazyClass, BuiltinMethodDescriptors.TYPE_GET_ATTRIBUTE);
        }

        /*
         * Here we have fast-paths for the most common values found in the __getattribute__ slot but
         * only for multi-context mode. The caching we can do in the generic lookup attribute
         * machinery (i.e., LookupCallableSlotInMRONode) in single context case seems to perform as
         * good as this fast-path (both in interpreter and in compiled code), so no point in using
         * it in single context mode.
         */

        @Specialization(guards = "isSingleContext()")
        final Object doSingleContext(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, getPythonClass(object), key, pe);
            }
        }

        @Specialization(guards = {"!isSingleContext()", "isObjectGetAttribute(getPythonClass(object))"})
        final Object doBuiltinObject(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached ObjectBuiltins.GetAttributeNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(guards = {"!isSingleContext()", "isTypeGetAttribute(getPythonClass(object))"})
        final Object doBuiltinType(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached TypeBuiltins.GetattributeNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(guards = {"!isSingleContext()", "isModuleGetAttribute(getPythonClass(object))"})
        final Object doBuiltinModule(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached ModuleBuiltins.ModuleGetattritbuteNode getAttributeNode) {
            try {
                return getAttributeNode.execute(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @Specialization(guards = "!isSingleContext()", replaces = {"doBuiltinObject", "doBuiltinType", "doBuiltinModule"})
        final Object doGeneric(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Shared("errorProfile") @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, getPythonClass(object), key, pe);
            }
        }

        @NeverDefault
        public static GetFixedAttributeNode create(TruffleString key) {
            return GetFixedAttributeNodeGen.create(key);
        }
    }

    public abstract static class GetAnyAttributeNode extends GetAttributeBaseNode {

        public abstract Object executeObject(VirtualFrame frame, Object object, Object key);

        @Specialization
        Object doIt(VirtualFrame frame, Object object, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                return dispatchNode.executeObject(frame, object, key);
            } catch (PException pe) {
                pe.expect(inliningTarget, AttributeError, errorProfile);
                return dispatchGetAttrOrRethrowObject(frame, object, key, pe);
            }
        }

        @NeverDefault
        public static GetAnyAttributeNode create() {
            return GetAnyAttributeNodeGen.create();
        }
    }
}
