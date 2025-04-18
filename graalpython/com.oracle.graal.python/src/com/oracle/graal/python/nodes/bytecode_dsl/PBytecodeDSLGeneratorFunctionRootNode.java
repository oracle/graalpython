/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.bytecode_dsl;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public final class PBytecodeDSLGeneratorFunctionRootNode extends PRootNode {
    private final PBytecodeDSLRootNode rootNode;
    private final TruffleString originalName;
    private final ConditionProfile isIterableCoroutine = ConditionProfile.create();

    public PBytecodeDSLGeneratorFunctionRootNode(PythonLanguage language, FrameDescriptor frameDescriptor, PBytecodeDSLRootNode rootNode, TruffleString originalName) {
        super(language, frameDescriptor);
        CompilerAsserts.neverPartOfCompilation();
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
                return PFactory.createIterableCoroutine(rootNode.getLanguage(), generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
            } else {
                return PFactory.createGenerator(rootNode.getLanguage(), generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
            }
        } else if (rootNode.getCodeUnit().isCoroutine()) {
            return PFactory.createCoroutine(rootNode.getLanguage(), generatorFunction.getName(), generatorFunction.getQualname(), rootNode, arguments);
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
