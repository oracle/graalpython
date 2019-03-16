package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class PRaiseNode extends Node {

    public abstract PException execute(VirtualFrame frame, Object type, Object cause, Object format, Object[] arguments);

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        throw execute(null, type, PNone.NO_VALUE, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw execute(null, type, PNone.NO_VALUE, getMessage(e), new Object[0]);
    }

    public final PException raiseIndexError() {
        return raise(PythonErrorType.IndexError, "cannot fit 'int' into an index-sized integer");
    }

    public final PException raiseOSError(VirtualFrame frame, int errno) {
        return raiseOSError(frame, new Object[]{errno});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror) {
        return raiseOSError(frame, new Object[]{oserror.getNumber(), oserror.getMessage()});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, Exception e) {
        return raiseOSError(frame, new Object[]{oserror.getNumber(), oserror.getMessage(), getMessage(e)});
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename) {
        Object[] args = new Object[]{oserror.getNumber(), oserror.getMessage(), filename};
        return raiseOSError(frame, args);
    }

    public final PException raiseOSError(VirtualFrame frame, OSErrorEnum oserror, String filename, String filename2) {
        Object[] args = new Object[]{oserror.getNumber(), oserror.getMessage(), filename, PNone.NONE, filename2};
        return raiseOSError(frame, args);
    }

    public final PException raiseOSError(VirtualFrame frame, Object[] args) {
        throw execute(frame, PythonBuiltinClassType.OSError, PNone.NO_VALUE, PNone.NO_VALUE, args);
    }

    public final PException raise(LazyPythonClass exceptionType) {
        throw execute(null, exceptionType, PNone.NO_VALUE, PNone.NO_VALUE, new Object[0]);
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        throw execute(null, type, cause, format, arguments);
    }

    public final PException raise(PBaseException exc) {
        throw raise(this, exc);
    }

    public static PException raise(Node raisingNode, PBaseException exc) {
        if (raisingNode.isAdoptable()) {
            throw PException.fromObject(exc, raisingNode);
        } else {
            throw PException.fromObject(exc, NodeUtil.getCurrentEncapsulatingNode());
        }
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"})
    PException doPythonType(@SuppressWarnings("unused") VirtualFrame frame, LazyPythonClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(exceptionType));
    }

    @Specialization(guards = {"isNoValue(cause)"})
    PException doBuiltinType(@SuppressWarnings("unused") VirtualFrame frame, PythonBuiltinClassType type, @SuppressWarnings("unused") PNone cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        assert format != null;
        throw raise(factory.createBaseException(type, format, arguments));
    }

    protected static boolean isOSError(PythonBuiltinClassType cls) {
        return cls == PythonBuiltinClassType.OSError;
    }

    @Specialization(guards = {"isOSError(cls)", "isNoValue(cause)", "isNoValue(format)"})
    PException doOSError(VirtualFrame frame, PythonBuiltinClassType cls,  @SuppressWarnings("unused") PNone cause,  @SuppressWarnings("unused") PNone format, Object[] arguments,
                         @Cached CallNode callNode,
                         @CachedContext(PythonLanguage.class) PythonContext ctxt) {
        PBaseException error = (PBaseException) callNode.execute(frame, ctxt.getCore().lookupType(cls), arguments, PKeyword.EMPTY_KEYWORDS);
        throw raise(error);
    }

    @Specialization(guards = {"!isOSError(type)", "isNoValue(cause)"})
    PException doBuiltinTypeWithCause(@SuppressWarnings("unused") VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Cached WriteAttributeToDynamicObjectNode writeCause) {
        assert format != null;
        PBaseException baseException = factory.createBaseException(type, format, arguments);
        writeCause.execute(baseException.getStorage(), SpecialAttributeNames.__CAUSE__, cause);
        throw raise(baseException);
    }

    @TruffleBoundary
    private static final String getMessage(Exception e) {
        return e.getMessage();
    }

    public static PRaiseNode create() {
        return PRaiseNodeGen.create();
    }

    public static PRaiseNode getUncached() {
        return PRaiseNodeGen.getUncached();
    }
}
