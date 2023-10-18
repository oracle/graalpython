/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_ENCODING;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_END;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_OBJECT;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_REASON;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.IDX_START;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.UNICODE_ERROR_ATTR_FACTORY;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsBytes;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsInt;
import static com.oracle.graal.python.builtins.objects.exception.UnicodeErrorBuiltins.getArgAsString;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnicodeDecodeError)
public final class UnicodeDecodeErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnicodeDecodeErrorBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class UnicodeDecodeErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PBaseException self, Object[] args);

        @Specialization
        static Object initNoArgs(VirtualFrame frame, PBaseException self, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached UnicodeErrorBuiltins.GetArgAsBytesNode getArgAsBytesNode,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached CastToJavaIntExactNode toJavaIntExactNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseInitNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            baseInitNode.execute(self, args);
            // PyArg_ParseTuple(args, "UOnnU"), TODO: add proper error messages
            self.setExceptionAttributes(new Object[]{
                            getArgAsString(inliningTarget, args, 0, raiseNode, toStringNode),
                            getArgAsBytes(frame, inliningTarget, args, 1, raiseNode, getArgAsBytesNode),
                            getArgAsInt(inliningTarget, args, 2, raiseNode, toJavaIntExactNode),
                            getArgAsInt(inliningTarget, args, 3, raiseNode, toJavaIntExactNode),
                            getArgAsString(inliningTarget, args, 4, raiseNode, toStringNode)
            });
            return PNone.NONE;
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class UnicodeEncodeErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString str(VirtualFrame frame, PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached SequenceStorageNodes.GetItemNode getitemNode,
                        @Cached PyObjectStrAsTruffleStringNode strNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            if (self.getExceptionAttributes() == null) {
                // Not properly initialized.
                return T_EMPTY_STRING;
            }

            // Get reason and encoding as strings, which they might not be if they've been
            // modified after we were constructed.
            PBytesLike object = (PBytesLike) attrNode.get(self, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY);
            final int start = attrNode.getInt(self, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
            final int end = attrNode.getInt(self, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
            final TruffleString encoding = strNode.execute(frame, inliningTarget, attrNode.get(self, IDX_ENCODING, UNICODE_ERROR_ATTR_FACTORY));
            final TruffleString reason = strNode.execute(frame, inliningTarget, attrNode.get(self, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY));
            if (start < object.getSequenceStorage().length() && end == start + 1) {
                final int b = getitemNode.executeKnownInt(object.getSequenceStorage(), start);
                String bStr = PythonUtils.formatJString("%02x", b);
                return simpleTruffleStringFormatNode.format("'%s' codec can't decode byte 0x%s in position %d: %s", encoding, bStr, start, reason);
            } else {
                return simpleTruffleStringFormatNode.format("'%s' codec can't decode bytes in position %d-%d: %s", encoding, start, end - 1, reason);
            }
        }
    }

    // Equivalent of make_decode_exception
    @GenerateInline
    @GenerateCached(false)
    public abstract static class MakeDecodeExceptionNode extends Node {

        /**
         * Prepares a {@code UnicodeDecodeError} exception object. It either adjusts the attributes
         * of the provided {@code exceptionObject} or allocates a new one.
         *
         * @param exceptionObject the exception object to adjust or {@code null} to allocate a new
         *            one
         * @return either {@code exceptionObject} if provided or a new exception object
         */
        public abstract PBaseException execute(Node inliningTarget, PBaseException exceptionObject, TruffleString encoding, Object inputObject, int startPos, int endPos, TruffleString reason);

        @Specialization(guards = "exceptionObject == null")
        static PBaseException createNew(Node inliningTarget, @SuppressWarnings("unused") PBaseException exceptionObject, TruffleString encoding, Object inputObject, int startPos, int endPos,
                        TruffleString reason,
                        @Cached(inline = false) CallNode callNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object obj = callNode.execute(UnicodeDecodeError, encoding, inputObject, startPos, endPos, reason);
            if (obj instanceof PBaseException exception) {
                return exception;
            }
            // Shouldn't happen unless the user manually replaces the method, which is really
            // unexpected and shouldn't be permitted at all, but currently it is
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, UnicodeDecodeError, obj);
        }

        @Specialization(guards = "exceptionObject != null")
        static PBaseException updateProvided(PBaseException exceptionObject, @SuppressWarnings("unused") TruffleString encoding, @SuppressWarnings("unused") Object inputObject, int startPos,
                        int endPos, TruffleString reason,
                        @Cached(inline = false) BaseExceptionAttrNode attrNode) {
            attrNode.set(exceptionObject, startPos, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
            attrNode.set(exceptionObject, endPos, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
            attrNode.set(exceptionObject, reason, IDX_REASON, UNICODE_ERROR_ATTR_FACTORY);
            return exceptionObject;
        }
    }

    // Equivalent of PyUnicodeDecodeError_GetObject
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeDecodeErrorGetObjectNode extends Node {

        /**
         * Returns the value of the {@code object} attribute, checking that it is present and a
         * subtype of {@code bytes}.
         */
        public abstract Object execute(Node inliningTarget, PBaseException exceptionObject);

        @Specialization
        static Object doIt(VirtualFrame frame, Node inliningTarget, PBaseException exceptionObject,
                        @Cached(inline = false) BaseExceptionAttrNode attrNode,
                        @Cached GetClassNode getClassNode,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object obj = attrNode.get(exceptionObject, IDX_OBJECT, UNICODE_ERROR_ATTR_FACTORY);
            if (obj == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_ATTRIBUTE_NOT_SET, "object");
            }
            if (!isSubtypeNode.execute(frame, getClassNode.execute(inliningTarget, obj), PythonBuiltinClassType.PBytes)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_ATTRIBUTE_MUST_BE_BYTES, "object");
            }
            return obj;
        }
    }

    // Equivalent of PyUnicodeDecodeError_GetStart
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeDecodeErrorGetStartNode extends Node {

        /**
         * Returns the value of the {@code start} attribute, ensuring it is not out of bounds.
         */
        public abstract int execute(Node inliningTarget, PBaseException exceptionObject);

        @Specialization
        static int doIt(VirtualFrame frame, Node inliningTarget, PBaseException exceptionObject,
                        @Cached PyUnicodeDecodeErrorGetObjectNode getObjectNode,
                        @Cached(inline = false) BaseExceptionAttrNode attrNode,
                        @Cached PyObjectSizeNode sizeNode) {
            Object obj = getObjectNode.execute(inliningTarget, exceptionObject);
            int size = sizeNode.execute(frame, inliningTarget, obj);
            int start = attrNode.getInt(exceptionObject, IDX_START, UNICODE_ERROR_ATTR_FACTORY);
            if (start < 0) {
                start = 0;
            }
            if (start >= size) {
                start = size - 1;
            }
            return start;
        }
    }

    // Equivalent of PyUnicodeDecodeError_GetEnd
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeDecodeErrorGetEndNode extends Node {

        /**
         * Returns the value of the {@code end} attribute, ensuring it is not out of bounds.
         */
        public abstract int execute(Node inliningTarget, PBaseException exceptionObject);

        @Specialization
        static int doIt(VirtualFrame frame, Node inliningTarget, PBaseException exceptionObject,
                        @Cached PyUnicodeDecodeErrorGetObjectNode getObjectNode,
                        @Cached(inline = false) BaseExceptionAttrNode attrNode,
                        @Cached PyObjectSizeNode sizeNode) {
            Object obj = getObjectNode.execute(inliningTarget, exceptionObject);
            int size = sizeNode.execute(frame, inliningTarget, obj);
            int end = attrNode.getInt(exceptionObject, IDX_END, UNICODE_ERROR_ATTR_FACTORY);
            if (end < 1) {
                end = 1;
            }
            if (end > size) {
                end = size;
            }
            return end;
        }
    }

    // Equivalent of PyUnicodeDecodeError_GetEncoding
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeDecodeErrorGetEncodingNode extends Node {

        /**
         * Returns the value of the {@code encoding} attribute, checking that it is present and a
         * subtype of {@code str}.
         */
        public abstract TruffleString execute(Node inliningTarget, PBaseException exceptionObject);

        @Specialization
        static TruffleString doIt(Node inliningTarget, PBaseException exceptionObject,
                        @Cached(inline = false) BaseExceptionAttrNode attrNode,
                        @Cached CastToTruffleStringCheckedNode castToStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object obj = attrNode.get(exceptionObject, IDX_ENCODING, UNICODE_ERROR_ATTR_FACTORY);
            if (obj == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.S_ATTRIBUTE_NOT_SET, "encoding");
            }
            return castToStringNode.cast(inliningTarget, obj, ErrorMessages.S_ATTRIBUTE_MUST_BE_UNICODE, "encoding");
        }
    }
}
