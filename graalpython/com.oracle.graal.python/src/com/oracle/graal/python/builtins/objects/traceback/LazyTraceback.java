package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.runtime.exception.PException;

public class LazyTraceback {
    private final PFrame.Reference frameInfo;
    private final PFrame frame;
    private final PException exception;
    private final LazyTraceback nextChain;
    private PTraceback traceback;
    private boolean materialized;

    public LazyTraceback(PFrame.Reference frameInfo, PException exception, LazyTraceback nextChain) {
        this.frame = null;
        this.frameInfo = frameInfo;
        this.exception = exception;
        this.nextChain = nextChain;
        this.materialized = false;
    }

    public LazyTraceback(PFrame frame, PException exception, LazyTraceback nextChain) {
        this.frame = frame;
        this.frameInfo = null;
        this.exception = exception;
        this.nextChain = nextChain;
        this.materialized = false;
    }

    public LazyTraceback(PTraceback traceback) {
        this.traceback = traceback;
        this.frameInfo = null;
        this.frame = null;
        this.nextChain = null;
        this.exception = null;
        this.materialized = true;
    }

    public PFrame.Reference getFrameInfo() {
        return frameInfo;
    }

    public PFrame getFrame() {
        return frame;
    }

    public PException getException() {
        return exception;
    }

    public LazyTraceback getNextChain() {
        return nextChain;
    }

    public PTraceback getTraceback() {
        return traceback;
    }

    public void setTraceback(PTraceback traceback) {
        this.traceback = traceback;
        this.materialized = true;
    }

    public boolean isMaterialized() {
        return materialized;
    }
}
