package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class PRaiseNode extends Node {

    public abstract PException execute(Object type, Object cause, Object format, Object[] arguments);

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        throw execute(type, PNone.NO_VALUE, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw execute(type, PNone.NO_VALUE, getMessage(e), new Object[0]);
    }

    public final PException raiseIndexError() {
        return raise(PythonErrorType.IndexError, "cannot fit 'int' into an index-sized integer");
    }

    public final PException raise(LazyPythonClass exceptionType) {
        throw execute(exceptionType, PNone.NO_VALUE, PNone.NO_VALUE, new Object[0]);
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        throw execute(type, cause, format, arguments);
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
    PException doPythonType(LazyPythonClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(exceptionType));
    }

    @Specialization(guards = {"isNoValue(cause)"})
    PException doBuiltinType(PythonBuiltinClassType type, @SuppressWarnings("unused") PNone cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        assert format != null;
        throw raise(factory.createBaseException(type, format, arguments));
    }

    @Specialization(guards = {"!isNoValue(cause)"})
    PException doBuiltinTypeWithCause(PythonBuiltinClassType type, PBaseException cause, String format, Object[] arguments,
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
