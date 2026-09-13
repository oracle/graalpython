/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.annotations.NativeSimpleType.POINTER;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT32;
import static com.oracle.graal.python.annotations.NativeSimpleType.VOID;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibrary;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibraryLoadException;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;

/**
 * Support for reading hardware performance counters via PAPI (Performance API,
 * {@code libpapi.so}) from a running GraalPy interpreter.
 * <p>
 * This is deliberately scoped to native-image builds only: PAPI reads hardware performance
 * counters of the calling OS thread, and that only has a stable, meaningful interpretation when
 * GraalPy itself is the native process being measured (i.e. under {@code native-image}), not when
 * it runs as a guest language hosted inside a JVM. {@link #createNative(PythonContext)} returns an
 * instance with {@link #isAvailable()} {@code == false} unless running in image code.
 * <p>
 * This class only supports a single, global event set at a time (matching the intended usage as a
 * simple {@code papi_start()}/{@code papi_read()}/{@code papi_stop()} API driven directly from
 * Python source, analogous to {@code time.perf_counter()}). It is not thread-safe: PAPI itself
 * requires separate event sets per OS thread for multithreaded counting, which is out of scope
 * for now.
 */
public final class NativePapiSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativePapiSupport.class);

    /** {@code libpapi.so}, resolved via the default dynamic linker search path (it is a
     * system-installed library, not one bundled by GraalPy). */
    private static final String LIBRARY_NAME = "libpapi.so";

    public static final int PAPI_OK = 0;
    public static final int PAPI_NULL = -1;

    /**
     * {@code PAPI_VER_CURRENT} as computed by {@code PAPI_VERSION_NUMBER(7, 2, 0, 0) & 0xffff0000}
     * in {@code /usr/include/papi.h} for PAPI 7.2.x. {@code PAPI_library_init} rejects a version it
     * considers ABI-incompatible, so this needs to be kept in sync with the PAPI major.minor
     * actually installed on the target system.
     */
    private static final int PAPI_VER_CURRENT = 0x07020000;

    /** Pseudo error codes for failures detected on the Java side rather than returned by PAPI
     * itself. Real PAPI error codes are always negative, so these can never collide with one. */
    private static final int JAVA_NOT_AVAILABLE = 1;
    private static final int JAVA_NOT_RUNNING = 2;
    private static final int JAVA_ALREADY_RUNNING = 3;

    abstract static class PapiNativeFunctions {
        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int PAPI_library_init(int version);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int PAPI_create_eventset(long eventSetPtr);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int PAPI_add_named_event(int eventSet, long eventNamePtr);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int PAPI_start(int eventSet);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int PAPI_read(int eventSet, long valuesPtr);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32, POINTER})
        abstract int PAPI_stop(int eventSet, long valuesPtr);

        @DowncallSignature(returnType = SINT32, argumentTypes = {SINT32})
        abstract int PAPI_cleanup_eventset(int eventSet);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int PAPI_destroy_eventset(long eventSetPtr);

        @DowncallSignature(returnType = POINTER, argumentTypes = {SINT32})
        abstract long PAPI_strerror(int errorCode);

        @DowncallSignature(returnType = VOID)
        abstract void PAPI_shutdown();

        static NativeLibrary loadNativeLibrary(PythonContext context) {
            try {
                return context.ensureNativeContext().loadLibrary(LIBRARY_NAME, PosixConstants.RTLD_LOCAL.value);
            } catch (NativeLibraryLoadException e) {
                throw new UnsupportedOperationException(String.format("Could not load PAPI support library '%s'.", LIBRARY_NAME), e);
            }
        }
    }

    /** Thrown for both real PAPI error codes (see {@link #getErrorCode()}) and for Java-side
     * usage errors (calling {@code read()}/{@code stop()} before {@code start()}, and the like). */
    public static final class PapiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int errorCode;

        PapiException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    /**
     * One recorded call-boundary event: a PAPI snapshot taken exactly at a Python-level function
     * call's entry or exit. Deliberately just an append-only flat record rather than something
     * that tracks caller/callee nesting itself: any post-processing needs to be done manually.
     */
    public record CallEvent(boolean isEnter, String name, long[] counters) {
    }

    private final PythonContext pythonContext;
    private final PapiNativeFunctions nativeFunctions;

    /** Lazily computed by {@link #isAvailable()} -- {@code null} means "not probed yet". This
     * must NOT be probed eagerly at context construction: {@code createNative} also runs during
     * native-image build-time context preinitialization, and actually touching native code
     * (dlopen, downcalls) is not allowed at that point (only at real runtime, once the image is
     * actually running). See {@link PythonContext#ensureNativeContext()}. */
    private Boolean available;

    private int eventSet = PAPI_NULL;
    private int numEvents;
    /** Scratch buffer for PAPI_read()/PAPI_stop()'s output values, sized to numEvents longs and
     * reused for the life of one start()..stop() session. */
    private long valuesPtr = NativeMemory.NULLPTR;

    /** Whether the per-call bytecode instrumentation (see PBytecodeDSLRootNode's
     * PapiRecordCallEnter op, and the papi_call_* builtins) should currently append to
     * {@link #callLog}. Read on every instrumented call/return once instrumentation has been
     * enabled at all, so this stays a plain field rather than something heavier. */
    private volatile boolean callRecording;
    private List<CallEvent> callLog = Collections.emptyList();

    private NativePapiSupport(PythonContext context, PapiNativeFunctions nativeFunctions) {
        this.pythonContext = context;
        this.nativeFunctions = nativeFunctions;
    }

    public static NativePapiSupport createNative(PythonContext context) {
        // Cheap and native-call-free, so it's safe to do eagerly, including at build time: just
        // wires up the (still unresolved) downcall handles, matching NativeZlibSupport's pattern.
        PapiNativeFunctions functions = ImageInfo.inImageCode() ? new PapiNativeFunctionsGen(context) : null;
        return new NativePapiSupport(context, functions);
    }

    @TruffleBoundary
    public boolean isAvailable() {
        if (available == null) {
            available = probeAvailability();
        }
        return available;
    }

    private boolean probeAvailability() {
        if (!ImageInfo.inImageCode() || nativeFunctions == null) {
            return false;
        }
        try {
            return nativeFunctions.PAPI_library_init(PAPI_VER_CURRENT) == PAPI_VER_CURRENT;
        } catch (UnsupportedOperationException e) {
            // libpapi.so not installed, or not found on the default library search path
            LOGGER.fine("PAPI support disabled: " + e.getMessage());
            return false;
        }
    }

    public boolean isRunning() {
        return eventSet != PAPI_NULL;
    }

    /**
     * Creates an event set for the given PAPI event names (e.g. {@code "PAPI_TOT_CYC"},
     * {@code "PAPI_TOT_INS"}) and starts counting. Counters run cumulatively until {@link #stop()}
     * -- use {@link #read()} to take non-destructive checkpoints in between (callers can diff
     * successive read() results themselves to get a delta since their own last checkpoint).
     */
    @TruffleBoundary
    public void start(String[] eventNames) {
        ensureAvailable();
        if (isRunning()) {
            throw new PapiException(JAVA_ALREADY_RUNNING, "PAPI counters are already running; call stop() first");
        }
        int newEventSet = createEventSet();
        try {
            for (String name : eventNames) {
                addEvent(newEventSet, name);
            }
            checkError(nativeFunctions.PAPI_start(newEventSet));
        } catch (PapiException e) {
            destroyEventSetQuietly(newEventSet);
            throw e;
        }
        eventSet = newEventSet;
        numEvents = eventNames.length;
        valuesPtr = NativeMemory.mallocLongArray(numEvents);
    }

    /** Cumulative counter values since {@link #start(String[])}; does not stop or reset counting. */
    @TruffleBoundary
    public long[] read() {
        ensureRunning();
        checkError(nativeFunctions.PAPI_read(eventSet, valuesPtr));
        return NativeMemory.readLongArrayElements(valuesPtr, 0, numEvents);
    }

    /** Stops counting, returns the final cumulative counter values, and releases the event set. */
    @TruffleBoundary
    public long[] stop() {
        ensureRunning();
        long[] totals;
        try {
            checkError(nativeFunctions.PAPI_stop(eventSet, valuesPtr));
            totals = NativeMemory.readLongArrayElements(valuesPtr, 0, numEvents);
        } finally {
            // Tear down unconditionally and "at all costs", so that we won't call PAPI_read in invalid state
            NativeMemory.free(valuesPtr);
            valuesPtr = NativeMemory.NULLPTR;
            destroyEventSetQuietly(eventSet);
            eventSet = PAPI_NULL;
            numEvents = 0;
            callRecording = false;
        }
        return totals;
    }

    /**
     * Starts per-call recording: from this point on, every Python-level function call/return
     * (woven in by PBytecodeDSLRootNode's PapiRecordCallEnter instrumentation once enabled) appends
     * a {@link CallEvent} snapshot to the call log instead of requiring the script to call
     * {@link #read()} itself at each call site. Requires {@link #start(String[])} to already be
     * running -- this only toggles *recording of* the counters that are already counting, it
     * doesn't start/stop PAPI itself.
     */
    @TruffleBoundary
    public void startCallRecording() {
        startCallRecording(0);
    }

    /**
     * Same as {@link #startCallRecording()}, but pre-sizes the call log to {@code expectedEvents}
     * entries. Recording appends to this list on every instrumented call/return, so letting an
     * ArrayList grow on demand means occasional resize-and-copy pauses land in the middle of
     * whatever's being measured -- pre-allocating when you have a rough idea of the call volume
     * avoids that source of noise. {@code expectedEvents <= 0} behaves like the no-arg overload
     * (grow on demand, from an empty list).
     */
    @TruffleBoundary
    public void startCallRecording(int expectedEvents) {
        ensureRunning();
        callLog = expectedEvents > 0 ? new ArrayList<>(expectedEvents) : new ArrayList<>();
        callRecording = true;
    }

    @TruffleBoundary
    public void stopCallRecording() {
        callRecording = false;
    }

    public boolean isCallRecording() {
        return callRecording;
    }

    /** The recorded call/return events since the last {@link #startCallRecording()}, in
     * chronological order. Read-only snapshot; further recording appends to a fresh list. */
    @TruffleBoundary
    public List<CallEvent> getCallLog() {
        return Collections.unmodifiableList(callLog);
    }

    /**
     * Appends a call-enter/exit snapshot to the call log. Called from the interpreter's per-call
     * instrumentation hook, so this is deliberately minimal -- no ensureRunning()/checkError()
     * ceremony, since {@link #isCallRecording()} can only be true while an event set is running
     * (see {@link #startCallRecording()} and how {@link #stop()} clears {@code callRecording}).
     * Silently does nothing on a read failure rather than throwing out of the middle of unrelated
     * Python code.
     */
    private void recordCallEvent(boolean isEnter, String name) {
        if (nativeFunctions.PAPI_read(eventSet, valuesPtr) != PAPI_OK) {
            return;
        }
        long[] values = NativeMemory.readLongArrayElements(valuesPtr, 0, numEvents);
        callLog.add(new CallEvent(isEnter, name, values));
    }

    @TruffleBoundary
    public void recordCallEnter(String name) {
        recordCallEvent(true, name);
    }

    @TruffleBoundary
    public void recordCallExit(String name) {
        recordCallEvent(false, name);
    }

    private int createEventSet() {
        long ptr = NativeMemory.mallocIntArray(1);
        try {
            NativeMemory.writeInt(ptr, PAPI_NULL);
            checkError(nativeFunctions.PAPI_create_eventset(ptr));
            return NativeMemory.readInt(ptr);
        } finally {
            NativeMemory.free(ptr);
        }
    }

    private void addEvent(int set, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        long namePtr = NativeMemory.copyToNativeZeroTerminatedByteArray(bytes, 0, bytes.length);
        try {
            checkError(nativeFunctions.PAPI_add_named_event(set, namePtr));
        } finally {
            NativeMemory.free(namePtr);
        }
    }

    /** Best-effort cleanup used on error paths -- deliberately does not throw. */
    private void destroyEventSetQuietly(int set) {
        long ptr = NativeMemory.mallocIntArray(1);
        try {
            NativeMemory.writeInt(ptr, set);
            nativeFunctions.PAPI_cleanup_eventset(set);
            nativeFunctions.PAPI_destroy_eventset(ptr);
        } catch (RuntimeException e) {
            LOGGER.fine("Error while releasing PAPI event set: " + e.getMessage());
        } finally {
            NativeMemory.free(ptr);
        }
    }

    private void ensureAvailable() {
        if (!isAvailable()) {
            throw new PapiException(JAVA_NOT_AVAILABLE, "PAPI is not available (native-image only, and requires libpapi.so to be installed)");
        }
    }

    private void ensureRunning() {
        ensureAvailable();
        if (!isRunning()) {
            throw new PapiException(JAVA_NOT_RUNNING, "PAPI counters are not running; call start() first");
        }
    }

    private void checkError(int code) {
        if (code != PAPI_OK) {
            throw new PapiException(code, strerror(code));
        }
    }

    private String strerror(int code) {
        long msgPtr = nativeFunctions.PAPI_strerror(code);
        if (msgPtr == NativeMemory.NULLPTR) {
            return "PAPI error " + code;
        }
        return ZeroTerminatedUtf8ToTruffleStringNode.executeUncached(msgPtr).toJavaStringUncached();
    }
}
