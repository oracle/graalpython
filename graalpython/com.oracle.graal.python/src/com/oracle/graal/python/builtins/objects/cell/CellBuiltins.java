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
package com.oracle.graal.python.builtins.objects.cell;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltinsFactory.GetRefNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCell)
public class CellBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CellBuiltinsFactory.getFactories();
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        public boolean eq(VirtualFrame frame, PCell self, PCell other,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return lib.equalsWithState(left, right, lib, PArguments.getThreadState(frame));
            }
            return left == null && right == null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __EQ__, "cell", self);
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBuiltinNode {
        @Specialization
        public boolean ne(VirtualFrame frame, PCell self, PCell other,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return !lib.equalsWithState(left, right, lib, PArguments.getThreadState(frame));
            }
            return left != null || right != null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __NE__, "cell", self);
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends PythonBuiltinNode {
        @Specialization
        public boolean lt(VirtualFrame frame, PCell self, PCell other,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return coerceToBooleanNode.executeBoolean(frame, compareNode.executeWith(frame, left, right));
            }
            return right != null;
        }

        protected static BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__LT__, __GT__, "<");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __LT__, "cell", self);
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends PythonBuiltinNode {
        @Specialization
        public boolean le(VirtualFrame frame, PCell self, PCell other,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return coerceToBooleanNode.executeBoolean(frame, compareNode.executeWith(frame, left, right));
            }
            return left == null;
        }

        protected static BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__LE__, __GE__, "<=");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __LE__, "cell", self);
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends PythonBuiltinNode {
        @Specialization
        public boolean gt(VirtualFrame frame, PCell self, PCell other,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return coerceToBooleanNode.executeBoolean(frame, compareNode.executeWith(frame, left, right));
            }
            return left != null;
        }

        protected static BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__GT__, __LT__, ">");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __GT__, "cell", self);
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends PythonBuiltinNode {
        @Specialization
        public boolean ge(VirtualFrame frame, PCell self, PCell other,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (left != null && right != null) {
                return coerceToBooleanNode.executeBoolean(frame, compareNode.executeWith(frame, left, right));
            }
            return right == null;
        }

        protected static BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.create(__GE__, __LE__, ">=");
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __GE__, "cell", self);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String repr(PCell self,
                        @Cached("create()") GetRefNode getRef,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached("create()") TypeNodes.GetNameNode getNameNode) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                return String.format("<cell at %s: empty>", self.hashCode());
            }
            String typeName = getNameNode.execute(lib.getLazyPythonClass(ref));
            return String.format("<cell at %s: %s object at %s>", self.hashCode(), typeName, ref.hashCode());
        }

        @Fallback
        public Object eq(Object self) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__repr__", "cell", self);
        }
    }

    @Builtin(name = "cell_contents", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class CellContentsNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PCell self, @SuppressWarnings("unused") PNone none,
                        @Cached("create()") GetRefNode getRef) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                throw raise(ValueError, ErrorMessages.IS_EMPTY, "Cell");
            }
            return ref;
        }

        @Specialization(guards = "isDeleteMarker(marker)")
        Object delete(PCell self, @SuppressWarnings("unused") Object marker) {
            self.clearRef();
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(ref)", "!isDeleteMarker(ref)"})
        Object set(PCell self, Object ref) {
            self.setRef(ref);
            return PNone.NONE;
        }
    }

    public abstract static class GetRefNode extends Node {
        public abstract Object execute(PCell self);

        protected static Assumption singleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        @Specialization(guards = "self == cachedSelf", assumptions = {"cachedSelf.isEffectivelyFinalAssumption()", "singleContextAssumption"}, limit = "1")
        Object cached(@SuppressWarnings("unused") PCell self,
                        @SuppressWarnings("unused") @Cached("singleContextAssumption()") Assumption singleContextAssumption,
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
