package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

@GenerateUncached
public abstract class GetTracebackNode extends Node {
    public abstract PTraceback execute(LazyTraceback tb);

    @Specialization(guards = "tb.isMaterialized()")
    PTraceback getMaterialized(LazyTraceback tb) {
        return tb.getTraceback();
    }

    @Specialization(guards = "!tb.isMaterialized()")
    PTraceback getLazy(LazyTraceback tb,
                    @Cached PythonObjectFactory factory) {
        int lineno = -2;
        LazyTraceback prev = tb.getNextChain();
        CallTarget currentCallTarget = Truffle.getRuntime().getCurrentFrame().getCallTarget();
        for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(tb.getException())) {
            if (currentCallTarget != null && element.getTarget() == currentCallTarget) {
                if (element.getLocation() != null) {
                    SourceSection sourceSection = element.getLocation().getEncapsulatingSourceSection();
                    if (sourceSection != null) {
                        lineno = sourceSection.getStartLine();
                    }
                }
                break;
            }
            LazyTraceback prevTraceback = prev;
            Frame tracebackFrame = element.getFrame();
            // frames may have not been requested
            if (tracebackFrame != null) {
                Node location = element.getLocation();
                // only include frames of non-builtin python functions
                if (PArguments.isPythonFrame(tracebackFrame) && location != null && !location.getRootNode().isInternal()) {
                    // The Truffle frame should be already materialized
                    prevTraceback = new LazyTraceback(factory.createTraceback(tracebackFrame.materialize(), location, prevTraceback));
                }
            }
            prev = prevTraceback;
        }
        PTraceback materializedTraceback;
        if (tb.getFrame() != null) {
            materializedTraceback = factory.createTraceback(tb.getFrame(), lineno, prev);
        } else if (tb.getFrameInfo() != null) {
            materializedTraceback = factory.createTraceback(tb.getFrameInfo(), lineno, prev);
        } else if (prev != null) {
            materializedTraceback = execute(prev);
        } else {
            materializedTraceback = null;
        }
        tb.setTraceback(materializedTraceback);
        return materializedTraceback;
    }
}
