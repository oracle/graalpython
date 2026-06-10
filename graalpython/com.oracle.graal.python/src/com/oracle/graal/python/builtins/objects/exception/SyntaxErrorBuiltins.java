/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_PARENTHESES_IN_CALL_TO_EXEC;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_PARENTHESES_IN_CALL_TO_PRINT;
import static com.oracle.graal.python.nodes.ErrorMessages.TUPLE_OUT_OF_BOUNDS;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.SyntaxError, PythonBuiltinClassType.IndentationError, PythonBuiltinClassType.TabError})
public final class SyntaxErrorBuiltins extends PythonBuiltins {

    public static final int IDX_MSG = 0;
    public static final int IDX_FILENAME = 1;
    public static final int IDX_LINENO = 2;
    public static final int IDX_OFFSET = 3;
    public static final int IDX_TEXT = 4;
    public static final int IDX_END_LINENO = 5;
    public static final int IDX_END_OFFSET = 6;
    public static final int IDX_PRINT_FILE_AND_LINE = 7;
    public static final int SYNTAX_ERR_NUM_ATTRS = IDX_PRINT_FILE_AND_LINE + 1;

    public static final BaseExceptionAttrNode.StorageFactory SYNTAX_ERROR_ATTR_FACTORY = (args) -> new Object[SYNTAX_ERR_NUM_ATTRS];

    private static final CFields[] NATIVE_ATTR_FIELDS = {
                    CFields.PySyntaxErrorObject__msg,
                    CFields.PySyntaxErrorObject__filename,
                    CFields.PySyntaxErrorObject__lineno,
                    CFields.PySyntaxErrorObject__offset,
                    CFields.PySyntaxErrorObject__text,
                    CFields.PySyntaxErrorObject__end_lineno,
                    CFields.PySyntaxErrorObject__end_offset,
                    CFields.PySyntaxErrorObject__print_file_and_line};

    public static final TpSlots SLOTS = SyntaxErrorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SyntaxErrorBuiltinsFactory.getFactories();
    }

    private static Object getNativeAttr(PythonAbstractNativeObject self, int index, CStructAccess.ReadObjectNode readObjectNode) {
        Object result = readObjectNode.readFromObj(self, NATIVE_ATTR_FIELDS[index]);
        return result == PNone.NO_VALUE ? PNone.NONE : result;
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    @SuppressWarnings({"truffle-sharing", "truffle-unused"})
    public abstract static class SyntaxErrorInitNode extends PythonBuiltinNode {
        private static final String PREFIX_PRINT = "print ";
        private static final String PREFIX_EXEC = "exec ";
        private static final char CHR_LEFTPAREN = '(';
        private static final char CHR_COLON = ':';
        private static final char CHR_COMMA = ',';
        private static final char CHR_SEMICOLON = ';';

        @TruffleBoundary
        private static String getLegacyPrintStatementMsg(String text) {
            int endPos = text.indexOf(CHR_SEMICOLON);
            if (endPos == -1) {
                endPos = text.length();
            }

            String data = text.substring(PREFIX_PRINT.length(), endPos).trim();
            // gets the modified text_len after stripping `print `
            final int textLen = data.length();
            String maybeEndArg = "";
            if (textLen > 0 && data.charAt(textLen - 1) == CHR_COMMA) {
                maybeEndArg = " end=\" \"";
            }
            return String.format(MISSING_PARENTHESES_IN_CALL_TO_PRINT.toJavaStringUncached(), data, maybeEndArg);
        }

        @TruffleBoundary
        private static Object checkForLegacyStatements(String text, int start) {
            // Ignore leading whitespace
            final String trimmedText = trimLeft(text, start);
            // Checking against an empty or whitespace-only part of the string
            if (trimmedText.isEmpty()) {
                return null;
            }

            // Check for legacy print statements
            if (text.startsWith(PREFIX_PRINT)) {
                return getLegacyPrintStatementMsg(trimmedText);
            }

            // Check for legacy exec statements
            if (text.startsWith(PREFIX_EXEC)) {
                return MISSING_PARENTHESES_IN_CALL_TO_EXEC;
            }
            return null;
        }

        @TruffleBoundary
        private static Object reportMissingParentheses(Object msg, String text) {
            // Skip entirely if there is an opening parenthesis
            final int leftParenIndex = text.indexOf(CHR_LEFTPAREN);
            if (leftParenIndex != -1) {
                // Use default error message for any line with an opening paren
                return msg;
            }

            // Handle the simple statement case
            Object rv = checkForLegacyStatements(text, 0);
            if (rv == null) {
                // Handle the one-line complex statement case
                final int colonIndex = text.indexOf(CHR_COLON);
                if (colonIndex >= 0 && colonIndex < text.length()) {
                    // Check again, starting from just after the colon
                    rv = checkForLegacyStatements(text, colonIndex + 1);
                }
            }

            return (rv != null) ? rv : msg;
        }

        @TruffleBoundary(allowInlining = true)
        private static String trimLeft(String str, int start) {
            int len = str.length();
            int st = start;
            while (st < len && str.charAt(st) <= ' ') {
                st++;
            }
            return str.substring(st);
        }

        private static Object[] initAttrs(VirtualFrame frame, Object[] args, Node inliningTarget,
                        CastToJavaStringNode castToJavaStringNode,
                        TupleNodes.ConstructTupleNode constructTupleNode,
                        SequenceStorageNodes.GetItemNode getItemNode,
                        PRaiseNode raiseNode) {
            Object[] attrs = SYNTAX_ERROR_ATTR_FACTORY.create();
            if (args.length >= 1) {
                attrs[IDX_MSG] = args[0];
            }
            if (args.length == 2) {
                PTuple info = constructTupleNode.execute(frame, args[1]);
                final SequenceStorage storage = info.getSequenceStorage();
                int infoLength = storage.length();
                if (infoLength != 4 && infoLength != 6) {
                    // not a very good error message, but it's what Python 2.4 gives
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, TUPLE_OUT_OF_BOUNDS);
                }

                attrs[IDX_FILENAME] = getItemNode.execute(storage, 0);
                attrs[IDX_LINENO] = getItemNode.execute(storage, 1);
                attrs[IDX_OFFSET] = getItemNode.execute(storage, 2);
                attrs[IDX_TEXT] = getItemNode.execute(storage, 3);
                if (infoLength == 6) {
                    attrs[IDX_END_LINENO] = getItemNode.execute(storage, 4);
                    attrs[IDX_END_OFFSET] = getItemNode.execute(storage, 5);
                }

                // Issue #21669: Custom error for 'print' & 'exec' as statements
                // Only applies to SyntaxError instances, not to subclasses such
                // as TabError or IndentationError (see issue #31161)
                if (PGuards.isString(attrs[IDX_TEXT])) {
                    attrs[IDX_MSG] = reportMissingParentheses(attrs[IDX_MSG], castToJavaStringNode.execute(attrs[IDX_TEXT]));
                }
            }
            return attrs;
        }

        @Specialization
        static Object init(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode,
                        @Cached PRaiseNode raiseNode) {
            baseExceptionInitNode.execute(frame, self, args, keywords);
            self.setExceptionAttributes(initAttrs(frame, args, inliningTarget, castToJavaStringNode, constructTupleNode, getItemNode, raiseNode));
            return PNone.NONE;
        }

        @Specialization
        static Object initNative(VirtualFrame frame, PythonAbstractNativeObject self, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNode) {
            baseExceptionInitNode.execute(frame, self, args, keywords);
            Object[] attrs = initAttrs(frame, args, inliningTarget, castToJavaStringNode, constructTupleNode, getItemNode, raiseNode);
            for (int i = 0; i < SYNTAX_ERR_NUM_ATTRS; i++) {
                Object attr = attrs[i];
                writeObjectNode.writeToObject(self, NATIVE_ATTR_FIELDS[i], attr == null ? PNone.NO_VALUE : attr);
            }
            return PNone.NONE;
        }
    }

    @GenerateCached(false) // this avoids truffle generating a concrete subclass with a missing getIndex()
    @SuppressWarnings({"truffle-static-method", "truffle-unused"})
    public abstract static class SyntaxErrorAttributeNode extends PythonBuiltinNode {
        abstract int getIndex();

        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, getIndex(), SYNTAX_ERROR_ATTR_FACTORY);
        }

        @Specialization(guards = "isNoValue(value)")
        Object getNative(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached(inline = false) CStructAccess.ReadObjectNode readObjectNode) {
            return getNativeAttr(self, getIndex(), readObjectNode);
        }

        @Specialization(guards = "!isNoValue(valueIn)")
        Object setNative(PythonAbstractNativeObject self, Object valueIn,
                        @Bind Node inliningTarget,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNode) {
            Object value = PGuards.isDeleteMarker(valueIn) ? PNone.NO_VALUE : valueIn;
            writeObjectNode.writeToObject(self, NATIVE_ATTR_FIELDS[getIndex()], value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "msg", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception msg")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorMsgNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_MSG;
        }
    }

    @Builtin(name = "filename", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception filename")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorFilenameNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_FILENAME;
        }
    }

    @Builtin(name = "lineno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception lineno")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorLinenoNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_LINENO;
        }
    }

    @Builtin(name = "offset", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception offset")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorOffsetNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_OFFSET;
        }
    }

    @Builtin(name = "text", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception text")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorTextNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_TEXT;
        }
    }

    @Builtin(name = "end_lineno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception end lineno")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorEndLineNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_END_LINENO;
        }
    }

    @Builtin(name = "end_offset", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception end offset")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorEndColumnNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_END_OFFSET;
        }
    }

    @Builtin(name = "print_file_and_line", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception print_file_and_line")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorPrintFileAndLineNode extends SyntaxErrorAttributeNode {
        @Override
        int getIndex() {
            return IDX_PRINT_FILE_AND_LINE;
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    @SuppressWarnings({"truffle-sharing", "truffle-static-method", "truffle-unused"})
    public abstract static class SyntaxErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString str(VirtualFrame frame, PBaseException self,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode) {
            return formatStr(attrNode.get(self, IDX_FILENAME, SYNTAX_ERROR_ATTR_FACTORY), attrNode.get(self, IDX_LINENO, SYNTAX_ERROR_ATTR_FACTORY), attrNode.get(self, IDX_MSG,
                            SYNTAX_ERROR_ATTR_FACTORY));
        }

        @Specialization
        TruffleString strNative(VirtualFrame frame, PythonAbstractNativeObject self,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            return formatStr(getNativeAttr(self, IDX_FILENAME, readObjectNode), getNativeAttr(self, IDX_LINENO, readObjectNode), getNativeAttr(self, IDX_MSG, readObjectNode));
        }

        @TruffleBoundary
        private TruffleString formatStr(Object filenameAttrValue, Object lineno, Object msg) {
            // Below, we always ignore overflow errors, just printing -1.
            // Still, we cannot allow an OverflowError to be raised, so
            // we need to call PyLong_AsLongAndOverflow.
            String filename;
            if (filenameAttrValue != PNone.NONE && PGuards.isString(filenameAttrValue)) {
                filename = CastToJavaStringNode.getUncached().execute(filenameAttrValue);
                filename = getLastPathElement(filename);
            } else {
                filename = null;
            }

            boolean heaveLineNo = lineno != PNone.NONE && PyLongCheckExactNode.executeUncached(lineno);
            final TruffleString msgStr = PyObjectStrAsTruffleStringNode.executeUncached(msg);

            if (filename == null && !heaveLineNo) {
                return msgStr;
            }

            TruffleString result;
            if (filename != null && heaveLineNo) {
                long ln;
                try {
                    ln = PyLongAsLongAndOverflowNode.executeUncached(lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = SimpleTruffleStringFormatNode.getUncached().format("%s (%s, line %d)", msgStr, toTruffleStringUncached(filename), ln);
            } else if (filename != null) {
                result = SimpleTruffleStringFormatNode.getUncached().format("%s (%s)", msgStr, toTruffleStringUncached(filename));
            } else {
                // only have_lineno
                long ln;
                try {
                    ln = PyLongAsLongAndOverflowNode.executeUncached(lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = SimpleTruffleStringFormatNode.getUncached().format("%s (line %d)", msgStr, ln);
            }
            return result;
        }

        String getLastPathElement(String path) {
            int sepIdx = path.lastIndexOf(getContext().getEnv().getFileNameSeparator());
            sepIdx = Math.max(sepIdx, path.lastIndexOf('/'));
            if (sepIdx < 0) {
                return path;
            }
            return path.substring(sepIdx + 1);
        }

    }
}
