/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import static com.oracle.graal.python.nodes.BuiltinNames.T__WARNINGS;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.SyntaxErrorBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.pegparser.ParserCallbacks;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PIncompleteSourceException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public class ParserCallbacksImpl implements ParserCallbacks {

    private static final TruffleString DEFAULT_FILENAME = tsLiteral("<string>");

    private final Supplier<Source> sourceSupplier;
    private final boolean withJavaStackTrace;
    private List<DeprecationWarning> deprecationWarnings;

    public ParserCallbacksImpl(Supplier<Source> sourceSupplier, boolean withJavaStackTrace) {
        this.sourceSupplier = sourceSupplier;
        this.withJavaStackTrace = withJavaStackTrace;
    }

    public ParserCallbacksImpl(Source source, boolean withJavaStackTrace) {
        this(() -> source, withJavaStackTrace);
    }

    private static class DeprecationWarning {
        final PythonBuiltinClassType type;
        final SourceRange sourceRange;
        final TruffleString message;

        public DeprecationWarning(PythonBuiltinClassType type, SourceRange sourceRange, TruffleString message) {
            this.type = type;
            this.sourceRange = sourceRange;
            this.message = message;
        }
    }

    @Override
    public void safePointPoll() {
        Node node = EncapsulatingNodeReference.getCurrent().get();
        TruffleSafepoint.poll(node);
    }

    @Override
    public RuntimeException reportIncompleteSource(int line) {
        throw new PIncompleteSourceException("", null, line, sourceSupplier.get());
    }

    @Override
    public RuntimeException onError(ErrorType errorType, SourceRange sourceRange, String message) {
        throw raiseSyntaxError(errorType, sourceRange, message);
    }

    public PException raiseSyntaxError(ErrorType errorType, SourceRange sourceRange, String message) {
        throw raiseSyntaxError(errorType, sourceRange, toTruffleStringUncached(message), sourceSupplier.get(), withJavaStackTrace);
    }

    public static PException raiseSyntaxError(ParserCallbacks.ErrorType errorType, SourceRange sourceRange, TruffleString message, Source source, boolean withJavaStackTrace) {
        Node location = new Node() {
            @Override
            public boolean isAdoptable() {
                return false;
            }

            @Override
            public SourceSection getSourceSection() {
                // TODO the parser should probably guarantee correct coordinates to make the
                // following checks unnecessary
                if (sourceRange.startLine > source.getLineCount() || sourceRange.endLine > source.getLineCount()) {
                    // Tokenizer pretends the input ends with a newline, which is not in the source
                    return source.createSection(source.getLength(), 0);
                }
                int startCol = Math.max(sourceRange.startColumn, 0) + 1;
                startCol = Math.min(startCol, source.getLineLength(sourceRange.startLine) + 1);
                int endCol;
                if (sourceRange.endColumn < 0) {
                    if (sourceRange.startLine == sourceRange.endLine) {
                        endCol = startCol;
                    } else {
                        endCol = 1;
                    }
                } else {
                    endCol = sourceRange.endColumn + 1;
                }
                endCol = Math.min(endCol, source.getLineLength(sourceRange.endLine) + 1);
                if (sourceRange.endLine == source.getLineCount() && endCol == source.getLineLength(sourceRange.endLine) + 1) {
                    // Source.createSection does not like it when the end coord points past the
                    // end of the source.
                    if (endCol > 1 && (sourceRange.startLine < sourceRange.endLine || startCol < endCol)) {
                        // Column index must be at least 1.
                        endCol--;
                    } else {
                        // There's no correct line:column coord for the last empty line.
                        return source.createSection(source.getLength(), 0);
                    }
                }
                return source.createSection(sourceRange.startLine, startCol, sourceRange.endLine, endCol);
            }
        };
        PBaseException instance;
        PythonBuiltinClassType cls = PythonBuiltinClassType.SyntaxError;
        switch (errorType) {
            case System:
                cls = PythonBuiltinClassType.SystemError;
                break;
            case Indentation:
                cls = PythonBuiltinClassType.IndentationError;
                break;
            case Tab:
                cls = PythonBuiltinClassType.TabError;
                break;
        }
        instance = PythonObjectFactory.getUncached().createBaseException(cls, message, PythonUtils.EMPTY_OBJECT_ARRAY);
        final Object[] excAttrs = SyntaxErrorBuiltins.SYNTAX_ERROR_ATTR_FACTORY.create();
        TruffleString filename = getFilename(source);
        excAttrs[SyntaxErrorBuiltins.IDX_FILENAME] = filename;
        excAttrs[SyntaxErrorBuiltins.IDX_LINENO] = sourceRange.startLine;
        excAttrs[SyntaxErrorBuiltins.IDX_OFFSET] = sourceRange.startColumn + 1;
        excAttrs[SyntaxErrorBuiltins.IDX_END_LINENO] = sourceRange.endLine == -1 ? PNone.NONE : sourceRange.endLine;
        excAttrs[SyntaxErrorBuiltins.IDX_END_OFFSET] = sourceRange.endColumn == -1 ? PNone.NONE : sourceRange.endColumn + 1;
        // Not very nice. This counts on the implementation in traceback.py where if the value of
        // text attribute is NONE, then the line is not printed
        Object text = PNone.NONE;
        if (sourceRange.startLine <= source.getLineCount()) {
            text = toTruffleStringUncached(source.getCharacters(sourceRange.startLine).toString());
        }
        excAttrs[SyntaxErrorBuiltins.IDX_MSG] = message;
        excAttrs[SyntaxErrorBuiltins.IDX_TEXT] = text;
        instance.setExceptionAttributes(excAttrs);
        throw PException.fromObject(instance, location, withJavaStackTrace);
    }

    private static TruffleString getFilename(Source source) {
        TruffleString filename = toTruffleStringUncached(source.getPath());
        if (filename == null) {
            filename = toTruffleStringUncached(source.getName());
            if (filename == null) {
                filename = DEFAULT_FILENAME;
            }
        }
        return filename;
    }

    @Override
    public void onWarning(WarningType warningType, SourceRange sourceRange, String message) {
        if (deprecationWarnings == null) {
            deprecationWarnings = new ArrayList<>();
        }
        PythonBuiltinClassType type;
        switch (warningType) {
            case Deprecation:
                type = PythonBuiltinClassType.DeprecationWarning;
                break;
            case Syntax:
                type = PythonBuiltinClassType.SyntaxWarning;
                break;
            default:
                throw shouldNotReachHere("Unexpected warning type: " + warningType);
        }
        deprecationWarnings.add(new DeprecationWarning(type, sourceRange, toTruffleStringUncached(message)));
    }

    public void triggerDeprecationWarnings() {
        if (deprecationWarnings != null) {
            triggerDeprecationWarningsBoundary();
        }
    }

    public void triggerAndClearDeprecationWarnings() {
        if (deprecationWarnings != null) {
            triggerDeprecationWarningsBoundary();
            deprecationWarnings.clear();
        }
    }

    @TruffleBoundary
    private void triggerDeprecationWarningsBoundary() {
        PythonModule warnings = PythonContext.get(null).lookupBuiltinModule(T__WARNINGS);
        Source source = sourceSupplier.get();
        for (DeprecationWarning warning : deprecationWarnings) {
            try {
                PyObjectCallMethodObjArgs.executeUncached(warnings, WarningsModuleBuiltins.T_WARN_EXPLICIT, //
                                warning.message, warning.type, getFilename(source), warning.sourceRange.startLine);
            } catch (PException e) {
                e.expect(null, warning.type, IsBuiltinObjectProfile.getUncached());
                /*
                 * Replace the DeprecationWarning exception with a SyntaxError to get a more
                 * accurate error report
                 */
                throw raiseSyntaxError(ErrorType.Syntax, warning.sourceRange, warning.message, source, withJavaStackTrace);
            }
        }
    }
}
