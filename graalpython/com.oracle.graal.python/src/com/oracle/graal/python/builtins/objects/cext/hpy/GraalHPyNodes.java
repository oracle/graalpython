/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethDirectRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethKeywordsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethNoargsRoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethORoot;
import com.oracle.graal.python.builtins.modules.ExternalFunctionNodes.MethVarargsRoot;
import com.oracle.graal.python.builtins.modules.PythonCextBuiltins.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyExternalFunctionNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public class GraalHPyNodes {

    @GenerateUncached
    abstract static class ImportHPySymbolNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext context, String name);

        @Specialization(guards = "cachedName == name", limit = "1", assumptions = "singleContextAssumption()")
        Object doReceiverCachedIdentity(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(guards = "cachedName.equals(name)", limit = "1", assumptions = "singleContextAssumption()", replaces = "doReceiverCachedIdentity")
        Object doReceiverCached(GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCached", "doReceiverCachedIdentity"})
        Object doGeneric(GraalHPyContext context, String name,
                        @CachedLibrary(limit = "1") @SuppressWarnings("unused") InteropLibrary interopLib,
                        @Cached PRaiseNode raiseNode) {
            return importHPySymbol(raiseNode, interopLib, context.getLLVMLibrary(), name);
        }

        protected static Object importHPySymbolUncached(GraalHPyContext context, String name) {
            Object hpyLibrary = context.getLLVMLibrary();
            InteropLibrary uncached = InteropLibrary.getFactory().getUncached(hpyLibrary);
            return importHPySymbol(PRaiseNode.getUncached(), uncached, hpyLibrary, name);
        }

        private static Object importHPySymbol(PRaiseNode raiseNode, InteropLibrary library, Object capiLibrary, String name) {
            try {
                return library.readMember(capiLibrary, name);
            } catch (UnknownIdentifierException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "invalid C API function: %s", name);
            } catch (UnsupportedMessageException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "corrupted C API library object: %s", capiLibrary);
            }
        }

    }

    @GenerateUncached
    public abstract static class PCallHPyFunction extends PNodeWithContext {

        public final Object call(GraalHPyContext context, String name, Object... args) {
            return execute(context, name, args);
        }

        abstract Object execute(GraalHPyContext context, String name, Object[] args);

        @Specialization
        Object doIt(GraalHPyContext context, String name, Object[] args,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Cached ImportHPySymbolNode importCAPISymbolNode,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            try {
                return interopLibrary.execute(importCAPISymbolNode.execute(context, name), args);
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "HPy C API symbol %s is not callable", name);
            }
        }
    }

    @ExportLibrary(NativeTypeLibrary.class)
    @ExportLibrary(InteropLibrary.class)
    public static class PtrArray implements TruffleObject {

        private final Object[] arr;

        public PtrArray(Object[] arr) {
            this.arr = arr;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return arr.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < arr.length;
        }

        @ExportMessage
        boolean isArrayElementModifiable(long idx) {
            return isArrayElementReadable(idx);
        }

        @ExportMessage
        boolean isArrayElementInsertable(long idx) {
            return false;
        }

        @ExportMessage
        Object readArrayElement(long idx) {
            return arr[(int) idx];
        }

        @ExportMessage
        void writeArrayElement(long idx, Object value) {
            arr[(int) idx] = value;
        }

        @ExportMessage
        boolean hasNativeType() {
            return false;
        }

        @ExportMessage
        Object getNativeType() {
            return null;
        }
    }

    @GenerateUncached
    public abstract static class HPyAddFunctionNode extends PNodeWithContext {

        private static final Signature SIGNATURE = Signature.createVarArgsAndKwArgsOnly();

        public abstract PBuiltinFunction execute(GraalHPyContext context, Object methodDef);

        @Specialization(limit = "1")
        static PBuiltinFunction doIt(GraalHPyContext context, Object methodDef,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callGetNameNode,
                        @Cached PCallHPyFunction callGetDocNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached BranchProfile profile,
                        @Cached PRaiseNode raiseNode) {
            String methodName = castToJavaStringNode.execute(callGetNameNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_GET_ML_NAME, methodDef));
            String methodDoc = castToJavaStringNode.execute(callGetDocNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_GET_ML_DOC, methodDef));

            try {
                Object methodFlagsObj = interopLibrary.readMember(methodDef, "ml_flags");
                if (!resultLib.fitsInInt(methodFlagsObj)) {
                    CompilerDirectives.transferToInterpreter();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_flags of %s is not an integer", methodName);
                }
                int flags = resultLib.asInt(methodFlagsObj);

                Object mlMethObj = interopLibrary.readMember(methodDef, "ml_meth");
                if (!resultLib.isExecutable(mlMethObj)) {
                    CompilerDirectives.transferToInterpreter();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_meth of %s is not callable", methodName);
                }

                RootCallTarget callTarget;
                PythonLanguage language = context.getContext().getLanguage();
                boolean hpyStyleMeth = context.isHPyMeth(flags);
                if (hpyStyleMeth) {
                    // HPy-style methods
                    Object[] callable = new Object[1];
                    Object[] trampoline = new Object[1];
                    resultLib.execute(mlMethObj, new PtrArray(callable), new PtrArray(trampoline));
                    callTarget = PExternalFunctionWrapper.createCallTarget(HPyExternalFunctionNode.create(language, methodName, callable[0], SIGNATURE, !CExtContext.isMethVarargs(flags)));
                } else {
                    // CPy-style methods
                    // TODO(fa) support static and class methods
                    // n.b. the TruffleObject type cast is safe since we received the object via
                    // interop and it must therefore be a TruffleObject
                    callTarget = PExternalFunctionWrapper.createCallTarget(MethDirectRoot.create(language, methodName, mlMethObj));
                }

                PBuiltinFunction builtinFunction = factory.createBuiltinFunction(methodName, null, 0, callTarget);
                return createWrapperFunction(builtinFunction, createWrapperRootNode(context, flags, hpyStyleMeth, builtinFunction));
            } catch (UnsupportedTypeException | ArityException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, e);
            } catch (UnsupportedMessageException e) {
                profile.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "HPy C API symbol %s is not callable", "");
            } catch (UnknownIdentifierException e) {
                e.printStackTrace();
            }
            return null;
        }

        @TruffleBoundary
        private static PBuiltinFunction createFunction(PythonLanguage lang, String name, Object callable, boolean doConversion) {
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(HPyExternalFunctionNode.create(lang, name, callable, SIGNATURE, doConversion));
            return PythonObjectFactory.getUncached().createBuiltinFunction(name, null, 0, callTarget);
        }

        @TruffleBoundary
        private static PBuiltinFunction createWrapperFunction(PBuiltinFunction function, PRootNode rootNode) {
            return PythonObjectFactory.getUncached().createBuiltinFunction(function.getName(), function.getEnclosingType(), 0, Truffle.getRuntime().createCallTarget(rootNode));
        }

        @TruffleBoundary
        private static PRootNode createWrapperRootNode(GraalHPyContext hpyContext, int flags, boolean hpyStyleMeth, PBuiltinFunction externalFunction) {
            if (CExtContext.isMethNoArgs(flags)) {
                return new MethNoargsRoot(hpyContext.getContext().getLanguage(), externalFunction.getName(), externalFunction);
            } else if (CExtContext.isMethO(flags)) {
                return new MethORoot(hpyContext.getContext().getLanguage(), externalFunction.getName(), externalFunction);
            } else if (CExtContext.isMethKeywords(flags)) {
                return new MethKeywordsRoot(hpyContext.getContext().getLanguage(), externalFunction.getName(), externalFunction);
            } else if (hpyStyleMeth && CExtContext.isMethVarargs(flags)) {
                return new HPyMethVarargsRoot(hpyContext.getContext().getLanguage(), externalFunction.getName(), externalFunction);
            } else if (!hpyStyleMeth && CExtContext.isMethVarargs(flags)) {
                return new MethVarargsRoot(hpyContext.getContext().getLanguage(), externalFunction.getName(), externalFunction);
            }
            throw new IllegalStateException("illegal method flags");
        }
    }

    public static class HPyMethVarargsRoot extends PRootNode {
        private static final Signature SIGNATURE = new Signature(false, 1, false, new String[]{"self"}, new String[0]);
        @Child private ReadVarArgsNode readVarargsNode;
        @Child private HPyAsHandleNode selfAsHandleNode;

        @Child private CalleeContext calleeContext;
        @Child private CallVarargsMethodNode invokeNode;
        @Child ReadIndexedArgumentNode readSelfNode = ReadIndexedArgumentNode.create(0);

        private final ConditionProfile customLocalsProfile = ConditionProfile.createCountingProfile();

        private final String name;
        private final Object callable;

        @TruffleBoundary
        public HPyMethVarargsRoot(PythonLanguage language, String name, Object callable) {
            super(language);
            this.name = name;
            this.callable = callable;
            this.calleeContext = CalleeContext.create();
            this.invokeNode = CallVarargsMethodNode.create();
            this.readVarargsNode = ReadVarArgsNode.create(1, true);
            this.selfAsHandleNode = HPyAsHandleNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
            try {
                Object self = readSelfNode.execute(frame);
                Object[] args = readVarargsNode.executeObjectArray(frame);
                Object[] arguments = PArguments.create();
                PArguments.setVariableArguments(arguments, selfAsHandleNode.execute(self), new HPyArrayWrapper(args), (long) args.length);
                return invokeNode.execute(frame, callable, arguments, PKeyword.EMPTY_KEYWORDS);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NodeCost getCost() {
            // this is just a thin argument shuffling wrapper
            return NodeCost.NONE;
        }

        @Override
        public String toString() {
            return "<METH root " + name + ">";
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    @GenerateUncached
    public abstract static class HPyAsContextNode extends PNodeWithContext {

        public abstract GraalHPyContext execute(Object object);

        public abstract GraalHPyContext executeInt(int l);

        public abstract GraalHPyContext executeLong(long l);

        @Specialization
        static GraalHPyContext doHandle(GraalHPyContext hpyContext) {
            return hpyContext;
        }

        // n.b. we could actually accept anything else but we have specializations to be more strict
        // about what we expect

        @Specialization
        static GraalHPyContext doInt(@SuppressWarnings("unused") int handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }

        @Specialization
        static GraalHPyContext doLong(@SuppressWarnings("unused") long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }

        @Specialization(guards = "interopLibrary.isPointer(handle)", limit = "2")
        static GraalHPyContext doLong(@SuppressWarnings("unused") Object handle,
                        @CachedLibrary("handle") @SuppressWarnings("unused") InteropLibrary interopLibrary,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext();
        }
    }

    @GenerateUncached
    public abstract static class HPyEnsureHandleNode extends PNodeWithContext {

        public abstract GraalHPyHandle execute(GraalHPyContext hpyContext, Object object);

        public abstract GraalHPyHandle executeInt(GraalHPyContext hpyContext, int l);

        public abstract GraalHPyHandle executeLong(GraalHPyContext hpyContext, long l);

        @Specialization
        static GraalHPyHandle doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, GraalHPyHandle handle) {
            return handle;
        }

        @Specialization(guards = {"hpyContext != null", "interopLibrary.isPointer(handle)"}, limit = "2")
        static GraalHPyHandle doPointer(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object handle,
                        @CachedLibrary("handle") InteropLibrary interopLibrary,
                        @Cached PRaiseNode raiseNode) {
            try {
                return doLongOvfWithContext(hpyContext, interopLibrary.asPointer(handle), raiseNode);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("");
            }
        }

        @Specialization(guards = "hpyContext == null")
        static GraalHPyHandle doInt(@SuppressWarnings("unused") GraalHPyContext hpyContext, int handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext().getObjectForHPyHandle(handle);
        }

        @Specialization(guards = "hpyContext == null", rewriteOn = OverflowException.class)
        static GraalHPyHandle doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) throws OverflowException {
            return context.getHPyContext().getObjectForHPyHandle(PInt.intValueExact(handle));
        }

        @Specialization(guards = "hpyContext == null", replaces = "doLong")
        static GraalHPyHandle doLongOvf(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            return doLongOvfWithContext(context.getHPyContext(), handle, raiseNode);
        }

        @Specialization(guards = "hpyContext != null")
        static GraalHPyHandle doIntWithContext(GraalHPyContext hpyContext, int handle) {
            return hpyContext.getObjectForHPyHandle(handle);
        }

        @Specialization(guards = "hpyContext != null", rewriteOn = OverflowException.class)
        static GraalHPyHandle doLongWithContext(GraalHPyContext hpyContext, long handle) throws OverflowException {
            return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle));
        }

        @Specialization(guards = "hpyContext != null", replaces = "doLongWithContext")
        static GraalHPyHandle doLongOvfWithContext(GraalHPyContext hpyContext, long handle,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode) {
            try {
                return hpyContext.getObjectForHPyHandle(PInt.intValueExact(handle));
            } catch (OverflowException e) {
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "unknown handle: %d", handle);
            }
        }
    }

    @GenerateUncached
    public abstract static class HPyAsPythonObjectNode extends CExtToJavaNode {

        @Specialization
        static Object doInt(@SuppressWarnings("unused") GraalHPyContext hpyContext, int handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeInt(hpyContext, handle).getDelegate();
        }

        @Specialization
        static Object doLong(@SuppressWarnings("unused") GraalHPyContext hpyContext, long handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeLong(hpyContext, handle).getDelegate();
        }

        @Specialization
        static Object doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, Object object,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.execute(hpyContext, object).getDelegate();
        }
    }

    @GenerateUncached
    public abstract static class HPyAsHandleNode extends CExtToNativeNode {

        // TODO(fa) implement handles for primitives that avoid boxing

        @Specialization
        static GraalHPyHandle doObject(@SuppressWarnings("unused") CExtContext hpyContext, Object object) {
            return new GraalHPyHandle(object);
        }

    }

    public abstract static class HPyAllAsHandleNode extends PNodeWithContext {
        public abstract void executeInto(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset);

        static boolean isArgsOffsetPlus(int len, int off, int plus) {
            return len == off + plus;
        }

        static boolean isLeArgsOffsetPlus(int len, int off, int plus) {
            return len < plus + off;
        }

        @Specialization(guards = {"args.length == argsOffset"})
        @SuppressWarnings("unused")
        static void cached0(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset) {
        }

        @Specialization(guards = {"args.length == cachedLength", "isLeArgsOffsetPlus(cachedLength, argsOffset, 8)"}, limit = "1", replaces = "cached0")
        @ExplodeLoop
        static void cachedLoop(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached("args.length") int cachedLength,
                        @Cached HPyAsHandleNode toSulongNode) {
            for (int i = 0; i < cachedLength - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(hpyContext, args[argsOffset + i]);
            }
        }

        @Specialization(replaces = {"cached0", "cachedLoop"})
        static void uncached(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode toSulongNode) {
            int len = args.length;
            for (int i = 0; i < len - argsOffset; i++) {
                dest[destOffset + i] = toSulongNode.execute(hpyContext, args[argsOffset + i]);
            }
        }
    }

    @GenerateUncached
    abstract static class HPyLongFromLong extends Node {
        public abstract Object execute(int value, boolean signed);

        public abstract Object execute(long value, boolean signed);

        public abstract Object execute(Object value, boolean signed);

        @Specialization(guards = "signed")
        Object doSignedInt(int n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "!signed")
        Object doUnsignedInt(int n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            if (n < 0) {
                return toSulongNode.execute(n & 0xFFFFFFFFL);
            }
            return toSulongNode.execute(n);
        }

        @Specialization(guards = "signed")
        Object doSignedLong(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"!signed", "n >= 0"})
        Object doUnsignedLongPositive(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode) {
            return toSulongNode.execute(n);
        }

        @Specialization(guards = {"!signed", "n < 0"})
        Object doUnsignedLongNegative(long n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return toSulongNode.execute(factory.createInt(convertToBigInteger(n)));
        }

        @TruffleBoundary
        private static BigInteger convertToBigInteger(long n) {
            return BigInteger.valueOf(n).add(BigInteger.ONE.shiftLeft(Long.SIZE));
        }

        @Specialization
        Object doPointer(PythonNativeObject n, @SuppressWarnings("unused") boolean signed,
                        @Cached HPyAsHandleNode toSulongNode,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return toSulongNode.execute(factory.createNativeVoidPtr(n.getPtr()));
        }
    }

}
