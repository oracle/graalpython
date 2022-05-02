/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativePointer;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;

public class CApiTransitions {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(CApiTransitions.class);

    // transfer: steal or borrow reference

    public static final class HandleContext {

        public final HashMap<Long, IdReference<?>> nativeLookup = new HashMap<>();
        public final ArrayList<PythonObjectReference> nativeHandles = new ArrayList<>();

        public final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        boolean referenceQueuePollActive = false;

    }

    private static HandleContext getContext() {
        return PythonContext.get(null).nativeContext;
    }

    public abstract static class IdReference<T> extends WeakReference<T> {

        public IdReference(T referent) {
            super(referent, getContext().referenceQueue);
        }
    }

    public static final class PythonObjectReference extends IdReference<PythonNativeWrapper> {

        /**
         * This reference forces the object to remain alive, and can be set to null when the
         * refcount falls to {@link PythonNativeWrapper#MANAGED_REFCNT}.
         */
        PythonNativeWrapper strongReference;
        private final long pointer;

        public PythonObjectReference(PythonNativeWrapper referent, long pointer) {
            super(referent);
            this.pointer = pointer;
            this.strongReference = referent.getRefCount() > PythonNativeWrapper.MANAGED_REFCNT ? referent : null;
            LOGGER.finer(() -> PythonUtils.formatJString("new %s PythonObjectReference to %s", (strongReference == null ? "weak" : "strong"), referent));
            referent.ref = this;
        }

        @Override
        public String toString() {
            return "PythonObjectReference<" + (strongReference == null ? "" : "strong,") + Long.toHexString(pointer) + ">";
        }
    }

    public static final class NativeObjectReference extends IdReference<PythonAbstractNativeObject> {

        final Object object;
        final long pointer;

        public NativeObjectReference(PythonAbstractNativeObject referent, long pointer) {
            super(referent);
            this.object = referent.object;
            this.pointer = pointer;
            referent.ref = this;
            assert (pointer & 7) == 0;
            LOGGER.finer(() -> PythonUtils.formatJString("new NativeObjectReference reference to %s", referent));
        }

        @Override
        public String toString() {
            return "NativeObjectReference<" + (get() == null ? "freed," : "") + Long.toHexString(pointer) + ">";
        }
    }

    @TruffleBoundary
    public static void pollReferenceQueue() {
        HandleContext context = getContext();
        if (!context.referenceQueuePollActive) {
            ReferenceQueue<Object> queue = context.referenceQueue;
            int count = 0;
            while (true) {
                Object entry = queue.poll();
                if (entry == null) {
                    if (count > 0) {
                        assert context.referenceQueuePollActive;
                        context.referenceQueuePollActive = false;
                        LOGGER.fine("collected " + count + " references from native reference queue");
                    }
                    return;
                }
                if (count == 0) {
                    assert !context.referenceQueuePollActive;
                    context.referenceQueuePollActive = true;
                } else {
                    assert context.referenceQueuePollActive;
                }
                count++;
                if (entry instanceof PythonObjectReference) {
                    PythonObjectReference reference = (PythonObjectReference) entry;
                    LOGGER.finer(() -> PythonUtils.formatJString("releasing PythonObjectReference %s", reference));

                    if (HandleTester.pointsToPyHandleSpace(reference.pointer)) {
                        int index = (int) (reference.pointer - HandleFactory.HANDLE_BASE);
                        assert context.nativeHandles.get(index) != null;
                        context.nativeHandles.set(index, null);
                    } else {
                        assert context.nativeLookup.containsKey(reference.pointer) : Long.toHexString(reference.pointer);
                        context.nativeLookup.remove(reference.pointer);
                    }
                } else {
                    NativeObjectReference reference = (NativeObjectReference) entry;
                    LOGGER.finer(() -> PythonUtils.formatJString("releasing NativeObjectReference %s", reference));
                    context.nativeLookup.remove(reference.pointer);
                    Object object = reference.object;
                    if (LIB.isPointer(object)) {
                        try {
                            object = LIB.asPointer(object);
                        } catch (UnsupportedMessageException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    }
                    PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_SUBREF, object, PythonNativeWrapper.MANAGED_REFCNT);
                }
            }
        }
    }

    @TruffleBoundary
    public static Object nativeCharToJava(Object value) {
        log(value);
        assert !(value instanceof Long);
        if (value instanceof String) {
            return logResult(PythonUtils.toTruffleStringUncached((String) value));
        } else if (value instanceof TruffleString) {
            return logResult(value);
        }
        if (LIB.isPointer(value)) {
            long pointer;
            try {
                pointer = LIB.asPointer(value);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (HandleTester.pointsToPyHandleSpace(pointer)) {
                Object obj = HandleResolver.resolve(pointer);
                if (obj instanceof PythonNativeWrapper) {
                    return logResult(((PythonNativeWrapper) obj).getDelegate());
                }
            } else {
                IdReference<?> lookup = getContext().nativeLookup.get(pointer);
                if (lookup != null) {
                    Object obj = lookup.get();
                    if (obj instanceof PythonAbstractNativeObject) {
                        return logResult(obj);
                    } else {
                        return logResult(((PythonNativeWrapper) value).getDelegate());
                    }
                }
            }
        }
        FromCharPointerNode fromCharPointerNode = FromCharPointerNodeGen.getUncached();
        return logResult(fromCharPointerNode.execute(value));
    }

    @TruffleBoundary
    public static Object lookupNative(long pointer) {
        log(pointer);
        if (getContext().nativeLookup.containsKey(pointer)) {
            return logResult(getContext().nativeLookup.get(pointer).get());
        }
        return logResult(null);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleReleaser implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary lib) {
            assert args.length == 1;
            Object arg = args[0];
            if (arg instanceof PythonNativeWrapper || arg instanceof PythonAbstractObject) {
                return 0;
            }
            long pointer;
            if (arg instanceof Long) {
                pointer = (long) arg;
            } else {
                if (!lib.isPointer(arg)) {
                    return 0;
                }
                try {
                    pointer = lib.asPointer(arg);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            assert HandleTester.pointsToPyHandleSpace(pointer);
            release(pointer);
            return 0;
        }

        public static void release(long pointer) {
            LOGGER.finer(() -> PythonUtils.formatJString("releasing handle %016x\n", pointer));
            // TODO: release handle
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleTester implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary lib) {
            assert args.length == 1;
            Object arg = args[0];
            assert !(arg instanceof PythonAbstractObject);
            if (arg instanceof PythonNativeWrapper) {
                return 1;
            }
            long pointer;
            if (arg instanceof Long) {
                pointer = (long) arg;
            } else {
                if (!lib.isPointer(arg)) {
                    return 0;
                }
                try {
                    pointer = lib.asPointer(arg);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return pointsToPyHandleSpace(pointer) ? 1 : 0;
        }

        public static boolean pointsToPyHandleSpace(long pointer) {
            return (pointer & HandleFactory.HANDLE_BASE) != 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleResolver implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            long pointer = (long) args[0];
            return resolve(pointer);
        }

        public static PythonNativeWrapper resolve(long pointer) {
            PythonNativeWrapper wrapper = getContext().nativeHandles.get((int) (pointer - HandleFactory.HANDLE_BASE)).get();
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            incRef(wrapper, 1);
            return wrapper;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleResolverStealing implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            long pointer = (long) args[0];
            return resolve(pointer);
        }

        public static PythonNativeWrapper resolve(long pointer) {
            PythonNativeWrapper wrapper = getContext().nativeHandles.get((int) (pointer - HandleFactory.HANDLE_BASE)).get();
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            return wrapper;
        }
    }

    public static final class HandleFactory {

        public static final long HANDLE_BASE = 0x8000_0000_0000_0000L;

        public static long create(PythonNativeWrapper wrapper) {
            assert !(wrapper instanceof TruffleObjectNativeWrapper);
            int idx = getContext().nativeHandles.size();
            long pointer = HANDLE_BASE + idx;
            getContext().nativeHandles.add(new PythonObjectReference(wrapper, pointer));
            return pointer;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PointerContainer implements TruffleObject {

        private final long pointer;

        public PointerContainer(long pointer) {
            this.pointer = pointer;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isPointer() {
            return true;
        }

        @ExportMessage
        public long asPointer() {
            return pointer;
        }

        @ExportMessage
        public void toNative() {
            // nothing to do
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("<%016x>", pointer);
        }
    }

    @TruffleBoundary
    public static void firstToNative(PythonNativeWrapper obj) {
        log(obj);
        assert !obj.isNative();
        obj.setNativePointer(logResult(HandleFactory.create(obj)));
    }

    @TruffleBoundary
    public static void firstToNative(PythonNativeWrapper obj, long ptr) {
        logVoid(obj, ptr);
        obj.setNativePointer(ptr);
        CApiTransitions.getContext().nativeLookup.put(ptr, new PythonObjectReference(obj, ptr));
    }

    // logging

    private static void log(Object... args) {
        if (LOGGER.isLoggable(Level.FINER)) {
            CompilerAsserts.neverPartOfCompilation();
            StackTraceElement element = new RuntimeException().getStackTrace()[1];
            StringBuilder str = new StringBuilder();
            String className = element.getClassName();
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf('.') + 1);
            }
            str.append(className).append(".").append(element.getMethodName()).append(": ");
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                str.append(i == 0 ? "" : ", ");
                format(str, a);
            }
            LOGGER.finer(str.toString());
        }
    }

    private static void logVoid(Object... args) {
        log(args);
    }

    private static void format(StringBuilder str, Object a) {
        if (a instanceof Long) {
            str.append(String.format("0x%x", (long) a));
        } else if (a instanceof Integer) {
            str.append(String.format("0x%x", (int) a));
        } else {
            str.append(a);
        }
    }

    private static <T> T logResult(T value) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            CompilerAsserts.neverPartOfCompilation();
            StackTraceElement element = new RuntimeException().getStackTrace()[1];
            StringBuilder str = new StringBuilder("    ==> <").append(element.getLineNumber()).append("> ");
            format(str, value);
            LOGGER.finest(str.toString());
        }
        return value;
    }

    @TruffleBoundary
    public static long incRef(PythonNativeWrapper nativeWrapper, long value) {
        assert value > 0;
        long refCount = nativeWrapper.getRefCount();
        nativeWrapper.setRefCount(refCount + value);
        assert refCount >= PythonNativeWrapper.MANAGED_REFCNT;
        if (refCount == PythonNativeWrapper.MANAGED_REFCNT && nativeWrapper.ref != null) {
            nativeWrapper.ref.strongReference = nativeWrapper;
        }
        return refCount;
    }

    @TruffleBoundary
    public static long decRef(PythonNativeWrapper nativeWrapper, long value) {
        assert value > 0;
        long refCount = nativeWrapper.getRefCount() - value;
        nativeWrapper.setRefCount(refCount);
        assert refCount >= PythonNativeWrapper.MANAGED_REFCNT;
        if (refCount == PythonNativeWrapper.MANAGED_REFCNT && nativeWrapper.ref != null) {
            nativeWrapper.ref.strongReference = null;
        }
        return refCount;
    }

    @TruffleBoundary
    public static void setRefCount(PythonNativeWrapper nativeWrapper, long value) {
        long refCnt = nativeWrapper.getRefCount();
        if (value < refCnt) {
            decRef(nativeWrapper, refCnt - value);
        } else if (value > refCnt) {
            incRef(nativeWrapper, value - refCnt);
        }
    }

    private static final InteropLibrary LIB = InteropLibrary.getUncached();

    @TruffleBoundary
    public static Object pythonToNative(Object obj, boolean transfer) {
        pollReferenceQueue();
        if (obj instanceof PythonAbstractNativeObject) {
            PythonAbstractNativeObject nativeObject = (PythonAbstractNativeObject) obj;
            if (transfer) {
                PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_ADDREF, nativeObject.object, 1);
            }
            return nativeObject.getPtr();
        } else if (obj instanceof PythonNativePointer) {
            return ((PythonNativePointer) obj).getPtr();
        } else if (obj instanceof DescriptorDeleteMarker) {
            return PythonContext.get(null).getNativeNull().getPtr();
        } else {
            PythonNativeWrapper wrapper = GetNativeWrapperNodeGen.getUncached().execute(obj);
            if (transfer) {
                // native part needs to decRef to release
                incRef(wrapper, 1);
            }
            return wrapper;
        }
    }

    @TruffleBoundary
    public static Object nativeToPython(Object obj, boolean transfer) {
        pollReferenceQueue();
        PythonNativeWrapper wrapper;

        if (obj instanceof PythonNativeWrapper) {
            wrapper = (PythonNativeWrapper) obj;
        } else {
            assert !(obj instanceof PythonAbstractNativeObject);
            long pointer;
            if (obj instanceof Long) {
                pointer = (long) obj;
            } else {
                if (!LIB.isPointer(obj)) {
                    throw CompilerDirectives.shouldNotReachHere("not a pointer: " + obj);
                }
                try {
                    pointer = LIB.asPointer(obj);
                } catch (final UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            if (pointer == 0) {
                return PNone.NO_VALUE;
            }
            if (HandleTester.pointsToPyHandleSpace(pointer)) {
                PythonObjectReference reference = getContext().nativeHandles.get((int) (pointer - HandleFactory.HANDLE_BASE));
                if (reference == null) {
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                wrapper = reference.get();
                if (wrapper == null) {
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
            } else {
                IdReference<?> lookup = getContext().nativeLookup.get(pointer);
                if (lookup != null) {
                    Object ref = lookup.get();
                    if (ref == null) {
                        LOGGER.fine(() -> "re-creating collected PythonAbstractNativeObject reference" + Long.toHexString(pointer));
                        return createAbstractNativeObject(obj, transfer, pointer);
                    }
                    if (ref instanceof PythonNativeWrapper) {
                        wrapper = (PythonNativeWrapper) ref;
                    } else {
                        PythonAbstractNativeObject result = (PythonAbstractNativeObject) ref;
                        if (transfer) {
                            PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_ADDREF, obj, -1);
                        }
                        return result;
                    }
                } else {
                    return createAbstractNativeObject(obj, transfer, pointer);
                }
            }
        }
        if (transfer) {
            assert wrapper.getRefCount() >= PythonNativeWrapper.MANAGED_REFCNT;
            decRef(wrapper, 1);
        }
        if (wrapper instanceof PrimitiveNativeWrapper) {
            PrimitiveNativeWrapper primitive = (PrimitiveNativeWrapper) wrapper;
            if (primitive.isBool()) {
                return primitive.getBool();
            } else if (primitive.isInt()) {
                return primitive.getInt();
            } else if (primitive.isLong()) {
                return primitive.getLong();
            } else if (primitive.isByte()) {
                return primitive.getByte();
            } else if (primitive.isDouble()) {
                return primitive.getDouble();
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else {
            return wrapper.getDelegate();
        }
    }

    private static Object createAbstractNativeObject(Object obj, boolean transfer, long pointer) {
        assert isBackendPointerObject(obj) : obj.getClass();

        PythonAbstractNativeObject result = new PythonAbstractNativeObject(obj);
        NativeObjectReference ref = new NativeObjectReference(result, pointer);
        getContext().nativeLookup.put(pointer, ref);

        long refCntDelta = PythonNativeWrapper.MANAGED_REFCNT - (transfer ? 1 : 0);
        PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_ADDREF, obj, refCntDelta);
        return result;
    }

    @TruffleBoundary
    public static boolean isBackendPointerObject(Object obj) {
        return obj != null && (obj.getClass().toString().contains("LLVMPointerImpl") || obj.getClass().toString().contains("NFIPointer"));
    }

    @TruffleBoundary
    public static PythonNativeWrapper nativeToPythonWrapper(Object obj) {
        if (obj instanceof PythonNativeWrapper) {
            return (PythonNativeWrapper) obj;
        } else if (obj instanceof PythonAbstractNativeObject) {
            throw CompilerDirectives.shouldNotReachHere();
        } else {
            long pointer;
            if (obj instanceof Long) {
                pointer = (long) obj;
            } else {
                if (!LIB.isPointer(obj)) {
                    throw CompilerDirectives.shouldNotReachHere("not a pointer: " + obj);
                }
                try {
                    pointer = LIB.asPointer(obj);
                } catch (final UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            if (pointer == 0) {
                return null;
            }
            PythonNativeWrapper wrapper;
            if (HandleTester.pointsToPyHandleSpace(pointer)) {
                wrapper = getContext().nativeHandles.get((int) (pointer - HandleFactory.HANDLE_BASE)).get();
                if (wrapper == null) {
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
                return wrapper;
            } else {
                IdReference<?> lookup = getContext().nativeLookup.get(pointer);
                if (lookup != null) {
                    Object ref = lookup.get();
                    if (ref == null) {
                        throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                    }
                    if (ref instanceof PythonNativeWrapper) {
                        return (PythonNativeWrapper) ref;
                    }
                }
            }
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PythonToNative implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            return pythonToNative(args[0], false);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PythonToNativeTransfer implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            return pythonToNative(args[0], true);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class NativeToPython implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            return nativeToPython(args[0], false);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class NativeToPythonTransfer implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            return nativeToPython(args[0], true);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class JavaStringToTruffleString implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args,
                        @Cached FromJavaStringNode fromJavaString) {
            assert args.length == 1;
            assert args[0] instanceof String;
            return fromJavaString.execute((String) args[0], PythonUtils.TS_ENCODING);
        }
    }
}
