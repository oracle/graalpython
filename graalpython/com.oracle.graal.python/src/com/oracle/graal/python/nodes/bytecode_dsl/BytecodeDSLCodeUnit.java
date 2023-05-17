package com.oracle.graal.python.nodes.bytecode_dsl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins.PBytecodeDSLSerializer;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen.Builder;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public class BytecodeDSLCodeUnit extends CodeUnit {
    /*
     * A {@link BytecodeDSLCodeUnit} is a context-independent representation of a root node. It
     * contains the bytes produced from Bytecode DSL serialization.
     *
     * Since it is expensive to serialize every root node, we perform serialization lazily using the
     * {@link BytecodeNodes} produced during parsing.
     *
     * When this code unit is directly instantiated via unmarshaling, there is no {@link
     * BytecodeNodes}; instead, we store the serialized bytes directly.
     */
    @CompilationFinal(dimensions = 1) private byte[] serialized;
    private final BytecodeRootNodes<PBytecodeDSLRootNode> nodes;

    public BytecodeDSLCodeUnit(TruffleString name, TruffleString qualname, int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags, TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn, int endLine, int endColumn,
                    byte[] serialized, BytecodeRootNodes<PBytecodeDSLRootNode> nodes) {
        super(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames, cellvars, freevars, cell2arg, constants, startLine, startColumn, endLine, endColumn);
        // Only one of these fields should be set. The other gets computed dynamically.
        assert serialized == null ^ nodes == null;
        this.serialized = serialized;
        this.nodes = nodes;
    }

    @TruffleBoundary
    public PBytecodeDSLRootNode createRootNode(PythonContext context, Source source) {
        byte[] toDeserialize = getSerialized(context.getTrue(), context.getFalse());
        BytecodeRootNodes<PBytecodeDSLRootNode> deserialized = MarshalModuleBuiltins.deserializeBytecodeNodes(context.getLanguage(), source, toDeserialize);
        assert deserialized.count() == 1;
        PBytecodeDSLRootNode result = deserialized.getNode(0);
        result.setMetadata(this);
        return result;
    }

    public byte[] getSerialized(PInt pyTrue, PInt pyFalse) {
        byte[] result = serialized;
        if (result == null) {
            synchronized (this) {
                result = serialized;
                if (result == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    result = serialized = computeSerialized(pyTrue, pyFalse);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    private byte[] computeSerialized(PInt pyTrue, PInt pyFalse) {
        try {
            BytecodeSerializer serializer = new PBytecodeDSLSerializer(pyTrue, pyFalse);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            nodes.serialize(new DataOutputStream(bytes), serializer);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

}
