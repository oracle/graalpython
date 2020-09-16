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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyMethKeywordsRoot;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyMethNoargsRoot;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyMethORoot;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyMethVarargsRoot;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

public class GraalHPyNodes {

    @GenerateUncached
    abstract static class ImportHPySymbolNode extends PNodeWithContext {

        public abstract Object execute(GraalHPyContext context, String name);

        @Specialization(guards = "cachedName == name", limit = "1", assumptions = "singleContextAssumption()")
        static Object doReceiverCachedIdentity(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(guards = "cachedName.equals(name)", limit = "1", assumptions = "singleContextAssumption()", replaces = "doReceiverCachedIdentity")
        static Object doReceiverCached(@SuppressWarnings("unused") GraalHPyContext context, @SuppressWarnings("unused") String name,
                        @Cached("name") @SuppressWarnings("unused") String cachedName,
                        @Cached("importHPySymbolUncached(context, name)") Object sym) {
            return sym;
        }

        @Specialization(replaces = {"doReceiverCached", "doReceiverCachedIdentity"})
        static Object doGeneric(GraalHPyContext context, String name,
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

    /**
     * Use this node to transform an exception to native if a Python exception was thrown during an
     * upcall and before returning to native code. This node will correctly link to the current
     * frame using the frame reference and tries to avoid any materialization of the frame. The
     * exception is then registered in the native context as the current exception.
     */
    @GenerateUncached
    public abstract static class HPyTransformExceptionToNativeNode extends Node {

        public abstract void execute(Frame frame, GraalHPyContext nativeContext, PException e);

        public final void execute(GraalHPyContext nativeContext, PException e) {
            execute(null, nativeContext, e);
        }

        @Specialization
        static void setCurrentException(Frame frame, GraalHPyContext nativeContext, PException e,
                        @Cached GetCurrentFrameRef getCurrentFrameRef) {
            // TODO connect f_back
            getCurrentFrameRef.execute(frame).markAsEscaped();
            nativeContext.setCurrentException(e);
        }
    }

    @GenerateUncached
    public abstract static class HPyRaiseNode extends Node {

        public final int raiseInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raise(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(frame, nativeContext, errorValue, errType, format, arguments);
        }

        public final int raiseIntWithoutFrame(GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object... arguments) {
            return executeInt(null, nativeContext, errorValue, errType, format, arguments);
        }

        public final Object raiseWithoutFrame(GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object... arguments) {
            return execute(null, nativeContext, errorValue, errType, format, arguments);
        }

        public abstract Object execute(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object[] arguments);

        public abstract int executeInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object[] arguments);

        @Specialization
        static int doInt(Frame frame, GraalHPyContext nativeContext, int errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
        }

        @Specialization
        static Object doObject(Frame frame, GraalHPyContext nativeContext, Object errorValue, Object errType, String format, Object[] arguments,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("transformExceptionToNativeNode") @Cached HPyTransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                throw raiseNode.execute(errType, PNone.NO_VALUE, format, arguments);
            } catch (PException p) {
                transformExceptionToNativeNode.execute(frame, nativeContext, p);
            }
            return errorValue;
        }
    }

    /**
     * <pre>
     *     typedef struct {
     *         const char *name;             // The name of the built-in function/method
     *         const char *doc;              // The __doc__ attribute, or NULL
     *         void *impl;                   // Function pointer to the implementation
     *         void *cpy_trampoline;         // Used by CPython to call impl
     *         HPyFunc_Signature signature;  // Indicates impl's expected the signature
     *     } HPyMeth;
     * </pre>
     */
    @GenerateUncached
    public abstract static class HPyAddFunctionNode extends PNodeWithContext {

        public abstract PBuiltinFunction execute(GraalHPyContext context, Object methodDef);

        @Specialization(limit = "1")
        static PBuiltinFunction doIt(GraalHPyContext context, Object methodDef,
                        @CachedLanguage PythonLanguage language,
                        @CachedLibrary("methodDef") InteropLibrary interopLibrary,
                        @CachedLibrary(limit = "2") InteropLibrary resultLib,
                        @Cached PCallHPyFunction callGetNameNode,
                        @Cached PCallHPyFunction callGetDocNode,
                        @Cached CastToJavaIntLossyNode castToJavaIntNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PythonObjectFactory factory,
                        @Cached WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                        @Cached PRaiseNode raiseNode) {
            assert checkLayout(methodDef);
            assert getKind(methodDef, interopLibrary, castToJavaIntNode) == GraalHPyDef.HPY_DEF_KIND_METH;

            String methodName = castToJavaStringNode.execute(callGetNameNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_GET_ML_NAME, methodDef));

            // note: 'ml_doc' may be NULL; in this case, we would store 'None'
            Object methodDoc = PNone.NONE;
            try {
                if (!resultLib.isNull(interopLibrary.readMember(methodDef, GraalHPyNativeSymbols.GRAAL_HPY_GET_ML_DOC))) {
                    methodDoc = castToJavaStringNode.execute(callGetDocNode.call(context, GraalHPyNativeSymbols.GRAAL_HPY_GET_ML_DOC, methodDef));
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }

            Object methodSignatureObj;
            int signature;
            Object methodFunctionPointer;
            try {
                methodSignatureObj = interopLibrary.readMember(methodDef, "signature");
                if (!resultLib.fitsInInt(methodSignatureObj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_flags of %s is not an integer", methodName);
                }
                signature = resultLib.asInt(methodSignatureObj);

                methodFunctionPointer = interopLibrary.readMember(methodDef, "impl");
                if (!resultLib.isExecutable(methodFunctionPointer)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(PythonBuiltinClassType.SystemError, "ml_meth of %s is not callable", methodName);
                }
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.SystemError, "Invalid struct member '%s'", e.getUnknownIdentifier());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, "Cannot access struct member 'ml_flags' or 'ml_meth'.");
            }

            PRootNode rootNode = createHPyWrapperRootNode(language, signature, methodName, methodFunctionPointer);
            PBuiltinFunction function = createWrapperFunction(factory, methodName, rootNode);

            // write doc string; we need to directly write to the storage otherwise it is
            // disallowed writing to builtin types.
            writeAttributeToDynamicObjectNode.execute(function.getStorage(), SpecialAttributeNames.__DOC__, methodDoc);

            return function;
        }

        @TruffleBoundary
        private static boolean checkLayout(Object methodDef) {
            String[] members = new String[]{"name", "doc", "impl", "cpy_trampoline", "signature"};
            InteropLibrary lib = InteropLibrary.getUncached(methodDef);
            for (String member : members) {
                if (!lib.isMemberReadable(methodDef, member)) {
                    return false;
                }
            }
            return true;
        }

        private static int getKind(Object methodDef, InteropLibrary interopLibrary, CastToJavaIntLossyNode castToJavaIntNode) {
            try {
                return castToJavaIntNode.execute(interopLibrary.readMember(methodDef, "kind"));
            } catch (UnsupportedMessageException | UnknownIdentifierException | CannotCastException e) {
                return -1;
            }
        }

        @TruffleBoundary
        private static PBuiltinFunction createWrapperFunction(PythonObjectFactory factory, String name, PRootNode rootNode) {
            return factory.createBuiltinFunction(name, null, 0, PythonUtils.getOrCreateCallTarget(rootNode));
        }

        @TruffleBoundary
        private static PRootNode createHPyWrapperRootNode(PythonLanguage language, int signature, String name, Object callable) {
            switch (signature) {
                case GraalHPyDef.HPyFunc_NOARGS:
                    return new HPyMethNoargsRoot(language, name, callable);
                case GraalHPyDef.HPyFunc_O:
                    return new HPyMethORoot(language, name, callable);
                case GraalHPyDef.HPyFunc_KEYWORDS:
                    return new HPyMethKeywordsRoot(language, name, callable);
                case GraalHPyDef.HPyFunc_VARARGS:
                    return new HPyMethVarargsRoot(language, name, callable);

            }
            // TODO(fa): support remaining signatures
            if (signature <= GraalHPyDef.HPyFunc_SETTER) {
                throw new IllegalStateException("unsupported HPy method signature");
            }
            throw new IllegalStateException("illegal HPy method signature");
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
        static Object doHandle(@SuppressWarnings("unused") GraalHPyContext hpyContext, GraalHPyHandle handle) {
            return handle.getDelegate();
        }

        @Specialization
        static Object doInt(GraalHPyContext hpyContext, int handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeInt(hpyContext, handle).getDelegate();
        }

        @Specialization
        static Object doLong(GraalHPyContext hpyContext, long handle,
                        @Shared("ensureHandleNode") @Cached HPyEnsureHandleNode ensureHandleNode) {
            return ensureHandleNode.executeLong(hpyContext, handle).getDelegate();
        }

        @Specialization(replaces = "doHandle")
        static Object doObject(GraalHPyContext hpyContext, Object object,
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

    public abstract static class HPyConvertArgsToSulongNode extends PNodeWithContext {

        public abstract void executeInto(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset);
    }

    public abstract static class HPyVarargsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = args[argsOffset + 2];
        }
    }

    public abstract static class HPyKeywordsToSulongNode extends HPyConvertArgsToSulongNode {

        @Specialization
        static void doConvert(GraalHPyContext hpyContext, Object[] args, int argsOffset, Object[] dest, int destOffset,
                        @Cached HPyAsHandleNode selfAsHandleNode,
                        @Cached HPyAsHandleNode kwAsHandleNode) {
            dest[destOffset] = selfAsHandleNode.execute(hpyContext, args[argsOffset]);
            dest[destOffset + 1] = args[argsOffset + 1];
            dest[destOffset + 2] = args[argsOffset + 2];
            dest[destOffset + 3] = kwAsHandleNode.execute(hpyContext, args[argsOffset + 3]);
        }
    }

    public abstract static class HPyAllAsHandleNode extends HPyConvertArgsToSulongNode {

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
