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
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.control.GetIteratorNodeGen.IsIteratorObjectNodeGen;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class GetIteratorNode extends UnaryOpNode {
    protected static final int MAX_CACHE_SIZE = 5;

    public static GetIteratorNode create() {
        return GetIteratorNodeGen.create(null);
    }

    public static GetIteratorNode create(ExpressionNode collection) {
        return GetIteratorNodeGen.create(collection);
    }

    @Child private GetClassNode getClassNode;

    private PythonClass getClass(Object value) {
        if (getClassNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getClassNode = insert(GetClassNode.create());
        }
        return getClassNode.execute(value);
    }

    /**
     * Tests if the class of a Python object is a builtin class, i.e., any magic methods cannot be
     * overridden.
     */
    protected boolean iterCannotBeOverridden(Object value) {
        return getClass(value).isBuiltin();
    }

    public abstract Object executeWith(Object value);

    @Specialization(guards = "iterCannotBeOverridden(value)")
    public PythonObject doPZip(PZip value) {
        return value;
    }

    @Specialization(guards = {"!isNoValue(value)"})
    public Object doGeneric(Object value,
                    @Cached("createIdentityProfile()") ValueProfile getattributeProfile,
                    @Cached("create(__ITER__)") LookupAttributeInMRONode lookupAttrMroNode,
                    @Cached("create(__GETITEM__)") LookupAttributeInMRONode lookupGetitemAttrMroNode,
                    @Cached("create()") CallUnaryMethodNode dispatchGetattribute,
                    @Cached("create()") IsIteratorObjectNode isIteratorObjectNode) {
        PythonClass clazz = getClass(value);
        Object attrObj = getattributeProfile.profile(lookupAttrMroNode.execute(clazz));
        if (attrObj != PNone.NO_VALUE && attrObj != PNone.NONE) {
            Object iterObj = dispatchGetattribute.executeObject(attrObj, value);
            if (isIteratorObjectNode.execute(iterObj)) {
                return iterObj;
            } else {
                throw nonIterator(iterObj);
            }
        } else {
            Object getItemAttrObj = lookupGetitemAttrMroNode.execute(clazz);
            if (getItemAttrObj != PNone.NO_VALUE) {
                return factory().createSequenceIterator(value);
            }
        }
        throw notIterable(value);
    }

    @Specialization
    public PythonObject doNone(PNone none) {
        throw notIterable(none);
    }

    private PException notIterable(Object value) {
        return raise(TypeError, "'%p' object is not iterable", value);
    }

    private PException nonIterator(Object value) {
        return raise(TypeError, "iter() returned non-iterator of type '%p'", value);
    }

    @ImportStatic(SpecialMethodNames.class)
    abstract static class IsIteratorObjectNode extends Node {

        public abstract boolean execute(Object o);

        @Specialization
        boolean doPIterator(@SuppressWarnings("unused") PBuiltinIterator it) {
            // a PIterator object is guaranteed to be an iterator object
            return true;
        }

        @Specialization
        boolean doPIterator(Object it,
                        @Cached("create()") GetClassNode getClassNode,
                        @Cached("create(__NEXT__)") LookupAttributeInMRONode lookupAttributeNode) {
            return lookupAttributeNode.execute(getClassNode.execute(it)) != PNone.NO_VALUE;
        }

        public static IsIteratorObjectNode create() {
            return IsIteratorObjectNodeGen.create();
        }
    }
}
