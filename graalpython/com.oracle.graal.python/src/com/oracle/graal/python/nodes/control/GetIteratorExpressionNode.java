/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.control;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNodeGen.GetIteratorNodeGen;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNodeGen.IsIteratorObjectNodeGen;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class GetIteratorExpressionNode extends UnaryOpNode {
    protected static final int MAX_CACHE_SIZE = 5;

    @Child private GetIteratorNode getIteratorNode = GetIteratorNode.create();

    @Specialization
    Object doGeneric(Object value) {
        return getIteratorNode.executeWith(value);
    }

    public static GetIteratorExpressionNode create(ExpressionNode collection) {
        return GetIteratorExpressionNodeGen.create(collection);
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GetIteratorNode extends Node {
        public abstract Object executeWith(Object value);

        @Specialization
        PythonObject doPZip(PZip value) {
            return value;
        }

        @Specialization(guards = {"!isNoValue(value)"})
        Object doGeneric(Object value,
                        @Cached("createIdentityProfile()") ValueProfile getattributeProfile,
                        @Cached GetLazyClassNode getClassNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttrMroNode,
                        @Cached LookupAttributeInMRONode.Dynamic lookupGetitemAttrMroNode,
                        @Cached CallUnaryMethodNode dispatchGetattribute,
                        @Cached IsIteratorObjectNode isIteratorObjectNode,
                        @Cached PythonObjectFactory factory,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            LazyPythonClass clazz = getClassNode.execute(value);
            Object attrObj = getattributeProfile.profile(lookupAttrMroNode.execute(clazz, SpecialMethodNames.__ITER__));
            if (attrObj != PNone.NO_VALUE && attrObj != PNone.NONE) {
                Object iterObj = dispatchGetattribute.executeObject(attrObj, value);
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

        @Specialization
        PythonObject doNone(PNone none,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            throw notIterable(raiseNode, none);
        }

        private static PException notIterable(PRaiseNode raiseNode, Object value) {
            throw raiseNode.raise(TypeError, "'%p' object is not iterable", value);
        }

        private static PException nonIterator(PRaiseNode raiseNode, Object value) {
            throw raiseNode.raise(TypeError, "iter() returned non-iterator of type '%p'", value);
        }

        public static GetIteratorNode create() {
            return GetIteratorNodeGen.create();
        }

        public static GetIteratorNode getUncached() {
            return GetIteratorNodeGen.getUncached();
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
