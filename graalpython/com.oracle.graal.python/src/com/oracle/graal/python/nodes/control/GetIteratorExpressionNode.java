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
package com.oracle.graal.python.nodes.control;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNodeGen.GetIteratorNodeGen;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNodeGen.GetIteratorWithoutFrameNodeGen;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNodeGen.IsIteratorObjectNodeGen;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class GetIteratorExpressionNode extends UnaryOpNode {
    protected static final int MAX_CACHE_SIZE = 5;

    @Child private GetIteratorNode getIteratorNode = GetIteratorNode.create();

    @Specialization
    Object doGeneric(VirtualFrame frame, Object value) {
        return getIteratorNode.executeWith(frame, value);
    }

    public static GetIteratorExpressionNode create(ExpressionNode collection) {
        return GetIteratorExpressionNodeGen.create(collection);
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GetIteratorWithoutFrameNode extends PNodeWithContext {
        public abstract Object executeWithGlobalState(Object value);

        @Specialization
        static PythonObject doPZip(PZip value) {
            return GetIteratorNode.doPZip(value);
        }

        @Specialization(guards = {"!isNoValue(value)"})
        static Object doGeneric(Object value,
                        @Cached("createIdentityProfile()") ValueProfile getattributeProfile,
                        @CachedLibrary(limit = "4") PythonObjectLibrary plib,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrMroNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupGetitemAttrMroNode,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @Cached IsIteratorObjectNode isIteratorObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            // NOTE: it's fine to pass 'null' frame since the caller must already take care of the
            // global state
            return GetIteratorNode.doGeneric(null, value, getattributeProfile, plib, lookupAttrMroNode, lookupGetitemAttrMroNode, dispatchGetattribute, isIteratorObjectNode, factory,
                            raiseNode);
        }

        @Specialization
        static PythonObject doNone(PNone none,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return GetIteratorNode.doNone(none, raiseNode);
        }

        public static GetIteratorWithoutFrameNode create() {
            return GetIteratorWithoutFrameNodeGen.create();
        }

        public static GetIteratorWithoutFrameNode getUncached() {
            return GetIteratorWithoutFrameNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class GetIteratorNode extends Node {
        public abstract Object executeWith(VirtualFrame frame, Object value);

        @Specialization
        static PythonObject doPZip(PZip value) {
            return value;
        }

        @Specialization(guards = {"!isNoValue(value)", "!plib.isReflectedObject(value, value)"})
        static Object doGeneric(VirtualFrame frame, Object value,
                        @Cached("createIdentityProfile()") ValueProfile getattributeProfile,
                        @CachedLibrary(limit = "4") PythonObjectLibrary plib,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrMroNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupGetitemAttrMroNode,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @Cached IsIteratorObjectNode isIteratorObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            Object clazz = plib.getLazyPythonClass(value);
            Object attrObj = getattributeProfile.profile(lookupAttrMroNode.execute(clazz, SpecialMethodNames.__ITER__));
            if (attrObj != PNone.NO_VALUE && attrObj != PNone.NONE) {
                Object iterObj = dispatchGetattribute.executeObject(frame, attrObj, value);
                if (isIteratorObjectNode.execute(iterObj)) {
                    return iterObj;
                } else {
                    throw nonIterator(raiseNode, iterObj);
                }
            } else {
                Object getItemAttrObj = lookupGetitemAttrMroNode.execute(clazz, SpecialMethodNames.__GETITEM__);
                if (getItemAttrObj != PNone.NO_VALUE) {
                    return factory.createSequenceIterator(value);
                }
            }
            throw notIterable(raiseNode, value);
        }

        @Specialization(guards = {"!isNoValue(value)", "plib.isReflectedObject(value, value)"})
        static Object doReflected(VirtualFrame frame, Object value,
                        @CachedLibrary(limit = "4") PythonObjectLibrary plib,
                        @Cached GetIteratorNode recursive) {
            return recursive.executeWith(frame, plib.getReflectedObject(value));
        }

        @Specialization
        static PythonObject doNone(PNone none,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw notIterable(raiseNode, none);
        }

        private static PException notIterable(PRaiseNode raiseNode, Object value) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, value);
        }

        private static PException nonIterator(PRaiseNode raiseNode, Object value) {
            throw raiseNode.raise(TypeError, ErrorMessages.ITER_RETURNED_NON_ITERABLE, value);
        }

        public static GetIteratorNode create() {
            return GetIteratorNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    abstract static class IsIteratorObjectNode extends Node {

        public abstract boolean execute(Object o);

        @Specialization
        boolean doPIterator(@SuppressWarnings("unused") PBuiltinIterator it) {
            // a PIterator object is guaranteed to be an iterator object
            return true;
        }

        @Specialization
        boolean doGeneric(Object it,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupAttributeNode) {
            return lookupAttributeNode.execute(it, SpecialMethodNames.__NEXT__) != PNone.NO_VALUE;
        }

        public static IsIteratorObjectNode create() {
            return IsIteratorObjectNodeGen.create();
        }

        public static IsIteratorObjectNode getUncached() {
            return IsIteratorObjectNodeGen.getUncached();
        }
    }
}
