package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.traceback.PTraceback;

public class ExceptionInfo {
    public final PBaseException exception;
    public final PTraceback traceback;

    public static final ExceptionInfo NO_EXCEPTION = new ExceptionInfo(null, null);

    public ExceptionInfo(PBaseException exception, PTraceback traceback) {
        this.exception = exception;
        this.traceback = traceback;
    }
}
