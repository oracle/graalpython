/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createASCIIString;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.isSpace;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyBytesObject__ob_sval;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyVarObject__ob_size;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.NON_HEX_NUMBER_IN_FROMHEX;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.arrayCopyOf;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentIndex;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.ToBytesNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyByteArrayCheckNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyOSFSPathNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ComparisonOp;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class BytesNodes {

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CreateBytesNode extends Node {

        public final PBytesLike execute(Node inliningTarget, PythonObjectFactory factory, Object basedOn, byte[] bytes) {
            return execute(inliningTarget, factory, basedOn, new ByteSequenceStorage(bytes));
        }

        public abstract PBytesLike execute(Node inliningTarget, PythonObjectFactory factory, Object basedOn, SequenceStorage bytes);

        @Specialization
        static PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, SequenceStorage bytes) {
            return factory.createBytes(bytes);
        }

        @Specialization
        static PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, SequenceStorage bytes) {
            return factory.createByteArray(bytes);
        }

        @Specialization(guards = "checkBytes.execute(inliningTarget, basedOn)")
        static PBytesLike bytes(@SuppressWarnings("unused") Node inliningTarget, PythonObjectFactory factory, @SuppressWarnings("unused") Object basedOn, SequenceStorage bytes,
                        @SuppressWarnings("unused") @Shared @Cached PyBytesCheckNode checkBytes) {
            return factory.createBytes(bytes);
        }

        @Specialization(guards = "!checkBytes.execute(inliningTarget, basedOn)")
        static PBytesLike bytearray(@SuppressWarnings("unused") Node inliningTarget, PythonObjectFactory factory, @SuppressWarnings("unused") Object basedOn, SequenceStorage bytes,
                        @SuppressWarnings("unused") @Shared @Cached PyBytesCheckNode checkBytes) {
            assert PyByteArrayCheckNode.executeUncached(basedOn);
            return factory.createByteArray(bytes);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PythonOptions.class)
    public abstract static class BytesJoinNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, byte[] sep, Object iterable);

        @Specialization
        static byte[] join(VirtualFrame frame, Node inliningTarget, byte[] sep, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Cached(inline = false) GetNextNode getNextNode,
                        @Cached(inline = false) ToBytesNode toBytesNode,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            ArrayList<byte[]> parts = new ArrayList<>();
            int partsTotalSize = 0;
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            while (true) {
                try {
                    partsTotalSize += append(parts, toBytesNode.execute(frame, getNextNode.execute(frame, iterator)));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return joinArrays(sep, parts, partsTotalSize);
                }
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static int append(ArrayList<byte[]> parts, byte[] barr) {
            parts.add(barr);
            return barr.length;
        }

        @TruffleBoundary(allowInlining = true)
        private static byte[] joinArrays(byte[] sep, ArrayList<byte[]> parts, int partsTotalSize) {
            byte[] joinedBytes = new byte[Math.max(0, partsTotalSize + (parts.size() - 1) * sep.length)];
            if (parts.size() > 0) {
                int offset = 0;
                byte[] array = parts.get(0);
                PythonUtils.arraycopy(array, 0, joinedBytes, offset, array.length);
                offset += array.length;
                for (int i = 1; i < parts.size(); i++) {
                    array = parts.get(i);
                    PythonUtils.arraycopy(sep, 0, joinedBytes, offset, sep.length);
                    offset += sep.length;
                    PythonUtils.arraycopy(array, 0, joinedBytes, offset, array.length);
                    offset += array.length;
                }
            }
            return joinedBytes;
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ToBytesNode extends Node {

        private final PythonBuiltinClassType errorType;
        private final TruffleString errorMessageFormat;

        ToBytesNode(PythonBuiltinClassType errorType, TruffleString errorMessageFormat) {
            this.errorType = errorType;
            this.errorMessageFormat = errorMessageFormat;
        }

        public final byte[] execute(PBytesLike obj) {
            return execute(null, obj);
        }

        public abstract byte[] execute(VirtualFrame frame, Object obj);

        @Specialization(limit = "2")
        byte[] doBytes(PBytesLike bytes,
                        @CachedLibrary("bytes") PythonBufferAccessLibrary bufferLib) {
            return bufferLib.getCopiedByteArray(bytes);
        }

        @Specialization(limit = "3")
        @SuppressWarnings("truffle-static-method")  // TODO: arg
        byte[] doBuffer(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer;
            try {
                buffer = bufferAcquireLib.acquireReadonly(object, frame, indirectCallData);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(errorType, errorMessageFormat, object);
            }
            try {
                return bufferLib.getCopiedByteArray(buffer);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @NeverDefault
        public static ToBytesNode create() {
            return ToBytesNodeGen.create(TypeError, EXPECTED_BYTESLIKE_GOT_P);
        }

        public static ToBytesNode create(PythonBuiltinClassType errorType, TruffleString errorMessageFormat) {
            return ToBytesNodeGen.create(errorType, errorMessageFormat);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ToBytesWithoutFrameNode extends Node {

        public abstract byte[] execute(Node inliningTarget, Object object);

        @Specialization(limit = "3")
        static byte[] doBuffer(Node inliningTarget, Object object,
                        @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer;
            try {
                buffer = bufferAcquireLib.acquireReadonly(object);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, EXPECTED_BYTESLIKE_GOT_P, object);
            }
            try {
                return bufferLib.getCopiedByteArray(buffer);
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class NeedleToBytesNode extends PNodeWithContext {
        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, Object needle);

        @Specialization(guards = "indexCheck.execute(inliningTarget, needle)")
        static byte[] number(VirtualFrame frame, Node inliningTarget, Object needle,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheck,
                        @Cached(inline = false) CastToByteNode castToByteNode) {
            return new byte[]{castToByteNode.execute(frame, needle)};
        }

        @Specialization(guards = "!indexCheck.execute(inliningTarget, needle)", limit = "3")
        static byte[] bytesLike(Node inliningTarget, Object needle,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheck,
                        @CachedLibrary("needle") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            Object buffer = acquireLib.acquireReadonly(needle);
            try {
                return bufferLib.getCopiedByteArray(buffer);
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class FindNode extends PNodeWithContext {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object self, Object needle, int start, int end, boolean reverse);

        public final int execute(VirtualFrame frame, Node inliningTarget, Object self, Object needle, int start, int end) {
            return execute(frame, inliningTarget, self, needle, start, end, false);
        }

        public final int executeReverse(VirtualFrame frame, Node inliningTarget, Object self, Object needle, int start, int end) {
            return execute(frame, inliningTarget, self, needle, start, end, true);
        }

        @Specialization
        static int find(VirtualFrame frame, Node inliningTarget, Object self, Object needle, int start, int end, boolean reverse,
                        @Cached NeedleToBytesNode needleToBytesNode,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached GetInternalByteArrayNode getInternalArray) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            return find(getInternalArray.execute(inliningTarget, storage), storage.length(), needleToBytesNode.execute(frame, inliningTarget, needle), adjustStartIndex(start, len),
                            adjustEndIndex(end, len), reverse);
        }

        public static int find(byte[] haystack, int len1, byte needle, int start, int end, boolean reverse) {
            if (start >= len1) {
                return -1;
            }
            return findElement(haystack, needle, start, end, reverse);
        }

        public static int find(byte[] haystack, int len1, byte[] needle, int start, int end, boolean reverse) {
            int len2 = needle.length;

            if (len2 == 0 && start <= len1) {
                if (!reverse) {
                    return start;
                } else {
                    return end;
                }
            }
            if (start >= len1 || len1 < len2) {
                return -1;
            }
            if (len2 == 1) {
                return findElement(haystack, needle[0], start, end, reverse);
            }

            return findSubSequence(haystack, needle, len2, start, end, reverse);
        }

        private static int isEqual(int i, byte[] haystack, byte[] needle, int len2) {
            for (int j = 0; j < len2; j++) {
                if (haystack[i + j] != needle[j]) {
                    return -1;
                }
            }
            return i;
        }

        private static int findSubSequence(byte[] haystack, byte[] needle, int len2, int start, int end, boolean reverse) {
            // TODO implement a more efficient algorithm
            if (!reverse) {
                return findSubSequenceForward(haystack, needle, len2, start, end);
            } else {
                return findSubSequenceReverse(haystack, needle, len2, start, end);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static int findSubSequenceForward(byte[] haystack, byte[] needle, int len2, int start, int end) {
            for (int i = start; i < end - len2 + 1; i++) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        @TruffleBoundary(allowInlining = true)
        private static int findSubSequenceReverse(byte[] haystack, byte[] needle, int len2, int start, int end) {
            for (int i = end - len2; i >= start; i--) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        private static int findElement(byte[] haystack, byte sub, int start, int end, boolean reverse) {
            if (!reverse) {
                return findElementForward(haystack, sub, start, end);
            } else {
                return findElementReverse(haystack, sub, start, end);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static int findElementForward(byte[] haystack, byte sub, int start, int end) {
            for (int i = start; i < end; i++) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        @TruffleBoundary(allowInlining = true)
        private static int findElementReverse(byte[] haystack, byte sub, int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class BytesLikeCheck extends PNodeWithContext {

        public abstract boolean execute(Node inliningTarget, Object object);

        @SuppressWarnings("unused")
        @Specialization
        static boolean check(PBytesLike obj) {
            return true;
        }

        @Specialization(guards = "!isPBytes(obj)")
        static boolean check(Node inliningTarget, PythonAbstractNativeObject obj,
                        @Cached GetClassNode getClassNode,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode) {
            Object type = getClassNode.execute(inliningTarget, obj);
            return isSubtypeNode.execute(null, type, PythonBuiltinClassType.PBytes) || isSubtypeNode.execute(null, type, PythonBuiltinClassType.PByteArray);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean check(Object obj) {
            return false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CmpNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, PBytesLike left, PBytesLike right);

        @Specialization
        static int cmp(PBytesLike left, PBytesLike right,
                        @Cached(inline = false) SequenceStorageNodes.GetItemNode getLeftItemNode,
                        @Cached(inline = false) SequenceStorageNodes.GetItemNode getRightItemNode) {
            int llen = left.getSequenceStorage().length();
            int rlen = right.getSequenceStorage().length();
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int a = getLeftItemNode.executeKnownInt(left.getSequenceStorage(), i);
                int b = getRightItemNode.executeKnownInt(right.getSequenceStorage(), i);
                if (a != b) {
                    // CPython uses 'memcmp'; so do unsigned comparison
                    return (a & 0xFF) - (b & 0xFF);
                }
            }
            return llen - rlen;
        }
    }

    public abstract static class ExpectStringNode extends ArgumentCastNode {
        private final int argNum;
        private final String className;

        protected ExpectStringNode(int argNum, String className) {
            this.argNum = argNum;
            this.className = className;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static Object none(PNone none) {
            return none;
        }

        @Specialization
        static Object str(TruffleString str) {
            return str;
        }

        @Specialization
        static Object str(PString str,
                        @Bind("this") Node inliningTarget,
                        @Cached StringNodes.StringMaterializeNode toStr) {
            return toStr.execute(inliningTarget, str);
        }

        @Fallback
        Object doOthers(@SuppressWarnings("unused") VirtualFrame frame, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, className, argNum, PythonBuiltinClassType.PString, value);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectStringNode create(@ArgumentIndex int argNum, String className) {
            return BytesNodesFactory.ExpectStringNodeGen.create(argNum, className);
        }
    }

    /**
     * Like {@code PyBytes_FromObject}, but returns a Java byte array. The array is guaranteed to be
     * a new copy. Note that {@code PyBytes_FromObject} returns the argument unchanged when it's
     * already bytes. We obviously cannot do that here, it must be done by the caller if the need
     * this behavior.
     */
    @GenerateInline(false)          // footprint reduction 44 -> 27
    public abstract static class BytesFromObject extends Node {
        public abstract byte[] execute(VirtualFrame frame, Object object);

        // TODO make fast paths for builtin list/tuple - note that FromSequenceNode doesn't work
        // properly when the list is mutated by its __index__

        @Specialization
        static byte[] doGeneric(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.IterableToByteNode iterableToByteNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (bufferAcquireLib.hasBuffer(object)) {
                // TODO PyBUF_FULL_RO
                Object buffer = bufferAcquireLib.acquire(object, BufferFlags.PyBUF_ND, frame, indirectCallData);
                try {
                    return bufferLib.getCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, indirectCallData);
                }
            }
            if (!PGuards.isString(object)) {
                try {
                    return iterableToByteNode.execute(frame, object);
                } catch (PException e) {
                    e.expect(inliningTarget, TypeError, errorProfile);
                }
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, object);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class BytesInitNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, Object source, Object encoding, Object errors);

        @Specialization
        static byte[] none(@SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return PythonUtils.EMPTY_BYTE_ARRAY;
        }

        @Specialization(guards = "isByteStorage(source)")
        static byte[] byteslike(PBytesLike source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return (byte[]) ((ByteSequenceStorage) source.getSequenceStorage()).getCopyOfInternalArrayObject();
        }

        @Specialization(guards = {"!isString(source)", "!isNoValue(source)"})
        static byte[] fromObject(VirtualFrame frame, Node inliningTarget, Object source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached(inline = false) BytesFromObject bytesFromObject,
                        // Exclusive as a workaround for GR-44836
                        @Cached @Exclusive PRaiseNode.Lazy raiseNode) {
            if (indexCheckNode.execute(inliningTarget, source)) {
                try {
                    int size = asSizeNode.executeExact(frame, inliningTarget, source);
                    if (size < 0) {
                        throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEGATIVE_COUNT);
                    }
                    try {
                        return new byte[size];
                    } catch (OutOfMemoryError error) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.get(inliningTarget).raise(MemoryError);
                    }
                } catch (PException e) {
                    e.expect(inliningTarget, TypeError, errorProfile);
                    // fallthrough
                }
            }
            return bytesFromObject.execute(frame, source);
        }

        @Specialization(guards = {"isString(source)", "isString(encoding)"})
        static byte[] fromString(Node inliningTarget, Object source, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached @Shared CastToTruffleStringNode castStr,
                        @Cached(inline = false) @Shared CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encodeNode) {
            return encodeNode.execute(source, castStr.execute(inliningTarget, encoding), T_STRICT);
        }

        @Specialization(guards = {"isString(source)", "isString(encoding)", "isString(errors)"})
        static byte[] fromString(Node inliningTarget, Object source, Object encoding, Object errors,
                        @Cached @Shared CastToTruffleStringNode castStr,
                        @Cached(inline = false) @Shared CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode encodeNode) {
            return encodeNode.execute(source, castStr.execute(inliningTarget, encoding), castStr.execute(inliningTarget, errors));
        }

        @Specialization(guards = "isString(source)")
        @SuppressWarnings("unused")
        static byte[] fromString(Node inliningTarget, Object source, PNone encoding, Object errors,
                        @Cached @Shared PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.STRING_ARG_WO_ENCODING);
        }

        @Fallback
        @SuppressWarnings("unused")
        public static byte[] error(Node inliningTarget, Object source, Object encoding, Object errors,
                        @Cached @Shared PRaiseNode.Lazy raiseNode) {
            if (PGuards.isNone(encoding)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ENCODING_ARG_WO_STRING);
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ERRORS_WITHOUT_STR_ARG);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ByteToHexNode extends PNodeWithContext {

        public abstract TruffleString execute(Node inliningTarget, byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup);

        @Specialization(guards = "bytesPerSepGroup == 0")
        static TruffleString zero(byte[] argbuf, int arglen, @SuppressWarnings("unused") byte sep, @SuppressWarnings("unused") int bytesPerSepGroup,
                        @Shared @Cached(inline = false) TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode) {

            int resultlen = arglen * 2;
            byte[] retbuf = new byte[resultlen];

            for (int i = 0, j = 0; i < arglen; ++i) {
                assert ((j + 1) < resultlen);
                int c = argbuf[i] & 0xFF;
                retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
            }
            return createASCIIString(retbuf, fromByteArrayNode, switchEncodingNode);
        }

        @Specialization(guards = "bytesPerSepGroup < 0")
        static TruffleString negative(Node inliningTarget, byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup,
                        @Shared @Cached InlinedConditionProfile earlyExit,
                        @Shared @Cached(inline = false) TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (earlyExit.profile(inliningTarget, arglen == 0)) {
                return T_EMPTY_STRING;
            }
            int absBytesPerSepGroup = -bytesPerSepGroup;
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;
            if (arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0, fromByteArrayNode, switchEncodingNode);
            }

            byte[] retbuf = new byte[resultlen];
            int chunks = (arglen - 1) / absBytesPerSepGroup;
            int i = 0, j = 0;
            for (int chunk = 0; chunk < chunks; chunk++) {
                for (int k = 0; k < absBytesPerSepGroup; k++) {
                    int c = argbuf[i++] & 0xFF;
                    retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                    retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
                }
                retbuf[j++] = sep;
            }
            while (i < arglen) {
                int c = argbuf[i++] & 0xFF;
                retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
            }

            return createASCIIString(retbuf, fromByteArrayNode, switchEncodingNode);
        }

        @Specialization(guards = "absBytesPerSepGroup > 0")
        static TruffleString positive(Node inliningTarget, byte[] argbuf, int arglen, byte sep, int absBytesPerSepGroup,
                        @Shared @Cached InlinedConditionProfile earlyExit,
                        @Shared @Cached(inline = false) TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (earlyExit.profile(inliningTarget, arglen == 0)) {
                return T_EMPTY_STRING;
            }
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;

            if (arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen) {
                throw raiseNode.get(inliningTarget).raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0, fromByteArrayNode, switchEncodingNode);
            }

            byte[] retbuf = new byte[resultlen];
            int chunks = (arglen - 1) / absBytesPerSepGroup;
            int i = arglen - 1;
            int j = resultlen - 1;
            for (int chunk = 0; chunk < chunks; chunk++) {
                for (int k = 0; k < absBytesPerSepGroup; k++) {
                    int c = argbuf[i--] & 0xFF;
                    retbuf[j--] = BytesUtils.HEXDIGITS[c & 0x0f];
                    retbuf[j--] = BytesUtils.HEXDIGITS[c >>> 4];
                }
                retbuf[j--] = sep;
            }
            while (i >= 0) {
                int c = argbuf[i--] & 0xFF;
                retbuf[j--] = BytesUtils.HEXDIGITS[c & 0x0f];
                retbuf[j--] = BytesUtils.HEXDIGITS[c >>> 4];
            }
            return createASCIIString(retbuf, fromByteArrayNode, switchEncodingNode);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 72 -> 54
    public abstract static class IterableToByteNode extends Node {
        public abstract byte[] execute(VirtualFrame frame, Object iterable);

        @Specialization
        static byte[] bytearray(VirtualFrame frame, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenghtHintNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached CastToByteNode castToByteNode,
                        @Cached PyObjectGetIter getIter) {
            Object it = getIter.execute(frame, inliningTarget, iterable);
            int len = lenghtHintNode.execute(frame, inliningTarget, iterable);
            byte[] arr = new byte[len < 16 && len > 0 ? len : 16];
            int i = 0;
            while (true) {
                try {
                    byte item = castToByteNode.execute(frame, getNextNode.execute(frame, it));
                    if (i >= arr.length) {
                        arr = resize(arr, arr.length * 2);
                    }
                    arr[i++] = item;
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, stopIterationProfile);
                    return resize(arr, i);
                }
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
        }
    }

    @GenerateInline(false)          // footprint reduction 52 -> 34
    public abstract static class DecodeUTF8FSPathNode extends Node {

        public abstract TruffleString execute(VirtualFrame frame, Object value);

        @Specialization
        static TruffleString doit(VirtualFrame frame, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached CastToTruffleStringNode toString,
                        @Cached PyOSFSPathNode fsPath,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            Object path = fsPath.execute(frame, inliningTarget, value);
            if (bufferAcquireLib.hasBuffer(path)) {
                Object buffer = bufferAcquireLib.acquireReadonly(path, frame, indirectCallData);
                try {
                    /*-
                     * This should be equivalent to PyUnicode_EncodeFSDefault
                     * TODO: encoding preference is set per context but will force
                     * it to UTF-8 for the time being.
                     */
                    TruffleString utf8 = fromByteArrayNode.execute(bufferLib.getCopiedByteArray(path), Encoding.UTF_8, false);
                    return switchEncodingNode.execute(utf8, TS_ENCODING);
                } finally {
                    bufferLib.release(buffer, frame, indirectCallData);
                }
            }
            return toString.execute(inliningTarget, path);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashBufferNode extends PNodeWithContext {
        public abstract long execute(Node inliningTarget, Object buffer);

        @Specialization(guards = "bufferLib.hasInternalByteArray(buffer)", limit = "2")
        static long hashDirect(Object buffer,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            PythonBufferAccessLibrary.assertIsBuffer(buffer);
            int len = bufferLib.getBufferLength(buffer);
            byte[] array = bufferLib.getInternalByteArray(buffer);
            return computeHash(len, array);
        }

        @TruffleBoundary
        private static int computeHash(int len, byte[] array) {
            int result = 1;
            for (int i = 0; i < len; i++) {
                result = 31 * result + array[i];
            }
            return result;
        }

        @Specialization(guards = "!bufferLib.hasInternalByteArray(buffer)", limit = "2")
        static long hashIndirect(Object buffer,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            PythonBufferAccessLibrary.assertIsBuffer(buffer);
            int len = bufferLib.getBufferLength(buffer);
            int result = 1;
            for (int i = 0; i < len; i++) {
                result = 31 * result + bufferLib.readByte(buffer, i);
            }
            return result;
        }
    }

    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 25
    public abstract static class HexStringToBytesNode extends Node {
        public abstract byte[] execute(TruffleString str);

        @Specialization(guards = "isAscii(str, getCodeRangeNode)")
        static byte[] ascii(TruffleString str,
                        @Bind("this") Node inliningTarget,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString ascii = switchEncodingNode.execute(str, Encoding.US_ASCII);
            InternalByteArray iba = getInternalByteArrayNode.execute(ascii, Encoding.US_ASCII);
            byte[] bytes = new byte[iba.getLength() / 2];
            byte[] strchar = iba.getArray();
            int n = 0;
            for (int i = iba.getOffset(); i < iba.getEnd(); ++i) {
                byte c = strchar[i];
                if (isSpace(c)) {
                    continue;
                }
                int top = BytesUtils.digitValue(c);
                if (top >= 16 || top < 0) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, NON_HEX_NUMBER_IN_FROMHEX, i);
                }

                c = i + 1 < iba.getEnd() ? strchar[++i] : 0;
                int bottom = BytesUtils.digitValue(c);
                if (bottom >= 16 || bottom < 0) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, NON_HEX_NUMBER_IN_FROMHEX, i);
                }

                bytes[n++] = (byte) ((top << 4) | bottom);
            }
            if (n != bytes.length) {
                bytes = arrayCopyOf(bytes, n);
            }
            return bytes;
        }

        @Specialization(guards = "!isAscii(str, getCodeRangeNode)")
        static byte[] nonAscii(TruffleString str,
                        @Bind("this") Node inliningTarget,
                        @Shared("getCodeRange") @Cached @SuppressWarnings("unused") TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
            int i = 0;
            while (it.hasNext()) {
                if (nextNode.execute(it) > 127) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, NON_HEX_NUMBER_IN_FROMHEX, i);
                }
                ++i;
            }
            throw shouldNotReachHere();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetBytesStorage extends Node {
        public abstract SequenceStorage execute(Node inliningTarget, Object bytes);

        @Specialization
        SequenceStorage getManaged(PBytesLike bytes) {
            return bytes.getSequenceStorage();
        }

        @Specialization
        @InliningCutoff
        SequenceStorage getNative(PythonAbstractNativeObject bytes,
                        @Cached(inline = false) GetNativeBytesStorage getNativeTupleStorage) {
            return getNativeTupleStorage.execute(bytes);
        }
    }

    @GenerateInline(false)
    public abstract static class GetNativeBytesStorage extends Node {
        public abstract NativeByteSequenceStorage execute(PythonAbstractNativeObject tuple);

        @Specialization
        NativeByteSequenceStorage getNative(PythonAbstractNativeObject bytes,
                        @Cached CStructAccess.GetElementPtrNode getContents,
                        @Cached CStructAccess.ReadI64Node readI64Node) {
            assert PyBytesCheckNode.executeUncached(bytes) || PyByteArrayCheckNode.executeUncached(bytes);
            Object array = getContents.getElementPtr(bytes.getPtr(), PyBytesObject__ob_sval);
            int size = (int) readI64Node.readFromObj(bytes, PyVarObject__ob_size);
            return NativeByteSequenceStorage.create(array, size, size, false);
        }
    }

    public static int adjustStartIndex(int startIn, int len) {
        if (startIn < 0) {
            int start = startIn + len;
            return start < 0 ? 0 : start;
        }
        return startIn;
    }

    public static int adjustEndIndex(int endIn, int len) {
        if (endIn > len) {
            return len;
        } else if (endIn < 0) {
            int end = endIn + len;
            return end < 0 ? 0 : end;
        }
        return endIn;
    }

    static boolean compareByteArrays(ComparisonOp op, byte[] selfArray, int selfLength, byte[] otherArray, int otherLength) {
        int compareResult = 0;
        if ((op == ComparisonOp.EQ || op == ComparisonOp.NE) && selfLength != otherLength) {
            return op == ComparisonOp.NE;
        }
        for (int i = 0; i < Math.min(selfLength, otherLength); i++) {
            compareResult = Byte.compareUnsigned(selfArray[i], otherArray[i]);
            if (compareResult != 0) {
                break;
            }
        }
        if (compareResult == 0) {
            compareResult = Integer.compare(selfLength, otherLength);
        }
        return op.cmpResultToBool(compareResult);
    }

}
