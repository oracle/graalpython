/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.GetSetDescriptor)
public class GetSetDescriptorTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GetSetDescriptorTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSetReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object repr(GetSetDescriptor descr) {
            return String.format("<attribute '%s' of '%s' objects>", descr.getName(), GetNameNode.doSlowPath(descr.getType()));
        }
    }

    abstract static class GetSetNode extends PythonTernaryBuiltinNode {

        @Child private GetMroNode getMroNode;
        @Child private GetNameNode getNameNode;
        @Child private IsSameTypeNode isSameTypeNode;

        private final IsBuiltinClassProfile isNoneBuiltinClassProfile = IsBuiltinClassProfile.create();
        private final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
        private final IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();
        private final BranchProfile errorBranch = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L70
        protected boolean descr_check(LazyPythonClass descrType, String name, Object obj, LazyPythonClass type) {
            if (PGuards.isNone(obj)) {
                if (!isNoneBuiltinClassProfile.profileClass(type, PythonBuiltinClassType.PNone)) {
                    return true;
                }
            }
            if (isBuiltinProfile.profile(descrType instanceof PythonBuiltinClassType)) {
                PythonBuiltinClassType builtinClassType = (PythonBuiltinClassType) descrType;
                for (PythonAbstractClass o : getMro(type)) {
                    if (isBuiltinClassProfile.profileClass(o, builtinClassType)) {
                        return false;
                    }
                }
            } else {
                for (PythonAbstractClass o : getMro(type)) {
                    if (isSameType(o, descrType)) {
                        return false;
                    }
                }
            }
            errorBranch.enter();
            throw raise(TypeError, "descriptor '%s' for '%s' objects doesn't apply to '%s' object", name, getTypeName(descrType), getTypeName(type));
        }

        private PythonAbstractClass[] getMro(LazyPythonClass clazz) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(clazz);
        }

        protected Object getTypeName(LazyPythonClass descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }

        private boolean isSameType(Object left, Object right) {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(IsSameTypeNode.create());
            }
            return isSameTypeNode.execute(left, right);
        }
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetGetNode extends GetSetNode {
        private final BranchProfile branchProfile = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L149
        @Specialization
        Object get(VirtualFrame frame, GetSetDescriptor descr, Object obj, LazyPythonClass type,
                        @Cached("create()") CallUnaryMethodNode callNode) {
            if (descr_check(descr.getType(), descr.getName(), obj, type)) {
                return descr;
            }
            if (descr.getGet() != null) {
                return callNode.executeObject(frame, descr.getGet(), obj);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, "attribute '%s' of '%s' objects is not readable", descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        Object getSlot(HiddenKeyDescriptor descr, Object obj, LazyPythonClass type,
                        @Cached("create()") ReadAttributeFromObjectNode readNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            if (descr_check(descr.getType(), descr.getKey().getName(), obj, type)) {
                return descr;
            }
            Object val = readNode.execute(obj, descr.getKey());
            if (profile.profile(val != PNone.NO_VALUE)) {
                return val;
            }
            throw raise(AttributeError, descr.getKey().getName());
        }
    }

    @Builtin(name = __SET__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetSetNode extends GetSetNode {
        @Child GetClassNode getClassNode = GetClassNode.create();
        private final BranchProfile branchProfile = BranchProfile.create();

        @Specialization
        Object set(VirtualFrame frame, GetSetDescriptor descr, Object obj, Object value,
                        @Cached("create()") CallBinaryMethodNode callNode) {
            // the noneType is not important here - there are no setters on None
            if (descr_check(descr.getType(), descr.getName(), obj, getClassNode.execute(obj))) {
                return descr;
            }
            if (descr.getSet() != null) {
                return callNode.executeObject(frame, descr.getSet(), obj, value);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, "attribute '%s' of '%s' object is not writable", descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        Object setSlot(HiddenKeyDescriptor descr, Object obj, Object value,
                        @Cached("create()") WriteAttributeToObjectNode writeNode) {
            // the noneType is not important here - there are no setters on None
            if (descr_check(descr.getType(), descr.getKey().getName(), obj, getClassNode.execute(obj))) {
                return descr;
            }
            return writeNode.execute(obj, descr.getKey(), value);
        }
    }
}
