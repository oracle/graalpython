/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.io.IONodes._CHUNK_SIZE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * Built-in functions shared between {@link PythonBuiltinClassType#GetSetDescriptor} and
 * {@link PythonBuiltinClassType#MemberDescriptor}.
 */
@CoreFunctions(extendClasses = {PythonBuiltinClassType.GetSetDescriptor, PythonBuiltinClassType.MemberDescriptor})
public class DescriptorBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DescriptorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class QualnameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor self,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            return PythonUtils.format("%s.%s", readQualNameNode.executeObject(frame, self.getType()), self.getName());
        }

        @Specialization
        static Object doHiddenKeyDescriptor(VirtualFrame frame, HiddenKeyDescriptor self,
                        @Cached("create(__QUALNAME__)") GetFixedAttributeNode readQualNameNode) {
            return PythonUtils.format("%s.%s", readQualNameNode.executeObject(frame, self.getType()), self.getKey().getName());
        }
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGetSetDescriptor(GetSetDescriptor self) {
            return self.getName();
        }

        @Specialization
        static Object doHiddenKeyDescriptor(HiddenKeyDescriptor self) {
            return self.getKey().getName();
        }
    }

    static final class DescriptorCheckNode extends Node {

        @Child private GetMroNode getMroNode;
        @Child private GetNameNode getNameNode;
        @Child private IsSameTypeNode isSameTypeNode;
        @Child private PRaiseNode raiseNode;
        @Child private PythonObjectLibrary lib;

        @Child private IsBuiltinClassProfile isBuiltinPythonClassObject = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

        private final ConditionProfile isBuiltinProfile = ConditionProfile.createBinaryProfile();

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L70
        public boolean execute(Object descrType, String name, Object obj) {
            if (PGuards.isNone(obj)) {
                // object's descriptors (__class__,...) need to work on every object including None
                if (!isBuiltinPythonClassObject.profileClass(descrType, PythonBuiltinClassType.PythonObject)) {
                    return true;
                }
            }
            Object type = ensureLib().getLazyPythonClass(obj);
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

        private PythonObjectLibrary ensureLib() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(3));
            }
            return lib;
        }

        public static DescriptorCheckNode create() {
            return new DescriptorCheckNode();
        }
    }

    public abstract static class DescrGetNode extends AbstractDescrNode {
        public abstract Object execute(VirtualFrame frame, Object descr, Object obj);

        @Specialization
        Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor descr, Object obj,
                        @Cached CallUnaryMethodNode callNode) {
            if (descr.getGet() != null) {
                return callNode.executeObject(frame, descr.getGet(), obj);
            } else {
                throw getRaiseNode().raise(AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_READABLE, descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        Object doHiddenKeyDescriptor(HiddenKeyDescriptor descr, Object obj,
                        @Cached ReadAttributeFromObjectNode readNode) {
            Object val = readNode.execute(obj, descr.getKey());
            if (val != PNone.NO_VALUE) {
                return val;
            }
            throw getRaiseNode().raise(AttributeError, descr.getKey().getName());
        }
    }

    public abstract static class DescrSetNode extends AbstractDescrNode {
        public abstract Object execute(VirtualFrame frame, Object descr, Object obj, Object value);

        @Specialization
        Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor descr, Object obj, Object value,
                        @Cached CallBinaryMethodNode callNode) {
            if (descr.getSet() != null) {
                return callNode.executeObject(frame, descr.getSet(), obj, value);
            } else {
                throw getRaiseNode().raise(AttributeError, ErrorMessages.ATTR_S_OF_S_OBJ_IS_NOT_WRITABLE, descr.getName(), getTypeName(descr.getType()));
            }
        }

        @Specialization
        static Object doHiddenKeyDescriptor(HiddenKeyDescriptor descr, Object obj, Object value,
                        @Cached WriteAttributeToObjectNode writeNode) {
            return writeNode.execute(obj, descr.getKey(), value);
        }
    }

    public abstract static class DescrDeleteNode extends AbstractDescrNode {
        public abstract Object execute(VirtualFrame frame, Object descr, Object obj);

        private static boolean isChunkSize(GetSetDescriptor descr) {
            // This is a special error message case. see
            // Modules/_io/textio.c:textiowrapper_chunk_size_set
            return PString.equals(_CHUNK_SIZE, descr.getName());
        }

        @Specialization
        Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor descr, Object obj,
                        @Cached CallBinaryMethodNode callNode,
                        @Cached BranchProfile branchProfile) {
            if (descr.allowsDelete()) {
                return callNode.executeObject(frame, descr.getSet(), obj, DescriptorDeleteMarker.INSTANCE);
            } else {
                branchProfile.enter();
                if (descr.getSet() != null) {
                    if (isChunkSize(descr)) {
                        throw getRaiseNode().raise(AttributeError, "cannot delete attribute");
                    }
                    throw getRaiseNode().raise(TypeError, ErrorMessages.CANNOT_DELETE_ATTRIBUTE, getTypeName(descr.getType()), descr.getName());
                } else {
                    throw getRaiseNode().raise(AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
                }
            }
        }

        @Specialization
        Object doHiddenKeyDescriptor(HiddenKeyDescriptor descr, Object obj,
                        @Cached WriteAttributeToObjectNode writeNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached ConditionProfile profile) {
            // PyMember_SetOne - Check if the attribute is set.
            if (profile.profile(readNode.execute(obj, descr.getKey()) != PNone.NO_VALUE)) {
                writeNode.execute(obj, descr.getKey(), PNone.NO_VALUE);
                return PNone.NONE;
            }
            throw getRaiseNode().raise(PythonBuiltinClassType.AttributeError, "%s", descr.getKey().getName());
        }
    }

    private abstract static class AbstractDescrNode extends Node {
        @Child private GetNameNode getNameNode;
        @Child private PRaiseNode raiseNode;

        protected Object getTypeName(Object descrType) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            return getNameNode.execute(descrType);
        }

        protected PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }
    }
}
