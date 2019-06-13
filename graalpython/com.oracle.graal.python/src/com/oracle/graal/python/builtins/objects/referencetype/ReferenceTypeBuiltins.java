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
package com.oracle.graal.python.builtins.objects.referencetype;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALLBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReferenceType)
public class ReferenceTypeBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReferenceTypeBuiltinsFactory.getFactories();
    }

    // ref.__callback__
    @Builtin(name = __CALLBACK__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallbackPropertyNode extends PythonBuiltinNode {
        @Specialization
        public Object getCallback(PReferenceType self) {
            return self.getCallback();
        }
    }

    // ref.__call__()
    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeCallNode extends PythonBuiltinNode {
        @Specialization
        public Object call(PReferenceType self) {
            return self.getPyObject();
        }
    }

    // ref.__hash__
    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeHashNode extends PythonBuiltinNode {
        @Specialization(guards = "self.getObject() != null")
        public int hash(PReferenceType self) {
            return self.getHash();
        }

        @Specialization(guards = "self.getObject() == null")
        public int hashGone(@SuppressWarnings("unused") PReferenceType self) {
            throw raise(PythonErrorType.TypeError, "weak object has gone away");
        }

        @Fallback
        public int hashWrong(@SuppressWarnings("unused") Object self) {
            throw raise(PythonErrorType.TypeError, "descriptor '__hash__' requires a 'weakref' object but received a '%p'", self);
        }
    }

    // ref.__repr__
    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RefTypeReprNode extends PythonBuiltinNode {
        @Specialization(guards = "self.getObject() == null")
        @TruffleBoundary
        public String repr(PReferenceType self) {
            return String.format("<weakref at %s; dead>", self.hashCode());
        }

        @Specialization(guards = "self.getObject() != null")
        @TruffleBoundary
        public String repr(PReferenceType self,
                        @Cached("create(__NAME__)") LookupInheritedAttributeNode getNameNode) {
            Object object = self.getObject();
            Object name = getNameNode.execute(object);
            if (name == PNone.NO_VALUE) {
                return String.format("<weakref at %s; to '%s' at %s>", self.hashCode(), object.hashCode(), object.hashCode());
            } else {
                return String.format("<weakref at %s; to '%s' at %s (%s)>", self.hashCode(), name, object.hashCode(), object);
            }
        }
    }

    // ref.__eq__
    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RefTypeEqNode extends PythonBuiltinNode {
        @Specialization(guards = {"self.getObject() != null", "other.getObject() != null"})
        boolean eq(VirtualFrame frame, PReferenceType self, PReferenceType other,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {
            return eqNode.executeBool(frame, self.getObject(), other.getObject());
        }

        @Specialization(guards = "self.getObject() == null || other.getObject() == null")
        boolean eq(PReferenceType self, PReferenceType other) {
            return self == other;
        }
    }

    // ref.__ne__
    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RefTypeNeNode extends PythonBuiltinNode {
        @Specialization(guards = {"self.getObject() != null", "other.getObject() != null"})
        boolean ne(VirtualFrame frame, PReferenceType self, PReferenceType other,
                        @Cached("create(__NE__, __NE__, __NE__)") BinaryComparisonNode neNode) {
            return neNode.executeBool(frame, self.getObject(), other.getObject());
        }

        @Specialization(guards = "self.getObject() == null || other.getObject() == null")
        boolean ne(PReferenceType self, PReferenceType other) {
            return self != other;
        }
    }
}
