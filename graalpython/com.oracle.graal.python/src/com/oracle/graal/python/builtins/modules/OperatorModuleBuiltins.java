/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes.GetLength;
import com.oracle.graal.python.lib.PyNumberAbsoluteNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAndNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAddNode;
import com.oracle.graal.python.lib.PyNumberInPlaceAndNode;
import com.oracle.graal.python.lib.PyNumberInPlaceFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceLshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceMultiplyNode;
import com.oracle.graal.python.lib.PyNumberInPlaceOrNode;
import com.oracle.graal.python.lib.PyNumberInPlacePowerNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRemainderNode;
import com.oracle.graal.python.lib.PyNumberInPlaceRshiftNode;
import com.oracle.graal.python.lib.PyNumberInPlaceSubtractNode;
import com.oracle.graal.python.lib.PyNumberInPlaceTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberInPlaceXorNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberLshiftNode;
import com.oracle.graal.python.lib.PyNumberMatrixMultiplyNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyNumberPositiveNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberRshiftNode;
import com.oracle.graal.python.lib.PyNumberSubtractNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberXorNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PySequenceConcatNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PySequenceInPlaceConcatNode;
import com.oracle.graal.python.lib.PySequenceIterSearchNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.InteropCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = OperatorModuleBuiltins.MODULE_NAME)
public final class OperatorModuleBuiltins extends PythonBuiltins {

    protected static final String MODULE_NAME = "_operator";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OperatorModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "truth", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruthNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            return isTrueNode.execute(frame, object);
        }
    }

    @Builtin(name = "not_", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NotNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doObject(VirtualFrame frame, Object object,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            return !isTrueNode.execute(frame, object);
        }
    }

    @Builtin(name = "is_", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsOperatorNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doObject(Object left, Object right,
                        @Cached IsNode isNode) {
            return isNode.execute(left, right);
        }
    }

    @Builtin(name = "is_not", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsNotNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doObject(Object left, Object right,
                        @Cached IsNode isNode) {
            return !isNode.execute(left, right);
        }
    }

    @Builtin(name = "add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberAddNode addNode) {
            return addNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "sub", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberSubtractNode subNode) {
            return subNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "matmul", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MatmulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberMatrixMultiplyNode matmulNode) {
            return matmulNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "floordiv", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberFloorDivideNode floorDivideNode) {
            return floorDivideNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "truediv", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberTrueDivideNode trueDivideNode) {
            return trueDivideNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "mod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ModNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberRemainderNode remainderNode) {
            return remainderNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "lshift", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LshiftNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberLshiftNode lshiftNode) {
            return lshiftNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "rshift", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RshiftNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberRshiftNode rshiftNode) {
            return rshiftNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "and_", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberAndNode andNode) {
            return andNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "xor", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberXorNode xorNode) {
            return xorNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "or_", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberOrNode orNode) {
            return orNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "pow", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PowNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberPowerNode powerNode) {
            return powerNode.execute(frame, left, right, PNone.NONE);
        }
    }

    @Builtin(name = "neg", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object object,
                        @Cached PyNumberNegativeNode negNode) {
            return negNode.execute(frame, object);
        }
    }

    @Builtin(name = "pos", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object object,
                        @Cached PyNumberPositiveNode posNode) {
            return posNode.execute(frame, object);
        }
    }

    @Builtin(name = "abs", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object object,
                        @Cached PyNumberAbsoluteNode absNode) {
            return absNode.execute(frame, object);
        }
    }

    @Builtin(name = "inv", minNumOfPositionalArgs = 1)
    @Builtin(name = "invert", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InvertNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object object,
                        @Cached PyNumberInvertNode invertNode) {
            return invertNode.execute(frame, object);
        }
    }

    @Builtin(name = "getitem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object value, Object index,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetItem getItem) {
            return getItem.execute(frame, inliningTarget, value, index);
        }
    }

    @Builtin(name = "setitem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PNone doObject(VirtualFrame frame, Object object, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetItem setItem) {
            setItem.execute(frame, inliningTarget, object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "delitem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone doObject(VirtualFrame frame, Object object, Object key,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDelItem delItem) {
            delItem.execute(frame, inliningTarget, object, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = "concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConcatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PySequenceConcatNode concatNode) {
            return concatNode.execute(frame, inliningTarget, left, right);
        }
    }

    @Builtin(name = "contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doObject(VirtualFrame frame, Object container, Object key,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, container, key);
        }
    }

    @Builtin(name = "indexOf", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IndexOfNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int doObject(VirtualFrame frame, Object container, Object key,
                        @Bind Node inliningTarget,
                        @Cached PySequenceIterSearchNode iterSearchNode) {
            return iterSearchNode.execute(frame, inliningTarget, container, key, PySequenceIterSearchNode.PY_ITERSEARCH_INDEX);
        }
    }

    @Builtin(name = "countOf", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountOfNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int doObject(VirtualFrame frame, Object container, Object key,
                        @Bind Node inliningTarget,
                        @Cached PySequenceIterSearchNode iterSearchNode) {
            return iterSearchNode.execute(frame, inliningTarget, container, key, PySequenceIterSearchNode.PY_ITERSEARCH_COUNT);
        }
    }

    @Builtin(name = "iconcat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IConcatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PySequenceInPlaceConcatNode concatNode) {
            return concatNode.execute(frame, inliningTarget, left, right);
        }
    }

    @Builtin(name = "iadd", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceAddNode addNode) {
            return addNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "isub", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ISubNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceSubtractNode subNode) {
            return subNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "mul", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberMultiplyNode mulNode) {
            return mulNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "imul", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceMultiplyNode mulNode) {
            return mulNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "imatmul", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IMatmulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceMatrixMultiplyNode matmulNode) {
            return matmulNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "ifloordiv", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IFloorDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceFloorDivideNode floorDivideNode) {
            return floorDivideNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "itruediv", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ITrueDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceTrueDivideNode trueDivideNode) {
            return trueDivideNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "imod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IModNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceRemainderNode remainderNode) {
            return remainderNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "ilshift", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ILshiftNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceLshiftNode lshiftNode) {
            return lshiftNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "irshift", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IRshiftNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceRshiftNode rshiftNode) {
            return rshiftNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "iand", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAndNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceAndNode andNode) {
            return andNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "ixor", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IXorNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceXorNode xorNode) {
            return xorNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "ior", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlaceOrNode orNode) {
            return orNode.execute(frame, left, right);
        }
    }

    @Builtin(name = "ipow", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IPowNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Cached PyNumberInPlacePowerNode powerNode) {
            return powerNode.execute(frame, left, right, PNone.NONE);
        }
    }

    @Builtin(name = "eq", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_EQ);
        }
    }

    @Builtin(name = "ne", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_NE);
        }
    }

    @Builtin(name = "lt", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_LT);
        }
    }

    @Builtin(name = "le", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_LE);
        }
    }

    @Builtin(name = "gt", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_GT);
        }
    }

    @Builtin(name = "ge", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare compareNode) {
            return compareNode.execute(frame, inliningTarget, left, right, RichCmpOp.Py_GE);
        }
    }

    @Builtin(name = "length_hint", minNumOfPositionalArgs = 1, parameterNames = {"obj", "default"})
    @ArgumentClinic(name = "default", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class LengthHintNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static int doObject(VirtualFrame frame, Object object, int defaultValue,
                        @Bind Node inliningTarget,
                        @Cached GetLength getLength) {
            int length = getLength.execute(frame, inliningTarget, object);
            if (length >= 0) {
                return length;
            }
            return defaultValue;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return OperatorModuleBuiltinsClinicProviders.LengthHintNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "call", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class CallOperatorNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords,
                        @Cached CallNode callNode) {
            return callNode.execute(frame, self, arguments, keywords);
        }
    }

    // _compare_digest
    @Builtin(name = "_compare_digest", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean compare(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") InteropCallData callData,
                        @Cached CastToJavaStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode raiseNode) {
            try {
                String leftString = cast.execute(left);
                String rightString = cast.execute(right);
                return tscmp(leftString, rightString);
            } catch (CannotCastException e) {
                if (!bufferAcquireLib.hasBuffer(left) || !bufferAcquireLib.hasBuffer(right)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_OR_COMBINATION_OF_TYPES, left, right);
                }
                Object savedState = InteropCallContext.enter(frame, inliningTarget, callData);
                Object leftBuffer = bufferAcquireLib.acquireReadonly(left);
                try {
                    Object rightBuffer = bufferAcquireLib.acquireReadonly(right);
                    try {
                        return tscmp(bufferLib.getCopiedByteArray(leftBuffer), bufferLib.getCopiedByteArray(rightBuffer));
                    } finally {
                        bufferLib.release(rightBuffer);
                    }
                } finally {
                    bufferLib.release(leftBuffer);
                    InteropCallContext.exit(frame, inliningTarget, callData, savedState);
                }
            }
        }

        // Comparison that's safe against timing attacks
        @TruffleBoundary
        private static boolean tscmp(String leftIn, String right) {
            String left = leftIn;
            int result = 0;
            if (left.length() != right.length()) {
                left = right;
                result = 1;
            }
            for (int i = 0; i < left.length(); i++) {
                result |= left.charAt(i) ^ right.charAt(i);
            }
            return result == 0;
        }

        @TruffleBoundary
        private static boolean tscmp(byte[] leftIn, byte[] right) {
            byte[] left = leftIn;
            int result = 0;
            if (left.length != right.length) {
                left = right;
                result = 1;
            }
            for (int i = 0; i < left.length; i++) {
                result |= left[i] ^ right[i];
            }
            return result == 0;
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object asIndex(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode index) {
            return index.execute(frame, inliningTarget, value);
        }
    }
}
