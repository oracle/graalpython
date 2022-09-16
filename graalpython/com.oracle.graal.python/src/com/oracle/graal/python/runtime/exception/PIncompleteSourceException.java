package com.oracle.graal.python.runtime.exception;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Runtime exception used to indicate incomplete source code during parsing.
 */
@ExportLibrary(InteropLibrary.class)
public class PIncompleteSourceException extends AbstractTruffleException {

    private static final long serialVersionUID = 4393080397807767467L;

    private Source source;
    private final int line;

    public PIncompleteSourceException(String message, Throwable cause, int line, Source source) {
        super(message, cause, UNLIMITED_STACK_TRACE, null);
        this.line = line;
        this.source = source;
    }

    public PIncompleteSourceException(String message, Throwable cause, int line) {
        this(message, cause, line, null);
    }

    public void setSource(Source source) {
        this.source = source;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException() {
        throw this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    ExceptionType getExceptionType() {
        return ExceptionType.PARSE_ERROR;
    }

    @ExportMessage
    boolean isExceptionIncompleteSource() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExceptionMessage() {
        return true;
    }

    @ExportMessage
    String getExceptionMessage() {
        return getMessage();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasSourceLocation() {
        return true;
    }

    @ExportMessage(name = "getSourceLocation")
    @TruffleBoundary
    SourceSection getExceptionSourceLocation() {
        if (line > 0 && line < source.getLineCount()) {
            return source.createSection(line);
        }
        return source.createUnavailableSection();
    }
}
