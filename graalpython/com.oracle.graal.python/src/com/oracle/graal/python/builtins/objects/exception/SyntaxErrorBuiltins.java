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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_PARENTHESES_IN_CALL_TO_EXEC;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_PARENTHESES_IN_CALL_TO_PRINT;
import static com.oracle.graal.python.nodes.ErrorMessages.TUPLE_OUT_OF_BOUNDS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.SyntaxError, PythonBuiltinClassType.IndentationError, PythonBuiltinClassType.TabError})
public final class SyntaxErrorBuiltins extends PythonBuiltins {

    public static final int IDX_MSG = 0;
    public static final int IDX_FILENAME = 1;
    public static final int IDX_LINENO = 2;
    public static final int IDX_OFFSET = 3;
    public static final int IDX_TEXT = 4;
    public static final int IDX_PRINT_FILE_AND_LINE = 5;
    public static final int SYNTAX_ERR_NUM_ATTRS = IDX_PRINT_FILE_AND_LINE + 1;

    public static final BaseExceptionAttrNode.StorageFactory SYNTAX_ERROR_ATTR_FACTORY = (args, factory) -> new Object[SYNTAX_ERR_NUM_ATTRS];

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SyntaxErrorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class SyntaxErrorInitNode extends PythonBuiltinNode {
        private static final String PREFIX_PRINT = "print ";
        private static final String PREFIX_EXEC = "exec ";
        private static final char CHR_LEFTPAREN = '(';
        private static final char CHR_COLON = ':';
        private static final char CHR_COMMA = ',';
        private static final char CHR_SEMICOLON = ';';

        @CompilerDirectives.TruffleBoundary
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
            return String.format(MISSING_PARENTHESES_IN_CALL_TO_PRINT, data, maybeEndArg);
        }

        @CompilerDirectives.TruffleBoundary
        private static Object checkForLegacyStatements(String text, int start) {
            // Ignore leading whitespace
            final String trimmedText = PythonUtils.trimLeft(PythonUtils.substring(text, start));
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

        @CompilerDirectives.TruffleBoundary
        private static Object reportMissingParentheses(Object msg, String text) {
            // Skip entirely if there is an opening parenthesis
            final int leftParenIndex = PythonUtils.indexOf(text, CHR_LEFTPAREN);
            if (leftParenIndex != -1) {
                // Use default error message for any line with an opening paren
                return msg;
            }

            // Handle the simple statement case
            Object rv = checkForLegacyStatements(text, 0);
            if (rv == null) {
                // Handle the one-line complex statement case
                final int colonIndex = PythonUtils.indexOf(text, CHR_COLON);
                if (colonIndex >= 0 && colonIndex < text.length()) {
                    // Check again, starting from just after the colon
                    rv = checkForLegacyStatements(text, colonIndex + 1);
                }
            }

            return (rv != null) ? rv : msg;
        }

        @Specialization
        Object init(VirtualFrame frame, PBaseException self, Object[] args,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode) {
            baseExceptionInitNode.execute(self, args);
            Object msg = null;
            Object filename = null;
            Object lineno = null;
            Object offset = null;
            Object text = null;
            Object printFileAndLine = null;
            if (args.length >= 1) {
                msg = args[0];
            }
            if (args.length == 2) {
                PTuple info = constructTupleNode.execute(frame, args[1]);
                final SequenceStorage storage = info.getSequenceStorage();
                if (lenNode.execute(storage) != 4) {
                    // not a very good error message, but it's what Python 2.4 gives
                    throw raise(PythonBuiltinClassType.IndexError, TUPLE_OUT_OF_BOUNDS);
                }

                filename = getItemNode.execute(frame, storage, 0);
                lineno = getItemNode.execute(frame, storage, 1);
                offset = getItemNode.execute(frame, storage, 2);
                text = getItemNode.execute(frame, storage, 3);

                // Issue #21669: Custom error for 'print' & 'exec' as statements
                // Only applies to SyntaxError instances, not to subclasses such
                // as TabError or IndentationError (see issue #31161)
                if (PGuards.isString(text)) {
                    msg = reportMissingParentheses(msg, castToJavaStringNode.execute(text));
                }
            }
            self.setExceptionAttributes(new Object[]{msg, filename, lineno, offset, text, printFileAndLine});
            return PNone.NONE;
        }
    }

    @Builtin(name = "msg", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception msg")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorMsgNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_MSG, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "filename", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception filename")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorFilenameNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_FILENAME, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "lineno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception lineno")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorLinenoNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_LINENO, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "offset", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception offset")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorOffsetNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_OFFSET, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "text", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception text")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorTextNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_TEXT, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "print_file_and_line", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception print_file_and_line")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorPrintFileAndLineNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_PRINT_FILE_AND_LINE, SYNTAX_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SyntaxErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached PyObjectStrAsJavaStringNode strNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyLongAsLongAndOverflowNode pyLongAsLongAndOverflowNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode) {
            // Below, we always ignore overflow errors, just printing -1.
            // Still, we cannot allow an OverflowError to be raised, so
            // we need to call PyLong_AsLongAndOverflow.
            String filename;
            final Object filenameAttrValue = attrNode.get(self, IDX_FILENAME, SYNTAX_ERROR_ATTR_FACTORY);
            if (filenameAttrValue != PNone.NONE && PGuards.isString(filenameAttrValue)) {
                filename = castToJavaStringNode.execute(self.getExceptionAttribute(IDX_FILENAME));
                final int sepIdx = PythonUtils.lastIndexOf(filename, getContext().getEnv().getFileNameSeparator());
                filename = (sepIdx != -1) ? PythonUtils.substring(filename, sepIdx + 1) : filename;
            } else {
                filename = null;
            }

            final Object lineno = attrNode.get(self, IDX_LINENO, SYNTAX_ERROR_ATTR_FACTORY);
            final Object msg = attrNode.get(self, IDX_MSG, SYNTAX_ERROR_ATTR_FACTORY);
            boolean heaveLineNo = lineno != PNone.NONE && pyLongCheckExactNode.execute(lineno);

            if (filename == null && !heaveLineNo) {
                return strNode.execute(frame, msg);
            }

            String result;
            if (filename != null && heaveLineNo) {
                long ln;
                try {
                    ln = pyLongAsLongAndOverflowNode.execute(frame, lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = PythonUtils.format("%s (%s, line %d)", msg, filename, ln);
            } else if (filename != null) {
                result = PythonUtils.format("%s (%s)", msg, filename);
            } else {
                // only have_lineno
                long ln;
                try {
                    ln = pyLongAsLongAndOverflowNode.execute(frame, lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = PythonUtils.format("%s (line %d)", msg, ln);
            }
            return result;
        }
    }
}
