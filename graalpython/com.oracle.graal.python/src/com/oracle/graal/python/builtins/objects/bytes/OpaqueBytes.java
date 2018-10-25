package com.oracle.graal.python.builtins.objects.bytes;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public final class OpaqueBytes implements TruffleObject {
    private static final Assumption neverOpaqueAssumption = Truffle.getRuntime().createAssumption("all contexts use a readable filesystem");
    private static final Assumption alwaysOpaqueAssumption = Truffle.getRuntime().createAssumption("no context has a readable filesystem");
    private final byte[] bytes;

    public OpaqueBytes(byte[] bytes) {
        assert !neverOpaqueAssumption.isValid();
        this.bytes = bytes;
    }

    public ForeignAccess getForeignAccess() {
        return OpaqueBytesMessageResolutionForeign.ACCESS;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public static boolean isInstance(Object next) {
        if (neverOpaqueAssumption.isValid()) {
            return false;
        }
        return next instanceof OpaqueBytes;
    }

    public static boolean isEnabled(PythonContext context) {
        if (neverOpaqueAssumption.isValid()) {
            return false;
        } else if (alwaysOpaqueAssumption.isValid()) {
            return true;
        }
        return checkOption(context);
    }

    private static Boolean checkOption(PythonContext context) {
        return PythonOptions.getOption(context, PythonOptions.OpaqueFilesystem);
    }

    public static void initializeForNewContext(PythonContext context) {
        CompilerDirectives.transferToInterpreter();
        if (checkOption(context)) {
            neverOpaqueAssumption.invalidate();
        } else {
            alwaysOpaqueAssumption.invalidate();
        }
    }

    @MessageResolution(receiverType = OpaqueBytes.class)
    static class OpaqueBytesMessageResolution {
        @Resolve(message = "HAS_SIZE")
        abstract static class HasSizeNode extends Node {
            Object access(@SuppressWarnings("unused") OpaqueBytes object) {
                return true;
            }
        }

        @Resolve(message = "GET_SIZE")
        abstract static class SizeNode extends Node {
            Object access(OpaqueBytes object) {
                return object.bytes.length;
            }
        }

        @CanResolve
        abstract static class CheckFunction extends Node {
            protected static boolean test(TruffleObject receiver) {
                return receiver instanceof OpaqueBytes;
            }
        }
    }
}
