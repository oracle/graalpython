package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

public abstract class ChainExceptionsNode extends Node {
    public abstract void execute(PException currentException, PException contextException);

    @Specialization
    public static void chainExceptions(PException currentException, PException contextException,
                    @Bind("this") Node inliningTarget,
                    @Cached ExceptionNodes.GetContextNode getContextNode,
                    @Cached ExceptionNodes.SetContextNode setContextNode,
                    @Cached InlinedLoopConditionProfile p1,
                    @Cached InlinedLoopConditionProfile p2) {
        Object current = currentException.getUnreifiedException();
        Object context = contextException.getUnreifiedException();
        if (current != context) {
            Object e = current;
            while (p1.profile(inliningTarget, e != PNone.NONE)) {
                Object eContext = getContextNode.execute(inliningTarget, e);
                if (eContext == context) {
                    // We have already chained this exception in an inner block, do nothing
                    return;
                }
                e = eContext;
            }
            e = context;
            while (p2.profile(inliningTarget, e != PNone.NONE)) {
                Object eContext = getContextNode.execute(inliningTarget, e);
                if (eContext == current) {
                    setContextNode.execute(inliningTarget, e, PNone.NONE);
                }
                e = eContext;
            }
            contextException.markEscaped();
            setContextNode.execute(inliningTarget, current, context);
        }
    }

    @NeverDefault
    public static ChainExceptionsNode create() {
        return ChainExceptionsNodeGen.create();
    }
}
