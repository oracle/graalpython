/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.referencetype;

import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.objectHashCode;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALLBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReferenceType)
public final class ReferenceTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReferenceTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object init(Object self, Object obj, Object callback) {
            return PNone.NONE;
        }
    }

    // ref.__callback__
    @Builtin(name = J___CALLBACK__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallbackPropertyNode extends PythonBuiltinNode {
        @Specialization
        public Object getCallback(PReferenceType self) {
            return self.getCallback();
        }
    }

    // ref.__call__()
    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallNode extends PythonBuiltinNode {
        @Specialization
        public Object call(PReferenceType self) {
            return self.getPyObject();
        }
    }

    // ref.__hash__
    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeHashNode extends PythonUnaryBuiltinNode {
        static long HASH_UNSET = -1;

        @Specialization(guards = "self.getHash() != HASH_UNSET")
        static long getHash(PReferenceType self) {
            return self.getHash();
        }

        @Specialization(guards = "self.getHash() == HASH_UNSET")
        static long computeHash(VirtualFrame frame, PReferenceType self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile referentProfile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object referent = self.getObject();
            if (referentProfile.profile(inliningTarget, referent != null)) {
                long hash = hashNode.execute(frame, inliningTarget, referent);
                self.setHash(hash);
                return hash;
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.WEAK_OBJ_GONE_AWAY);
            }
        }

        @Fallback
        static int hashWrong(@SuppressWarnings("unused") Object self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, "__hash__", "weakref", self);
        }
    }

    // ref.__repr__
    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RefTypeReprNode extends PythonBuiltinNode {
        @Specialization(guards = "self.getObject() == null")
        static TruffleString repr(PReferenceType self,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<weakref at %d; dead>", objectHashCode(self));
        }

        @Specialization(guards = "self.getObject() != null")
        static TruffleString repr(VirtualFrame frame, PReferenceType self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Shared("formatter") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object object = self.getObject();
            Object cls = getClassNode.execute(inliningTarget, object);
            TruffleString className = getNameNode.execute(inliningTarget, cls);
            Object name = lookup.execute(frame, inliningTarget, object, T___NAME__);
            if (name == PNone.NO_VALUE) {
                return simpleTruffleStringFormatNode.format("<weakref at %d; to '%s' at %d>", objectHashCode(self), className, objectHashCode(object));
            } else {
                return simpleTruffleStringFormatNode.format("<weakref at %d; to '%s' at %d (%s)>", objectHashCode(self), className, objectHashCode(object), toStr(name));
            }
        }

        @TruffleBoundary
        private static String toStr(Object o) {
            // TODO GR-37980
            return o.toString();
        }
    }

    // ref.__eq__
    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RefTypeEqNode extends PythonBuiltinNode {
        @Specialization(guards = {"self.getObject() != null", "other.getObject() != null"})
        Object eq(VirtualFrame frame, PReferenceType self, PReferenceType other,
                        @Cached BinaryComparisonNode.EqNode eqNode) {
            return eqNode.executeObject(frame, self.getObject(), other.getObject());
        }

        @Specialization(guards = "self.getObject() == null || other.getObject() == null")
        boolean eq(PReferenceType self, PReferenceType other) {
            return self == other;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // ref.__ne__
    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RefTypeNeNode extends PythonBuiltinNode {
        @Specialization(guards = {"self.getObject() != null", "other.getObject() != null"})
        Object ne(VirtualFrame frame, PReferenceType self, PReferenceType other,
                        @Cached BinaryComparisonNode.NeNode neNode) {
            return neNode.executeObject(frame, self.getObject(), other.getObject());
        }

        @Specialization(guards = "self.getObject() == null || other.getObject() == null")
        boolean ne(PReferenceType self, PReferenceType other) {
            return self != other;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
