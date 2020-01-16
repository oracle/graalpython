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
package com.oracle.graal.python.builtins.objects.cell;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltinsFactory.GetRefNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCell)
public class CellBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CellBuiltinsFactory.getFactories();
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2, needsFrame = false)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        public boolean eq(PCell self, PCell other,
                        @Cached("create()") GetRefNode getRefL,
                        @Cached("create()") GetRefNode getRefR) {
            return getRefL.execute(self).equals(getRefR.execute(other));
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'cell' object but received a '%p'", self);
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeqNode extends PythonBuiltinNode {
        @Specialization
        public boolean neq(PCell self, PCell other,
                        @Cached("create()") GetRefNode getRefL,
                        @Cached("create()") GetRefNode getRefR) {
            return !getRefL.execute(self).equals(getRefR.execute(other));
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__neq__' requires a 'cell' object but received a '%p'", self);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String repr(PCell self,
                        @Cached("create()") GetRefNode getRef,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("create()") TypeNodes.GetNameNode getNameNode) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                return String.format("<cell at %s: empty>", self.hashCode());
            }
            String typeName = getNameNode.execute(getClassNode.execute(ref));
            return String.format("<cell at %s: %s object at %s>", self.hashCode(), typeName, ref.hashCode());
        }

        @Fallback
        public Object eq(Object self) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__repr__' requires a 'cell' object but received a '%p'", self);
        }
    }

    @Builtin(name = "cell_contents", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CellContentsNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        public Object get(PCell self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") GetRefNode getRef) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                throw raise(ValueError, "Cell is empty");
            }
            return ref;
        }

        @Specialization(guards = "!isNoValue(ref)")
        public Object set(PCell self, Object ref) {
            self.setRef(ref);
            return PNone.NONE;
        }
    }

    public abstract static class GetRefNode extends Node {
        public abstract Object execute(PCell self);

        @Specialization(guards = "self == cachedSelf", assumptions = "cachedSelf.isEffectivelyFinalAssumption()", limit = "1")
        Object cached(@SuppressWarnings("unused") PCell self,
                        @SuppressWarnings("unused") @Cached("self") PCell cachedSelf,
                        @Cached("self.getRef()") Object ref) {
            return ref;
        }

        @Specialization(replaces = "cached")
        Object uncached(PCell self) {
            return self.getRef();
        }

        public static GetRefNode create() {
            return GetRefNodeGen.create();
        }
    }

}
