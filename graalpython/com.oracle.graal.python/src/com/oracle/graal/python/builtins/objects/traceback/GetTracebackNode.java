package com.oracle.graal.python.builtins.objects.traceback;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <strong>Summary of our implementation of traceback handling</strong>
 *
 * <p>
 * When a {@link com.oracle.graal.python.runtime.exception.PException} is thrown, Truffle collects
 * every frame that the exception passes through until its caught. Then, when asked for a traceback,
 * it provides a sequence of stack trace element objects that capture frames from the root node
 * where the exception was thrown up to the top level root node. This stack trace is created from
 * the frames captured during the unwinding and then the frames that are currently on stack, i.e. it
 * expects to be called in an exception handler, otherwise the stacktrace is incorrect - it would
 * contain frames from the place where you asked for the stacktrace not the place where the
 * exception occured. Additionally, the stacktrace is frozen on the first access and from then on,
 * Truffle always returns its cached copy.
 * </p>
 * <p>
 * Python, on the other hand, builds the traceback incrementally. Firstly, it only includes the
 * frames that the exception has passed through during the unwinding plus the frame where it was
 * caught. It doesn't include the frames above it (to the top). Secondly, the traceback is never
 * frozen. The traceback accumulates more frames when the exception gets reraised. To correct the
 * mismatch between Truffle and Python eception handling, we need to wrap {@link PException}s in
 * {@link LazyTraceback} objects when caught and adhere to rules of exception handling mentioned
 * below.
 * </p>
 *
 * <p>
 * {@link LazyTraceback} represents a (possibly empty) traceback segment. It consists of an optional
 * Python frame or frame reference and a {@link PException} which serves as a carrier of the Truffle
 * stack trace. {@link LazyTraceback} forms a linked list that gets prepended a new
 * {@link LazyTraceback} each time the python exception gets reraised, either explicitly (raise
 * statement) or implicitly (for example at the end of finally). Each of these segments needs to
 * have their own distinct {@link PException} to avoid interference, therefore a caught
 * {@link PException} must never be rethrown after being added to the traceback and it must never be
 * added to the traceback multiple times.
 * </p>
 *
 * <p>
 * The whole chain of {@link LazyTraceback} objects can be materialized into a linked list
 * PTraceback objects. Due to all the parts of a segment being optional, it can also materialize to
 * nothing (null/None). The materialization is lazy and is split between {@link GetTracebackNode}
 * and {@link TracebackBuiltins}.
 * </p>
 *
 * <p>
 * Rules for exception handling:
 * <ul>
 * <li>When you catch a {@link PException PException} and need to obtain its corresponding
 * {@link com.oracle.graal.python.builtins.objects.exception.PBaseException PBaseException}, use the
 * {@link PException#reifyAndGetPythonException(VirtualFrame) reifyAndGetPythonException} method,
 * unless you're just doing a simple class check. Try to avoid the
 * {@link PException#getExceptionObject() getExceptionObject} method unless you know what you're
 * doing.</li>
 * <li>{@link PException PException} must never be rethrown after it has been possibly exposed to
 * the program, because its Truffle stacktrace may already be frozen and it would not capture more
 * frames. If you need to rethrow without the catching site appearing in the traceback, use
 * {@link com.oracle.graal.python.builtins.objects.exception.PBaseException#getExceptionForReraise(LazyTraceback)
 * PBaseException.getExceptionForReraise} method to obtain a fresh {@link PException PException} to
 * throw</li>
 * </ul>
 * </p>
 */
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
        /*
         * Truffle stacktrace consists of the frames captured during the unwinding and the frames
         * that are now on the Java stack. We don't want the frames from the stack to creep in. They
         * would be incorrect because we are no longer in the location where the exception was
         * caught, and unwanted because Python doesn't include frames from the active stack in the
         * traceback. But we still need to peek at the stacktrace element for the current frame,
         * even though the frame is incorrect (see below why). Truffle doesn't tell us where is the
         * boundary between exception frames and frames from the Java stack, so we cut it off when
         * we see the current call target in the stacktrace.
         *
         * For the top frame of a traceback, we need to know the location of where the exception
         * occured in the "try" block. We cannot get it from the frame we capture because it already
         * "moved" to the "except" block. When unwinding, Truffle captures the frame and location of
         * each exiting call. When constructing the stacktrace, the location is "moved up". The
         * element with the bottom frame gets the location from the exception and the other elements
         * get the location from the frame of the element directly below them. Therefore, the
         * element for the current frame, even though the frame itself doesn't belong to the
         * traceback, contains the desired location from which we can get the lineno.
         */
        int lineno = -2;
        LazyTraceback prev = tb.getNextChain();
        CallTarget currentCallTarget = Truffle.getRuntime().getCurrentFrame().getCallTarget();
        boolean skipFirst = tb.getException().shouldHideLocation();
        for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(tb.getException())) {
            if (skipFirst) {
                skipFirst = false;
                continue;
            }
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

    public static GetTracebackNode create() {
        return GetTracebackNodeGen.create();
    }
}
