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
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.GetSetDescriptor)
public class GetSetDescriptorTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GetSetDescriptorTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSetReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object repr(GetSetDescriptor descr) {
            return String.format("<attribute '%s' of '%s' objects>", descr.getName(), descr.getType().getName());
        }
    }

    abstract static class GetSetNode extends PythonTernaryBuiltinNode {

        private final IsBuiltinClassProfile isNoneBuiltinClassProfile = IsBuiltinClassProfile.create();
        private final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
        private final IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();
        private final BranchProfile errorBranch = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L70
        protected boolean descr_check(GetSetDescriptor descr, Object obj, PythonClass type) {
            if (PGuards.isNone(obj)) {
                if (!isNoneBuiltinClassProfile.profileClass(type, PythonBuiltinClassType.PNone)) {
                    return true;
                }
            }
            LazyPythonClass descrType = descr.getType();
            if (isBuiltinProfile.profile(descrType instanceof PythonBuiltinClassType)) {
                PythonBuiltinClassType builtinClassType = (PythonBuiltinClassType) descrType;
                for (PythonClass o : type.getMethodResolutionOrder()) {
                    if (isBuiltinClassProfile.profileClass(o, builtinClassType)) {
                        return false;
                    }
                }
            } else {
                for (PythonClass o : type.getMethodResolutionOrder()) {
                    if (o == descrType) {
                        return false;
                    }
                }
            }
            errorBranch.enter();
            throw raise(TypeError, "descriptor '%s' for '%s' objects doesn't apply to '%s' object", descr.getName(), descrType.getName(), type.getName());
        }
    }

    @Builtin(name = __GET__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetGetNode extends GetSetNode {
        @Child CallUnaryMethodNode callNode = CallUnaryMethodNode.create();
        private final BranchProfile branchProfile = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L149
        @Specialization
        Object get(GetSetDescriptor descr, Object obj, PythonClass type) {
            if (descr_check(descr, obj, type)) {
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

    @Builtin(name = __SET__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetSetNode extends GetSetNode {
        @Child GetClassNode getClassNode = GetClassNode.create();
        @Child CallBinaryMethodNode callNode = CallBinaryMethodNode.create();
        private final BranchProfile branchProfile = BranchProfile.create();

        @Specialization
        Object set(GetSetDescriptor descr, Object obj, Object value) {
            // the noneType is not important here - there are no setters on None
            if (descr_check(descr, obj, getClassNode.execute(obj))) {
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
}
