/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.ErrorMessages.INTEGER_REQUIRED;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnicodeError)
public final class UnicodeErrorBuiltins extends PythonBuiltins {
    public static final int IDX_ENCODING = 0;
    public static final int IDX_OBJECT = 1;
    public static final int IDX_START = 2;
    public static final int IDX_END = 3;
    public static final int IDX_REASON = 4;
    public static final int UNICODE_ERR_NUM_ATTRS = IDX_REASON + 1;

    public static final BaseExceptionAttrNode.StorageFactory UNICODE_ERROR_ATTR_FACTORY = (args) -> new Object[UNICODE_ERR_NUM_ATTRS];

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnicodeErrorBuiltinsFactory.getFactories();
    }

    public static TruffleString getArgAsString(Node inliningTarget, Object[] args, int index, PRaiseNode raiseNode, CastToTruffleStringNode castNode) {
        if (args.length < index + 1 || !PGuards.isString(args[index])) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError);
        } else {
            return castNode.execute(inliningTarget, args[index]);
        }
    }

    public static int getArgAsInt(Node inliningTarget, Object[] args, int index, PRaiseNode raiseNode, CastToJavaIntExactNode castNode) {
        if (args.length < index + 1 || !(PGuards.isInteger(args[index]) || PGuards.isPInt(args[index]))) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError);
        } else {
            return castNode.execute(inliningTarget, args[index]);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetArgAsBytesNode extends PNodeWithContext {
        abstract PBytes execute(VirtualFrame frame, Node inliningTarget, Object val);

        @Specialization
        @TruffleBoundary
        static PBytes doString(TruffleString value,
                        @Bind PythonLanguage language) {
            // TODO GR-37601: cbasca cPython works directly with bytes while we have Java strings
            // which are encoded, here we decode using the system encoding but this might not be the
            // correct / ideal case
            return PFactory.createBytes(language, value.toJavaStringUncached().getBytes());
        }

        @Specialization
        static PBytes doBytes(PBytes value) {
            return value;
        }

        @Specialization(guards = {"!isPBytes(value)", "!isString(value)"})
        static PBytes doOther(VirtualFrame frame, Object value,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonBufferAccessLibrary bufferLib,
                        @Bind PythonLanguage language) {
            try {
                final byte[] buffer = bufferLib.getInternalOrCopiedByteArray(value);
                final int bufferLength = bufferLib.getBufferLength(value);
                return PFactory.createBytes(language, buffer, bufferLength);
            } finally {
                bufferLib.release(value, frame, indirectCallData);
            }
        }
    }

    public static Object getArgAsBytes(VirtualFrame frame, Node inliningTarget, Object[] args, int index, PRaiseNode raiseNode, GetArgAsBytesNode getArgAsBytesNode) {
        if (args.length < index + 1) {
            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError);
        } else {
            return getArgAsBytesNode.execute(frame, inliningTarget, args[index]);
        }
    }

    @Builtin(name = "encoding", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception encoding")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorEncodingNode extends PythonBuiltinNode {
        @Specialization
        static Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_ENCODING, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "object", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception object")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorNode extends PythonBuiltinNode {
        @Specialization
        static Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception start")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorStartNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PBaseException self, PNone none,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, none, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setBool(PBaseException self, boolean value,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value ? 1 : 0, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setInt(PBaseException self, int value,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setInt(PBaseException self, long value,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, (int) value, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setPInt(PBaseException self, PInt value,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, castToJavaIntExactNode.execute(inliningTarget, value), IDX_START, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization(guards = {"!isNoValue(value)", "!canBeInteger(value)"})
        @SuppressWarnings("unused")
        static Object generic(PBaseException self, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, INTEGER_REQUIRED);
        }
    }

    @Builtin(name = "end", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception end")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorEndNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PBaseException self, PNone none,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, none, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setBool(PBaseException self, boolean value,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value ? 1 : 0, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setInt(PBaseException self, int value,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization
        static Object setPInt(PBaseException self, PInt value,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Shared @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, castToJavaIntExactNode.execute(inliningTarget, value), IDX_END, UNICODE_ERROR_ATTR_FACTORY);
        }

        @Specialization(guards = {"!isNoValue(value)", "!canBeInteger(value)"})
        @SuppressWarnings("unused")
        static Object generic(PBaseException self, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, INTEGER_REQUIRED);
        }
    }

    @Builtin(name = "reason", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception reason")
    @GenerateNodeFactory
    public abstract static class UnicodeErrorReasonNode extends PythonBuiltinNode {
        @Specialization
        static Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY);
        }
    }
}
