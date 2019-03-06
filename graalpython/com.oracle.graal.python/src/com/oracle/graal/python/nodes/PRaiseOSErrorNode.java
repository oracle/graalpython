package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PGuards.class)
public abstract class PRaiseOSErrorNode extends Node {

    public abstract PException execute(VirtualFrame frame, Object[] arguments);

    public final PException raiseOSError(VirtualFrame frame, int errno) {
        return execute(frame, new Object[]{errno});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage()});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage(), filename});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename, String filename2) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage(), filename, PNone.NONE, filename2});
    }

    @Specialization
    PException raiseOSError(VirtualFrame frame, Object[] arguments,
                    @Cached CallVarargsMethodNode callNode,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(PythonBuiltinClassType.OSError), arguments, PKeyword.EMPTY_KEYWORDS);
        return PRaiseNode.raise(this, error);
    }
}
