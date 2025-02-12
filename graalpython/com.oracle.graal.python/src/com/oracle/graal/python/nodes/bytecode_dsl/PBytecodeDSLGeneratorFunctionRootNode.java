package com.oracle.graal.python.nodes.bytecode_dsl;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public final class PBytecodeDSLGeneratorFunctionRootNode extends PRootNode {
    private final PBytecodeDSLRootNode rootNode;
    private final TruffleString originalName;
    private final ConditionProfile isIterableCoroutine = ConditionProfile.create();

    @TruffleBoundary
    public PBytecodeDSLGeneratorFunctionRootNode(PythonLanguage language, FrameDescriptor frameDescriptor, PBytecodeDSLRootNode rootNode, TruffleString originalName) {
        super(language, frameDescriptor);
        this.rootNode = rootNode;
        this.originalName = originalName;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();

        // This is passed from InvokeNode node
        PFunction generatorFunction = PArguments.getGeneratorFunction(arguments);
        assert generatorFunction != null;
        if (rootNode.getCodeUnit().isGenerator()) {
            // if CO_ITERABLE_COROUTINE was explicitly set (likely by types.coroutine), we have to
            // pass the information to the generator
            // .gi_code.co_flags will still be wrong, but at least await will work correctly
            if (isIterableCoroutine.profile((generatorFunction.getCode().getFlags() & 0x100) != 0)) {
                return rootNode.factory.createIterableCoroutine(generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
            } else {
                return rootNode.factory.createGenerator(generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
            }
        } else if (rootNode.getCodeUnit().isCoroutine()) {
            return rootNode.factory.createCoroutine(generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
        } else if (rootNode.getCodeUnit().isAsyncGenerator()) {
            /*
             * TODO: Support async generators.
             *
             * We need to produce something instead of failing here because async generators are
             * instantiated in frozen module code.
             */
            return PNone.NONE;
        }
        throw CompilerDirectives.shouldNotReachHere("Unknown generator/coroutine type");
    }

    @Override
    public String getName() {
        return originalName.toJavaStringUncached();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<generator function root " + originalName + ">";
    }

    @Override
    public Signature getSignature() {
        return rootNode.getSignature();
    }

    @Override
    public boolean isPythonInternal() {
        return rootNode.isPythonInternal();
    }

    @Override
    public SourceSection getSourceSection() {
        return rootNode.getSourceSection();
    }

    @Override
    protected byte[] extractCode() {
        return rootNode.extractCode();
    }

    public PBytecodeDSLRootNode getBytecodeRootNode() {
        return rootNode;
    }
}