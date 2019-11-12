package com.oracle.graal.python.builtins.objects.bytes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = PythonBufferLibrary.class, receiverType = String.class)
final class DefaultBufferStringExports {
    @ExportMessage
    static boolean isBuffer(@SuppressWarnings("unused") String str) {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    static int getBufferLength(@SuppressWarnings("unused") String str) {
        return getBufferBytes(str).length;
    }

    @ExportMessage
    @TruffleBoundary
    static byte[] getBufferBytes(String str) {
        return str.getBytes();
    }
}
