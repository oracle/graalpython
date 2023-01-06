/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins.AllocateLockNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.AcquireLockNode;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.ReleaseLockNode;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextCEvalBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextCEvalBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyThread_allocate_lock")
    @GenerateNodeFactory
    public abstract static class PyThreadAllocateLockNode extends PythonBuiltinNode {
        @Specialization
        public Object allocate(VirtualFrame frame,
                        @Cached AllocateLockNode allocateNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return allocateNode.execute(frame, PNone.NO_VALUE, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyThread_acquire_lock", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyThreadAcquireLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static int acquire(VirtualFrame frame, PLock lock, int waitflag,
                        @Cached AcquireLockNode acquireNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return ((boolean) acquireNode.execute(frame, lock, waitflag, PNone.NONE)) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PyThread_release_lock", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyThreadReleaseLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object release(VirtualFrame frame, PLock lock,
                        @Cached ReleaseLockNode releaseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return releaseNode.execute(frame, lock);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyEval_GetBuiltins")
    @GenerateNodeFactory
    public abstract static class PyEvalGetBuiltinsNode extends PythonBuiltinNode {
        @Specialization
        public Object release(@Cached GetDictIfExistsNode getDictNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                PythonModule cext = getCore().getBuiltins();
                return getDictNode.execute(cext);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyEval_EvalCodeEx", minNumOfPositionalArgs = 8, needsFrame = true)
    @GenerateNodeFactory
    abstract static class PyEvalEvalCodeEx extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object codeWrapper, Object globalsWrapper, Object localsWrapper,
                        Object argumentArrayPtr, Object kwsPtr, Object defaultValueArrayPtr,
                        Object kwdefaultsWrapper, Object closureWrapper,
                        @CachedLibrary(limit = "2") InteropLibrary ptrLib,
                        @Cached CExtNodes.AsPythonObjectNode codeAsPythonObjectNode,
                        @Cached CExtNodes.AsPythonObjectNode globalsAsPythonObjectNode,
                        @Cached CExtNodes.AsPythonObjectNode localsAsPythonObjectNode,
                        @Cached CExtNodes.AsPythonObjectNode kwdefaultsAsPythonObjectNode,
                        @Cached CExtNodes.AsPythonObjectNode closureAsPythonObjectNode,
                        @Cached CExtNodes.ToJavaNode elementToJavaNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached CodeNodes.GetCodeSignatureNode getSignatureNode,
                        @Cached CodeNodes.GetCodeCallTargetNode getCallTargetNode,
                        @Cached CreateArgumentsNode.CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                        @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached CExtNodes.ToNewRefNode toNewRefNode,
                        @Cached GenericInvokeNode invokeNode) {
            PCode code = (PCode) codeAsPythonObjectNode.execute(codeWrapper);
            Object globals = globalsAsPythonObjectNode.execute(globalsWrapper);
            Object locals = localsAsPythonObjectNode.execute(localsWrapper);
            Object[] defaults = unwrapArray(defaultValueArrayPtr, ptrLib, elementToJavaNode);
            PKeyword[] kwdefaults = expandKeywordStarargsNode.execute(kwdefaultsAsPythonObjectNode.execute(kwdefaultsWrapper));
            PCell[] closure = null;
            Object closureObj = closureAsPythonObjectNode.execute(closureWrapper);
            if (closureObj != PNone.NO_VALUE) {
                // CPython also just accesses the object as tuple without further checks.
                closure = PCell.toCellArray(getObjectArrayNode.execute(closureObj));
            }
            Object[] kws = unwrapArray(kwsPtr, ptrLib, elementToJavaNode);

            PKeyword[] keywords = PKeyword.create(kws.length / 2);
            for (int i = 0; i < kws.length / 2; i += 2) {
                TruffleString keywordName = castToStringNode.execute(kws[i]);
                keywords[i] = new PKeyword(keywordName, kws[i + 1]);
            }

            // prepare Python frame arguments
            Object[] userArguments = unwrapArray(argumentArrayPtr, ptrLib, elementToJavaNode);
            Signature signature = getSignatureNode.execute(code);
            Object[] pArguments = createAndCheckArgumentsNode.execute(code, userArguments, keywords, signature, null, null, defaults, kwdefaults, false);

            // set custom locals
            if (!(locals instanceof PNone)) {
                PArguments.setSpecialArgument(pArguments, locals);
            }
            PArguments.setClosure(pArguments, closure);
            // TODO(fa): set builtins in globals
            // PythonModule builtins = getContext().getBuiltins();
            // setBuiltinsInGlobals(frame, globals, setBuiltins, builtins, lib);
            if (globals instanceof PythonObject) {
                PArguments.setGlobals(pArguments, (PythonObject) globals);
            } else {
                // TODO(fa): raise appropriate exception
            }

            try {
                RootCallTarget rootCallTarget = getCallTargetNode.execute(code);
                Object result = invokeNode.execute(frame, rootCallTarget, pArguments);
                return toNewRefNode.execute(result);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }

        private static Object[] unwrapArray(Object ptr, InteropLibrary ptrLib, CExtNodes.ToJavaNode elementToJavaNode) {
            if (ptrLib.hasArrayElements(ptr)) {
                try {
                    int size = PInt.intValueExact(ptrLib.getArraySize(ptr));
                    Object[] result = new Object[size];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = elementToJavaNode.execute(ptrLib.readArrayElement(ptr, i));
                    }
                    return result;
                } catch (UnsupportedMessageException | OverflowException | InvalidArrayIndexException e) {
                    // fall through
                }
            }
            /*
             * Whenever some access goes wrong then this would basically be a segfault in CPython.
             * So, we just throw a fatal exception which is not a Python exception.
             */
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
