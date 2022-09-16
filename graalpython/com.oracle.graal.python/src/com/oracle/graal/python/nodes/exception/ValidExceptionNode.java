package com.oracle.graal.python.nodes.exception;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class ValidExceptionNode extends Node {
    public abstract boolean execute(Frame frame, Object type);

    protected boolean emulateJython() {
        return PythonLanguage.get(this).getEngineOption(PythonOptions.EmulateJython);
    }

    protected static boolean isPythonExceptionType(PythonBuiltinClassType type) {
        PythonBuiltinClassType base = type;
        while (base != null) {
            if (base == PythonBuiltinClassType.PBaseException) {
                return true;
            }
            base = base.getBase();
        }
        return false;
    }

    @Specialization(guards = "cachedType == type", limit = "3")
    static boolean isPythonExceptionTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType type,
                    @SuppressWarnings("unused") @Cached("type") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(type)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "cachedType == klass.getType()", limit = "3")
    static boolean isPythonExceptionClassCached(@SuppressWarnings("unused") PythonBuiltinClass klass,
                    @SuppressWarnings("unused") @Cached("klass.getType()") PythonBuiltinClassType cachedType,
                    @Cached("isPythonExceptionType(cachedType)") boolean isExceptionType) {
        return isExceptionType;
    }

    @Specialization(guards = "isTypeNode.execute(type)", limit = "1", replaces = {"isPythonExceptionTypeCached", "isPythonExceptionClassCached"})
    static boolean isPythonException(VirtualFrame frame, Object type,
                    @SuppressWarnings("unused") @Cached TypeNodes.IsTypeNode isTypeNode,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(frame, type, PythonBuiltinClassType.PBaseException);
    }

    protected boolean isHostObject(Object object) {
        return PythonContext.get(this).getEnv().isHostObject(object);
    }

    @Specialization(guards = {"emulateJython()", "isHostObject(type)"})
    @SuppressWarnings("unused")
    boolean isJavaException(@SuppressWarnings("unused") VirtualFrame frame, Object type) {
        Object hostType = PythonContext.get(this).getEnv().asHostObject(type);
        return hostType instanceof Class && Throwable.class.isAssignableFrom((Class<?>) hostType);
    }

    @Fallback
    static boolean isAnException(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object type) {
        return false;
    }

    static ValidExceptionNode create() {
        return ValidExceptionNodeGen.create();
    }
}
