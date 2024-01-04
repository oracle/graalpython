package com.oracle.graal.python.builtins.modules.pickle;

public class MemoKeyError extends Exception {
    public static final MemoKeyError INSTANCE = new MemoKeyError();

    private static final long serialVersionUID = -5143517716899396171L;

    private MemoKeyError() {
        /*
         * We use the super constructor that initializes the cause to null. Without that, the cause
         * would be this exception itself. This helps escape analysis: it avoids the circle of an
         * object pointing to itself. We also do not need a message, so we use the constructor that
         * also allows us to set the message to null.
         */
        super(null, null);
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }
}
