/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.PythonLanguage.getCore;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.BytesJoinNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.FindNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.IterableToByteNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.ToBytesNodeGen;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class BytesNodes {

    @GenerateUncached
    public abstract static class CreateBytesNode extends Node {

        public abstract PBytesLike execute(PythonObjectFactory factory, PBytesLike basedOn, Object bytes);

        @Specialization
        PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, byte[] bytes) {
            return factory.createBytes(bytes);
        }

        @Specialization
        PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, byte[] bytes) {
            return factory.createByteArray(bytes);
        }

        @Specialization
        PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, SequenceStorage bytes) {
            return factory.createBytes(bytes);
        }

        @Specialization
        PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, SequenceStorage bytes) {
            return factory.createByteArray(bytes);
        }

        @Specialization
        PBytesLike bytes(PythonObjectFactory factory, @SuppressWarnings("unused") PBytes basedOn, PBytesLike bytes) {
            return factory.createBytes(bytes.getSequenceStorage());
        }

        @Specialization
        PBytesLike bytearray(PythonObjectFactory factory, @SuppressWarnings("unused") PByteArray basedOn, PBytesLike bytes) {
            return factory.createByteArray(bytes.getSequenceStorage());
        }

    }

    @ImportStatic(PythonOptions.class)
    public abstract static class BytesJoinNode extends PNodeWithContext {

        public abstract byte[] execute(VirtualFrame frame, byte[] sep, Object iterable);

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static byte[] join(VirtualFrame frame, byte[] sep, Object iterable,
                        @CachedLibrary("iterable") PythonObjectLibrary lib,
                        @Cached GetNextNode getNextNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            ArrayList<byte[]> parts = new ArrayList<>();
            int partsTotalSize = 0;
            Object iterator = lib.getIteratorWithFrame(iterable, frame);
            while (true) {
                try {
                    partsTotalSize += append(parts, toBytesNode.execute(frame, getNextNode.execute(frame, iterator)));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
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

        public static BytesJoinNode create() {
            return BytesJoinNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ToBytesNode extends PNodeWithContext {
        private static final String DEFAULT_FORMAT = "expected a bytes-like object, %p found";

        @Child private PRaiseNode raise = PRaiseNode.create();

        private final PythonBuiltinClassType errorType;
        private final String errorMessageFormat;
        final boolean allowRecursive;

        ToBytesNode(boolean allowRecursive, PythonBuiltinClassType errorType, String errorMessageFormat) {
            this.allowRecursive = allowRecursive;
            this.errorType = errorType;
            this.errorMessageFormat = errorMessageFormat;
        }

        public abstract byte[] execute(VirtualFrame frame, Object obj);

        @Specialization
        byte[] doBytes(PBytesLike bytes,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached IsBuiltinClassProfile exceptionProfile) {
            try {
                return toByteArrayNode.execute(bytes.getSequenceStorage());
            } catch (PException e) {
                e.expect(TypeError, exceptionProfile);
                return doError(bytes);
            }
        }

        @Specialization(guards = "allowRecursive")
        byte[] doMemoryView(VirtualFrame frame, PMemoryView memoryView,
                        @Cached("createRecursive()") ToBytesNode recursive,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode) {
            return recursive.execute(frame, callToBytesNode.executeObject(frame, memoryView));
        }

        @Fallback
        byte[] doError(Object obj) {
            throw raise.raise(errorType, errorMessageFormat, obj);
        }

        ToBytesNode createRecursive() {
            return ToBytesNode.create(false);
        }

        public static ToBytesNode create() {
            return ToBytesNodeGen.create(true);
        }

        public static ToBytesNode create(boolean allowRecursive) {
            return ToBytesNodeGen.create(allowRecursive, TypeError, DEFAULT_FORMAT);
        }

        public static ToBytesNode create(boolean allowRecursive, PythonBuiltinClassType errorType, String errorMessageFormat) {
            return ToBytesNodeGen.create(allowRecursive, errorType, errorMessageFormat);
        }
    }

    public abstract static class FindNode extends PNodeWithContext {
        @Child private PRaiseNode raise = PRaiseNode.create();

        public abstract int execute(Object self, int len1, Object sub, int start, int end);

        @Specialization
        int find(byte[] haystack, int len1, byte needle, int start, int end,
                        @Cached ConditionProfile earlyExit) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            return findElement(haystack, needle, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(byte[] haystack, int len1, byte[] needle, int start, int end,
                        @Cached ConditionProfile earlyExit1,
                        @Cached ConditionProfile earlyExit2,
                        @Cached ConditionProfile lenIsOne) {
            int len2 = needle.length;

            if (earlyExit1.profile(len2 == 0 && start <= len1)) {
                return emptySubIndex(start, end);
            }
            if (earlyExit2.profile(start >= len1 || len1 < len2)) {
                return -1;
            }
            if (lenIsOne.profile(len2 == 1)) {
                return findElement(haystack, needle[0], start, end);
            }

            return findSubSequence(haystack, needle, len2, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(SequenceStorage self, int len1, PBytesLike sub, int start, int end,
                        @Cached ConditionProfile earlyExit1,
                        @Cached ConditionProfile earlyExit2,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            byte[] haystack = getBytes.execute(self);
            byte[] needle = getBytes.execute(sub.getSequenceStorage());
            int len2 = lenNode.execute(sub.getSequenceStorage());

            if (earlyExit1.profile(len2 == 0 && start <= len1)) {
                return emptySubIndex(start, end);
            }
            if (earlyExit2.profile(start >= len1 || len1 < len2)) {
                return -1;
            }
            if (len2 == 1) {
                return findElement(haystack, needle[0], start, end);
            }

            return findSubSequence(haystack, needle, len2, start, end > len1 ? len1 : end);
        }

        @Specialization
        int find(SequenceStorage self, int len1, int sub, int start, int end,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached CastToJavaByteNode cast) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            byte[] haystack = getBytes.execute(self);
            return findElement(haystack, cast.execute(sub), start, end > len1 ? len1 : end);
        }

        @Specialization(guards = "lib.canBeIndex(sub)")
        int useIndex(SequenceStorage self, int len1, Object sub, int start, int end,
                        @Cached ConditionProfile earlyExit,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached CastToJavaByteNode cast,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes) {
            if (earlyExit.profile(start >= len1)) {
                return -1;
            }
            byte[] haystack = getBytes.execute(self);
            byte subByte = cast.execute(lib.asIndex(sub));
            return findElement(haystack, subByte, start, end > len1 ? len1 : end);
        }

        // Overridden in RFind
        @SuppressWarnings("unused")
        protected int emptySubIndex(int start, int end) {
            return start;
        }

        @TruffleBoundary(allowInlining = true)
        protected static int isEqual(int i, byte[] haystack, byte[] needle, int len2) {
            for (int j = 0; j < len2; j++) {
                if (haystack[i + j] != needle[j]) {
                    return -1;
                }
            }
            return i;
        }

        @TruffleBoundary(allowInlining = true)
        protected int findSubSequence(byte[] haystack, byte[] needle, int len2, int start, int end) {
            // TODO implement a more efficient algorithm
            for (int i = start; i < end - len2 + 1; i++) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        @TruffleBoundary(allowInlining = true)
        protected int findElement(byte[] haystack, byte sub, int start, int end) {
            for (int i = start; i < end; i++) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unused")
        @Fallback
        int doError(Object bytes, int len1, Object sub, int start, int end) {
            throw raise.raise(TypeError, ErrorMessages.EXPECTED_S_P_FOUND, "a bytes-like object", sub);
        }

        public static FindNode create() {
            return FindNodeGen.create();
        }
    }

    public abstract static class RFindNode extends FindNode {

        @Override
        protected int emptySubIndex(int start, int end) {
            return (end - start) + start;
        }

        @Override
        protected int findSubSequence(byte[] haystack, byte[] needle, int len2, int start, int end) {
            // TODO implement a more efficient algorithm
            for (int i = end - len2; i >= start; i--) {
                if (isEqual(i, haystack, needle, len2) != -1) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        protected int findElement(byte[] haystack, byte sub, int start, int end) {
            for (int i = end - 1; i >= start; i--) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        public static RFindNode create() {
            return BytesNodesFactory.RFindNodeGen.create();
        }
    }

    public static class FromSequenceStorageNode extends Node {

        @Node.Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Node.Child private CastToByteNode castToByteNode;
        @Node.Child private SequenceStorageNodes.LenNode lenNode;

        public byte[] execute(VirtualFrame frame, SequenceStorage storage) {
            int len = getLenNode().execute(storage);
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                Object item = getGetItemNode().execute(frame, storage, i);
                bytes[i] = getCastToByteNode().execute(frame, item);
            }
            return bytes;
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList()));
            }
            return getItemNode;
        }

        private CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(CastToByteNode.create());
            }
            return castToByteNode;
        }

        private SequenceStorageNodes.LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode;
        }

        public static FromSequenceStorageNode create() {
            return new FromSequenceStorageNode();
        }
    }

    public static class FromSequenceNode extends Node {

        @Child private FromSequenceStorageNode fromSequenceStorageNode;
        @Child private SequenceNodes.GetSequenceStorageNode getSequenceStorageNode;

        public byte[] execute(VirtualFrame frame, PSequence sequence) {
            if (fromSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromSequenceStorageNode = insert(FromSequenceStorageNode.create());
                getSequenceStorageNode = insert(SequenceNodes.GetSequenceStorageNode.create());
            }

            return fromSequenceStorageNode.execute(frame, getSequenceStorageNode.execute(sequence));
        }

        public static FromSequenceNode create() {
            return new FromSequenceNode();
        }
    }

    public abstract static class FromIteratorNode extends PNodeWithContext {

        @Child private SequenceStorageNodes.AppendNode appendByteNode;

        public abstract byte[] execute(VirtualFrame frame, Object iterator);

        private SequenceStorageNodes.AppendNode getAppendByteNode() {
            if (appendByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendByteNode = insert(SequenceStorageNodes.AppendNode.create());
            }
            return appendByteNode;
        }

        @Specialization
        byte[] doIt(VirtualFrame frame, Object iterObject,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            ByteSequenceStorage bss = new ByteSequenceStorage(16);
            while (true) {
                try {
                    getAppendByteNode().execute(bss, getNextNode.execute(frame, iterObject), BytesLikeNoGeneralizationNode.SUPPLIER);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return bss.getInternalByteArray();
                }
            }
        }

        public static FromIteratorNode create() {
            return BytesNodesFactory.FromIteratorNodeGen.create();
        }
    }

    public abstract static class CmpNode extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, PBytesLike left, PBytesLike right);

        @Specialization
        static int cmp(VirtualFrame frame, PBytesLike left, PBytesLike right,
                        @Cached SequenceStorageNodes.GetItemNode getLeftItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getRightItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            int llen = lenNode.execute(left.getSequenceStorage());
            int rlen = lenNode.execute(right.getSequenceStorage());
            for (int i = 0; i < Math.min(llen, rlen); i++) {
                int a = getLeftItemNode.executeInt(frame, left.getSequenceStorage(), i);
                int b = getRightItemNode.executeInt(frame, right.getSequenceStorage(), i);
                if (a != b) {
                    // CPython uses 'memcmp'; so do unsigned comparison
                    return (a & 0xFF) - (b & 0xFF);
                }
            }
            return llen - rlen;
        }

        public static CmpNode create() {
            return BytesNodesFactory.CmpNodeGen.create();
        }
    }

    public abstract static class ExpectStringNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final int argNum;
        final String className;

        protected ExpectStringNode(int argNum, String className) {
            this.argNum = argNum;
            this.className = className;
        }

        protected String className() {
            return className;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static Object none(PNone none) {
            return none;
        }

        @Specialization
        static Object str(String str) {
            return str;
        }

        @Specialization
        static Object str(PString str,
                        @Cached StringNodes.StringMaterializeNode toStr) {
            return toStr.execute(str);
        }

        @Fallback
        Object doOthers(@SuppressWarnings("unused") VirtualFrame frame, Object value) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, className(), argNum, PythonBuiltinClassType.PString, value);
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(PGuards.class)
    public abstract static class BytesInitNode extends Node {

        public abstract byte[] execute(VirtualFrame frame, Object source, Object encoding, Object errors);

        @Specialization
        byte[] none(@SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return PythonUtils.EMPTY_BYTE_ARRAY;
        }

        @Specialization(guards = "isByteStorage(source)")
        byte[] byteslike(PBytesLike source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return (byte[]) ((ByteSequenceStorage) source.getSequenceStorage()).getCopyOfInternalArrayObject();
        }

        @Specialization(guards = "lib.canBeIndex(source)", limit = "3")
        byte[] size(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @CachedLibrary("source") PythonObjectLibrary lib) {
            int cap = lib.asSizeWithState(source, PArguments.getThreadState(frame));
            return BytesUtils.fromSize(getCore(), cap);
        }

        @Specialization(guards = {"!isString(source)", "!isNoValue(source)", "!lib.canBeIndex(source)"}, limit = "3")
        public byte[] iterable(VirtualFrame frame, Object source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createCast()") IterableToByteNode toByteNode,
                        @SuppressWarnings("unused") @CachedLibrary("source") PythonObjectLibrary lib) {
            return toByteNode.execute(frame, source, lenNode.execute(frame, source));
        }

        @Specialization
        byte[] fromString(String source, String encoding, @SuppressWarnings("unused") Object errors,
                        @Cached PRaiseNode raise) {
            String e = errors instanceof String ? (String) errors : "strict";
            return BytesBuiltins.stringToByte(source, encoding, e, raise);
        }

        @Specialization
        @SuppressWarnings("unused")
        byte[] fromString(String source, PNone encoding, Object errors,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.STRING_ARG_WO_ENCODING);
        }

        @Fallback
        @SuppressWarnings("unused")
        public byte[] error(Object source, Object encoding, Object errors) {
            if (PGuards.isNone(encoding)) {
                throw PythonLanguage.getCore().raise(TypeError, ErrorMessages.ENCODING_ARG_WO_STRING);
            }
            throw PythonLanguage.getCore().raise(TypeError, ErrorMessages.ERRORS_WITHOUT_STR_ARG);
        }

        protected static BytesNodes.IterableToByteNode createCast() {
            return BytesNodes.IterableToByteNode.create(val -> PythonLanguage.getCore().raise(TypeError, ErrorMessages.ERRORS_WITHOUT_STR_ARG));
        }
    }

    @GenerateNodeFactory
    public abstract static class ByteToHexNode extends PNodeWithContext {

        public abstract String execute(byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup);

        @Specialization(guards = "bytesPerSepGroup == 0")
        public String zero(byte[] argbuf, int arglen, @SuppressWarnings("unused") byte sep, @SuppressWarnings("unused") int bytesPerSepGroup) {

            int resultlen = arglen * 2;
            byte[] retbuf = new byte[resultlen];

            for (int i = 0, j = 0; i < arglen; ++i) {
                assert ((j + 1) < resultlen);
                int c = argbuf[i] & 0xFF;
                retbuf[j++] = BytesUtils.HEXDIGITS[c >>> 4];
                retbuf[j++] = BytesUtils.HEXDIGITS[c & 0x0f];
            }
            return BytesUtils.createASCIIString(retbuf);
        }

        @Specialization(guards = "bytesPerSepGroup < 0")
        public String negative(byte[] argbuf, int arglen, byte sep, int bytesPerSepGroup,
                        @Cached ConditionProfile earlyExit,
                        @Cached ConditionProfile memoryError,
                        @Cached.Shared("error") @Cached PRaiseNode raiseNode) {
            if (earlyExit.profile(arglen == 0)) {
                return "";
            }
            int absBytesPerSepGroup = -bytesPerSepGroup;
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;
            if (memoryError.profile(arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen)) {
                raiseNode.raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0);
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

            return BytesUtils.createASCIIString(retbuf);
        }

        @Specialization(guards = "absBytesPerSepGroup > 0")
        public String positive(byte[] argbuf, int arglen, byte sep, int absBytesPerSepGroup,
                        @Cached ConditionProfile earlyExit,
                        @Cached ConditionProfile memoryError,
                        @Cached.Shared("error") @Cached PRaiseNode raiseNode) {
            if (earlyExit.profile(arglen == 0)) {
                return "";
            }
            /* How many sep characters we'll be inserting. */
            int resultlen = (arglen - 1) / absBytesPerSepGroup;

            if (memoryError.profile(arglen >= SysModuleBuiltins.MAXSIZE / 2 - resultlen)) {
                raiseNode.raise(MemoryError);
            }

            resultlen += arglen * 2;

            if (absBytesPerSepGroup >= arglen) {
                return zero(argbuf, arglen, sep, 0);
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
            return BytesUtils.createASCIIString(retbuf);
        }
    }

    public abstract static class IterableToByteNode extends PNodeWithContext {

        private final Function<Object, Object> typeErrorHandler;
        @Child private PRaiseNode raise = PRaiseNode.create();

        abstract byte[] execute(VirtualFrame frame, Object iterable, int len);

        protected IterableToByteNode(Function<Object, Object> typeErrorHandler) {
            this.typeErrorHandler = typeErrorHandler;
        }

        @Specialization(guards = "!lib.canBeIndex(iterable)")
        public byte[] bytearray(VirtualFrame frame, Object iterable, int len,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinClassProfile stopIterationProfile,
                        @Cached CastToByteNode castToByteNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            Object it = lib.getIteratorWithFrame(iterable, frame);
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
                    e.expectStopIteration(stopIterationProfile);
                    return resize(arr, i);
                }
            }
        }

        @Fallback
        public byte[] error(@SuppressWarnings("unused") VirtualFrame frame, Object obj, @SuppressWarnings("unused") int len) {
            assert typeErrorHandler != null;
            return (byte[]) typeErrorHandler.apply(obj);
        }

        public static IterableToByteNode create(Function<Object, Object> typeErrorHandler) {
            return IterableToByteNodeGen.create(typeErrorHandler);
        }

        protected CastToByteNode createCast() {
            return CastToByteNode.create(val -> {
                throw raise.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            }, val -> {
                throw raise.raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            });
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
        }
    }
}
