package com.oracle.graal.python.runtime;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;

/**
 * Represents the result of {@code path_t} conversion. Similar to CPython's {@code path_t}
 * structure, but only contains the results of the conversion, not the conversion parameters.
 */
public abstract class PosixFileHandle {

    public static final PosixFileHandle DEFAULT = new PosixFileHandle() {
    };

    /**
     * Contains the original object (or the object returned by {@code __fspath__}) for auditing
     * purposes. This field is {code null} iff the path parameter was optional and the caller did
     * not provide it.
     */
    public final Object originalObject;

    private PosixFileHandle() {
        originalObject = null;
    }

    protected PosixFileHandle(Object originalObject) {
        assert originalObject != null;
        this.originalObject = originalObject;
    }

    /**
     * Contains the path as a sequence of bytes (already fs-encoded, but without the terminating
     * null character).
     */
    public static class PosixPath extends PosixFileHandle {
        public final byte[] path;

        public PosixPath(Object originalObject, byte[] path) {
            super(originalObject);
            assert path != null;
            this.path = path;
        }
    }

    /**
     * Contains the file descriptor if it was allowed in
     * {@link PosixModuleBuiltins.PathConversionNode} and the caller provided an integer instead of
     * a path.
     */
    public static class PosixFd extends PosixFileHandle {
        public final int fd;

        public PosixFd(Object originalObject, int fd) {
            super(originalObject);
            this.fd = fd;
        }
    }
}
