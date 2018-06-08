package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Wrappers for methods used by native code.
 */
public abstract class ManagedMethodWrappers {

    public abstract static class MethodWrapper implements TruffleObject {
        private final Object method;

        public MethodWrapper(Object method) {
            this.method = method;
        }

        public Object getMethod() {
            return method;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof MethodWrapper;
        }

        public ForeignAccess getForeignAccess() {
            return ManagedMethodWrappersMRForeign.ACCESS;
        }
    }

    static class MethKeywords extends MethodWrapper {

        public MethKeywords(Object method) {
            super(method);
        }
    }

    static class MethVarargs extends MethodWrapper {

        public MethVarargs(Object method) {
            super(method);
        }
    }

    /**
     * Creates a wrapper for signature {@code meth(*args, **kwargs)}.
     */
    public static MethodWrapper createKeywords(Object method) {
        return new MethKeywords(method);
    }

    /**
     * Creates a wrapper for signature {@code meth(*args)}.
     */
    public static MethodWrapper createVarargs(Object method) {
        return new MethVarargs(method);
    }

}
