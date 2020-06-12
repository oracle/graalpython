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
package com.oracle.graal.python.builtins.objects.getsetdescriptor;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELETE__;
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
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
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

    abstract static class DescriptorCheckNode extends PNodeWithContext {

        @Child private GetMroNode getMroNode;
        @Child private GetNameNode getNameNode;
        @Child private IsSameTypeNode isSameTypeNode;
        @Child private PRaiseNode raiseNode;

        @Child private IsBuiltinClassProfile isBuiltinPythonClassObject = IsBuiltinClassProfile.create();
        private final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();
        @Child private IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();
        private final BranchProfile errorBranch = BranchProfile.create();

        public abstract boolean execute(LazyPythonClass descrType, String name, Object obj);

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L70
        @Specialization(limit = "3")
        boolean descr_check(LazyPythonClass descrType, String name, Object obj,
                        @CachedLibrary("obj") PythonObjectLibrary lib) {
            if (PGuards.isNone(obj)) {
                // object's descriptors (__class__,...) need to work on every object including None
                if (!isBuiltinPythonClassObject.profileClass(descrType, PythonBuiltinClassType.PythonObject)) {
                    return true;
                }
            }
            Object type = lib.getLazyPythonClass(obj);
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
            throw getRaiseNode().raise(TypeError, ErrorMessages.DESC_S_FOR_S_DOESNT_APPLY_TO_S, name, getTypeName(descrType), getTypeName(type));
        }

        private PythonAbstractClass[] getMro(Object clazz) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(clazz);
        }

        private Object getTypeName(Object descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }

        private boolean isSameType(Object left, Object right) {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(IsSameTypeNodeGen.create());
            }
            return isSameTypeNode.execute(left, right);
        }

        private PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetGetNode extends PythonTernaryBuiltinNode {
        @Child private GetNameNode getNameNode;
        private final BranchProfile branchProfile = BranchProfile.create();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L149
        @Specialization
        Object get(VirtualFrame frame, GetSetDescriptor descr, Object obj, @SuppressWarnings("unused") Object type,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached CallUnaryMethodNode callNode) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getName(), obj)) {
                return descr;
            }
            if (descr.getGet() != null) {
                return callNode.executeObject(frame, descr.getGet(), obj);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_READABLE, descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        Object getSlot(HiddenKeyDescriptor descr, Object obj, @SuppressWarnings("unused") Object type,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached ConditionProfile profile) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getKey().getName(), obj)) {
                return descr;
            }
            Object val = readNode.execute(obj, descr.getKey());
            if (profile.profile(val != PNone.NO_VALUE)) {
                return val;
            }
            throw raise(AttributeError, descr.getKey().getName());
        }

        private Object getTypeName(LazyPythonClass descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }
    }

    @Builtin(name = __SET__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetSetSetNode extends PythonTernaryBuiltinNode {
        @Child private GetNameNode getNameNode;
        private final BranchProfile branchProfile = BranchProfile.create();

        @Specialization
        Object set(VirtualFrame frame, GetSetDescriptor descr, Object obj, Object value,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached CallBinaryMethodNode callNode) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getName(), obj)) {
                return descr;
            }
            if (descr.getSet() != null) {
                return callNode.executeObject(frame, descr.getSet(), obj, value);
            } else {
                branchProfile.enter();
                throw raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_WRITABLE, descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        Object setSlot(HiddenKeyDescriptor descr, Object obj, Object value,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached WriteAttributeToObjectNode writeNode) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getKey().getName(), obj)) {
                return descr;
            }
            return writeNode.execute(obj, descr.getKey(), value);
        }

        private Object getTypeName(LazyPythonClass descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }
    }

    @Builtin(name = __DELETE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetSetDeleteNode extends PythonBinaryBuiltinNode {
        @Child private GetNameNode getNameNode;
        private final BranchProfile branchProfile = BranchProfile.create();

        @Specialization
        Object delete(VirtualFrame frame, GetSetDescriptor descr, Object obj,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached CallBinaryMethodNode callNode) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getName(), obj)) {
                return descr;
            }
            if (descr.allowsDelete()) {
                return callNode.executeObject(frame, descr.getSet(), obj, DescriptorDeleteMarker.INSTANCE);
            } else {
                branchProfile.enter();
                if (descr.getSet() != null) {
                    throw raise(TypeError, ErrorMessages.CANNOT_DELETE_ATTRIBUTE, getTypeName(descr.getType()), descr.getName());
                } else {
                    throw raise(AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
                }
            }
        }

        @Specialization
        Object deleteSlot(HiddenKeyDescriptor descr, Object obj,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached WriteAttributeToObjectNode writeNode) {
            if (descriptorCheckNode.execute(descr.getType(), descr.getKey().getName(), obj)) {
                return descr;
            }
            writeNode.execute(obj, descr.getKey(), PNone.NO_VALUE);
            return PNone.NONE;
        }

        private Object getTypeName(LazyPythonClass descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }
    }
}
