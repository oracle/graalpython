package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.nio.ByteBuffer;
import java.util.List;

import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSSLSocket)
public class SSLSocketBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLSocketBuiltinsFactory.getFactories();
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, parameterNames = {"$self", "len", "buffer"})
    @ArgumentClinic(name = "len", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "isNoValue(buffer)")
        Object read(PSSLSocket self, int len, @SuppressWarnings("unused") PNone buffer) {
            if (len < 0) {
                throw raise(ValueError, ErrorMessages.SIZE_SHOULD_NOT_BE_NEGATIVE);
            }
            ByteBuffer output = ByteBuffer.allocate(len);
            SSLEngineHelper.read(this, self, output);
            return factory().createBytes(output.array(), output.limit());
        }

        @Specialization(guards = "!isNoValue(buffer)")
        Object readInto(PSSLSocket self, int len, Object buffer) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"})
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        Object write(PSSLSocket self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                byte[] bytes = bufferLib.getBufferBytes(buffer);
                int lenght = bufferLib.getBufferLength(buffer);
                ByteBuffer input = ByteBuffer.wrap(bytes, 0, lenght);
                SSLEngineHelper.write(this, self, input);
                return lenght;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object buffer) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, buffer);
        }
    }

    @Builtin(name = "do_handshake", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DoHandshakeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doHandshake(PSSLSocket self) {
            SSLEngineHelper.handshake(this, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = "context", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PSSLContext getContext(PSSLSocket self) {
            return self.getContext();
        }
    }

    @Builtin(name = "version", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class VersionNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getVersion(PSSLSocket self) {
            if (self.isHandshakeComplete()) {
                return getProtocol(self.getEngine());
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static String getProtocol(SSLEngine engine) {
            return engine.getSession().getProtocol();
        }
    }
}
