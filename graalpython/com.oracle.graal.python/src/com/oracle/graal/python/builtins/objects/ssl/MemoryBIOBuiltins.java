package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMemoryBIO)
public class MemoryBIOBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MemoryBIOBuiltinsFactory.getFactories();
    }

    @Builtin(name = "MemoryBIO", constructsClass = PythonBuiltinClassType.PMemoryBIO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class MemoryBIONode extends PythonUnaryBuiltinNode {
        @Specialization
        PMemoryBIO create(Object type) {
            return factory().createMemoryBIO(type);
        }
    }

    @Builtin(name = "pending", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PendingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getPending(PMemoryBIO self) {
            return self.getBio().getPending();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean eof(PMemoryBIO self) {
            return self.getBio().isEOF();
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PBytes read(PMemoryBIO self, int size) {
            int len = size >= 0 ? size : Integer.MAX_VALUE;
            return factory().createBytes(self.getBio().read(len));
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MemoryBIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "lib.isBuffer(buffer)", limit = "3")
        int write(PMemoryBIO self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary lib) {
            if (self.getBio().isEOF()) {
                throw raise(SSLError, "cannot write() after write_eof()");
            }
            try {
                byte[] bytes = lib.getBufferBytes(buffer);
                int len = lib.getBufferLength(buffer);
                self.getBio().write(bytes, len);
                return len;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, arg);
        }
    }

    @Builtin(name = "write_eof", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WriteEOFNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone writeEOF(PMemoryBIO self) {
            self.getBio().writeEOF();
            return PNone.NONE;
        }
    }
}
