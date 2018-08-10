/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.getsetdescriptor;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = GetSetDescriptor.class)
public class GetSetDescriptorTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GetSetDescriptorTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class GetSetReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object repr(GetSetDescriptor descr) {
            return String.format("<attribute '%s' of '%s' objects>", descr.getName(), descr.getType().getName());
        }
    }

    @Builtin(name = __GET__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class GetSetGetNode extends PythonTernaryBuiltinNode {
        @Child CallUnaryMethodNode callNode = CallUnaryMethodNode.create();
        private final BranchProfile branchProfile = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L149
        @Specialization
        Object get(GetSetDescriptor descr, Object obj, PythonClass type) {
            if (descr_check(getCore(), descr, obj, type, getCore().lookupType(PythonBuiltinClassType.PNone))) {
                return descr;
            }
            if (descr.getGet() != null) {
                return callNode.executeObject(descr.getGet(), obj);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, "attribute '%s' of '%s' objects is not readable", descr.getName(), descr.getType().getName());
            }
        }
    }

    @Builtin(name = __SET__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class GetSetSetNode extends PythonTernaryBuiltinNode {
        @Child GetClassNode getClassNode = GetClassNode.create();
        @Child CallBinaryMethodNode callNode = CallBinaryMethodNode.create();
        private final BranchProfile branchProfile = BranchProfile.create();

        @Specialization
        Object set(GetSetDescriptor descr, Object obj, Object value) {
            // the noneType is not important here - there are no setters on None
            if (descr_check(getCore(), descr, obj, getClassNode.execute(obj), null)) {
                return descr;
            }
            if (descr.getSet() != null) {
                return callNode.executeObject(descr.getSet(), obj, value);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, "attribute '%s' of '%s' objects is not writable", descr.getName(), descr.getType().getName());
            }
        }
    }

    // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L70
    private static boolean descr_check(PythonCore core, GetSetDescriptor descr, Object obj, PythonClass type, PythonBuiltinClass noneType) {
        if (PGuards.isNone(obj) && type != noneType) {
            return true;
        }
        for (Object o : type.getMethodResolutionOrder()) {
            if (o == descr.getType()) {
                return false;
            }
        }

        throw core.raise(TypeError, "descriptor '%s' for '%s' objects doesn't apply to '%s' object", descr.getName(), descr.getType().getName(), type.getName());
    }
}
