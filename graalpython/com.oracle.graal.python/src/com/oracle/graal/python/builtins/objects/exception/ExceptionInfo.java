package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;

public class ExceptionInfo {
    public final PBaseException exception;
    public final LazyTraceback traceback;

    public static final ExceptionInfo NO_EXCEPTION = new ExceptionInfo(null, (LazyTraceback) null);

    public ExceptionInfo(PBaseException exception, LazyTraceback traceback) {
        this.exception = exception;
        this.traceback = traceback;
    }

    public ExceptionInfo(PBaseException exception, PTraceback traceback) {
        this.exception = exception;
        this.traceback = new LazyTraceback(traceback);
    }
}
