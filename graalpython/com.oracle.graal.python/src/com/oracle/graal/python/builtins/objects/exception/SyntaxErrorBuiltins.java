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

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SyntaxErrorBuiltinsFactory.getFactories();
    }

    @CompilerDirectives.ValueType
    public static final class SyntaxErrorData extends PBaseException.Data {
        private Object msg;
        private Object filename;
        private Object lineno;
        private Object offset;
        private Object text;
        private Object printFileAndLine;

        private SyntaxErrorData() {

        }

        public Object getMsg() {
            return msg;
        }

        public void setMsg(Object msg) {
            this.msg = msg;
        }

        public Object getFilename() {
            return filename;
        }

        public void setFilename(Object filename) {
            this.filename = filename;
        }

        public Object getLineno() {
            return lineno;
        }

        public void setLineno(Object lineno) {
            this.lineno = lineno;
        }

        public Object getOffset() {
            return offset;
        }

        public void setOffset(Object offset) {
            this.offset = offset;
        }

        public Object getText() {
            return text;
        }

        public void setText(Object text) {
            this.text = text;
        }

        public Object getPrintFileAndLine() {
            return printFileAndLine;
        }

        public void setPrintFileAndLine(Object printFileAndLine) {
            this.printFileAndLine = printFileAndLine;
        }

        public static SyntaxErrorData create(Object msg, Object filename, Object lineno, Object offset, Object text) {
            return create(msg, filename, lineno, offset, text, null);
        }

        public static SyntaxErrorData create(Object msg, Object filename, Object lineno, Object offset, Object text, Object printFileAndLine) {
            final SyntaxErrorData data = new SyntaxErrorData();
            data.setMsg(msg);
            data.setFilename(filename);
            data.setLineno(lineno);
            data.setOffset(offset);
            data.setText(text);
            data.setPrintFileAndLine(printFileAndLine);
            return data;
        }
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
            self.setData(SyntaxErrorData.create(msg, filename, lineno, offset, text, printFileAndLine));
            return PNone.NONE;
        }
    }

    abstract static class SyntaxErrorBaseAttrNode extends PythonBuiltinNode {
        protected Object get(SyntaxErrorData data) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        protected void set(SyntaxErrorData data, Object value) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "isNoValue(none)")
        public Object get(PBaseException self, @SuppressWarnings("unused") PNone none) {
            final Object data = self.getData();
            assert data instanceof SyntaxErrorData;
            final Object value = get((SyntaxErrorData) data);
            return value != null ? value : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        public Object set(PBaseException self, Object value) {
            final Object data = self.getData();
            assert data instanceof SyntaxErrorData;
            set((SyntaxErrorData) data, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = "msg", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception msg")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorMsgNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getMsg();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setMsg(value);
        }
    }

    @Builtin(name = "filename", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception filename")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorFilenameNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getFilename();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setFilename(value);
        }
    }

    @Builtin(name = "lineno", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception lineno")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorLinenoNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getLineno();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setLineno(value);
        }
    }

    @Builtin(name = "offset", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception offset")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorOffsetNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getOffset();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setOffset(value);
        }
    }

    @Builtin(name = "text", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception text")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorTextNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getText();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setText(value);
        }
    }

    @Builtin(name = "print_file_and_line", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception print_file_and_line")
    @GenerateNodeFactory
    public abstract static class SyntaxErrorPrintFileAndLineNode extends SyntaxErrorBaseAttrNode {
        @Override
        protected Object get(SyntaxErrorData data) {
            return data.getPrintFileAndLine();
        }

        @Override
        protected void set(SyntaxErrorData data, Object value) {
            data.setPrintFileAndLine(value);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class SyntaxErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Cached PyObjectStrAsJavaStringNode strNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyLongAsLongAndOverflowNode pyLongAsLongAndOverflowNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode) {
            assert self.getData() instanceof SyntaxErrorData;
            final SyntaxErrorData data = (SyntaxErrorData) self.getData();
            // Below, we always ignore overflow errors, just printing -1.
            // Still, we cannot allow an OverflowError to be raised, so
            // we need to call PyLong_AsLongAndOverflow.
            String filename;
            if (data.getFilename() != null && PGuards.isString(data.getFilename())) {
                filename = castToJavaStringNode.execute(data.getFilename());
                final int sepIdx = PythonUtils.lastIndexOf(filename, getContext().getEnv().getFileNameSeparator());
                filename = (sepIdx != -1) ? PythonUtils.substring(filename, sepIdx + 1) : filename;
            } else {
                filename = null;
            }

            final Object lineno = data.getLineno();
            final Object msg = data.getMsg();
            boolean heaveLineNo = lineno != null && pyLongCheckExactNode.execute(lineno);

            if (filename == null && !heaveLineNo) {
                return strNode.execute(frame, msg != null ? msg : PNone.NONE);
            }

            String result;
            if (filename != null && heaveLineNo) {
                long ln;
                try {
                    ln = pyLongAsLongAndOverflowNode.execute(frame, lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = PythonUtils.format("%s (%s, line %d)", msg != null ? msg : PNone.NONE, filename, ln);
            } else if (filename != null) {
                result = PythonUtils.format("%s (%s)", msg != null ? msg : PNone.NONE, filename);
            } else {
                // only have_lineno
                long ln;
                try {
                    ln = pyLongAsLongAndOverflowNode.execute(frame, lineno);
                } catch (OverflowException e) {
                    ln = -1;
                }
                result = PythonUtils.format("%s (line %d)", msg != null ? msg : PNone.NONE, ln);
            }
            return result;
        }
    }
}
