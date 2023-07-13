/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

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
        static Object doObject(VirtualFrame frame, Object object,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            return isTrueNode.execute(frame, object);
        }
    }

    @Builtin(name = "getitem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObject(VirtualFrame frame, Object value, Object index,
                        @Cached PyObjectGetItem getItem) {
            return getItem.execute(frame, value, index);
        }
    }

    // _compare_digest
    @Builtin(name = "_compare_digest", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean compare(VirtualFrame frame, Object left, Object right,
                        @Cached CastToJavaStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            try {
                String leftString = cast.execute(left);
                String rightString = cast.execute(right);
                return tscmp(leftString, rightString);
            } catch (CannotCastException e) {
                if (!bufferAcquireLib.hasBuffer(left) || !bufferAcquireLib.hasBuffer(right)) {
                    throw raise(TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_OR_COMBINATION_OF_TYPES, left, right);
                }
                Object savedState = IndirectCallContext.enter(frame, this);
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
                    IndirectCallContext.exit(frame, this, savedState);
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
                        @Cached PyNumberIndexNode index) {
            return index.execute(frame, value);
        }
    }
}
