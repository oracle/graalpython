/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.codecs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.nodes.ErrorMessages.CHARACTER_MAPPING_MUST_BE_IN_RANGE_256;
import static com.oracle.graal.python.nodes.ErrorMessages.CHARACTER_MAPPING_MUST_RETURN_INT_BYTES_OR_NONE_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.CHARACTER_MAPS_TO_UNDEFINED;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.CallDecodingErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.CallEncodingErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.DecodingErrorHandlerResult;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.EncodingErrorHandlerResult;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.ErrorHandler;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.ErrorHandlerCache;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.GetErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.RaiseEncodeException;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.ByteArrayBuilder;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public final class CharmapNodes {

    private static final TruffleString T_CHARMAP = tsLiteral("charmap");

    private CharmapNodes() {
    }

    // Equivalent of PyUnicode_BuildEncodingMap
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeBuildEncodingMapNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TruffleString map);

        @Specialization
        static Object doIt(VirtualFrame frame, Node inliningTarget, TruffleString map,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached(inline = false) HashingStorageSetItem setItemNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached(inline = false) PythonObjectFactory factory) {
            int len = Math.min(codePointLengthNode.execute(map, TS_ENCODING), 256);
            if (len == 0) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            }
            byte[] level1 = new byte[32];
            byte[] level2 = new byte[512];
            int count2 = 0;
            int count3 = 0;

            Arrays.fill(level1, (byte) 0xFF);
            Arrays.fill(level2, (byte) 0xFF);
            if (codePointAtIndexNode.execute(map, 0, TS_ENCODING, ErrorHandling.BEST_EFFORT) != 0) {
                return doDict(frame, inliningTarget, map, len, codePointAtIndexNode, setItemNode, factory);
            }
            for (int i = 1; i < len; ++i) {
                int cp = codePointAtIndexNode.execute(map, i, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                if (cp == 0 || cp > 0xFFFF) {
                    return doDict(frame, inliningTarget, map, len, codePointAtIndexNode, setItemNode, factory);
                }
                if (cp == 0xFFFE) {
                    continue;
                }
                int l1 = cp >> 11;
                int l2 = cp >> 7;
                if (level1[l1] == (byte) 0xFF) {
                    level1[l1] = (byte) count2++;
                }
                if (level2[l2] == (byte) 0xFF) {
                    level2[l2] = (byte) count3++;
                }
            }
            if (count2 >= 0xFF || count3 >= 0xFF) {
                return doDict(frame, inliningTarget, map, len, codePointAtIndexNode, setItemNode, factory);
            }

            byte[] level23 = new byte[16 * count2 + 128 * count3];
            int l3Start = 16 * count2;
            Arrays.fill(level23, 0, l3Start, (byte) 0xFF);
            count3 = 0;
            for (int i = 1; i < len; ++i) {
                int cp = codePointAtIndexNode.execute(map, i, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                if (cp == 0xFFFE) {
                    continue;
                }
                int o1 = cp >> 11;
                int o2 = (cp >> 7) & 0x0F;
                int i2 = 16 * (level1[o1] & 0xFF) + o2;
                if (level23[i2] == (byte) 0xFF) {
                    level23[i2] = (byte) count3++;
                }
                int o3 = cp & 0x7F;
                int i3 = 128 * (level23[i2] & 0xFF) + o3;
                level23[l3Start + i3] = (byte) i;
            }
            return factory.createEncodingMap(count2, count3, level1, level23);
        }

        private static Object doDict(VirtualFrame frame, Node inliningTarget, TruffleString map, int len, TruffleString.CodePointAtIndexNode codePointAtIndexNode, HashingStorageSetItem setItemNode,
                        PythonObjectFactory factory) {
            HashingStorage store = PDict.createNewStorage(len);
            for (int i = 0; i < len; ++i) {
                int cp = codePointAtIndexNode.execute(map, i, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                store = setItemNode.execute(frame, inliningTarget, store, cp, i);
            }
            return factory.createDict(store);
        }
    }

    // Equivalent of _PyUnicode_EncodeCharmap
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyUnicodeEncodeCharmapNode extends Node {
        public abstract byte[] execute(VirtualFrame frame, Node inliningTarget, TruffleString src, TruffleString errors, Object mapping);

        @Specialization
        static byte[] doLatin1(TruffleString src, TruffleString errors, PNone mapping,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            // TODO latin1
            throw raiseNode.raise(NotImplementedError, toTruffleStringUncached("latin1"));
        }

        @Fallback
        static byte[] doGenericMapping(VirtualFrame frame, Node inliningTarget, TruffleString src, TruffleString errors, Object mapping,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached CharmapEncodeOutputNode charmapEncodeOutputNode,
                        @Cached CharmapEncodingErrorNode charmapEncodingErrorNode) {
            int len = codePointLengthNode.execute(src, TS_ENCODING);
            if (len == 0) {
                return EMPTY_BYTE_ARRAY;
            }
            ByteArrayBuilder builder = new ByteArrayBuilder(len);
            int inPos = 0;
            ErrorHandlerCache cache = new ErrorHandlerCache();
            while (inPos < len) {
                int cp = codePointAtIndexNode.execute(src, inPos, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                boolean x = charmapEncodeOutputNode.execute(frame, inliningTarget, cp, mapping, builder);
                if (!x) {
                    inPos = charmapEncodingErrorNode.execute(frame, inliningTarget, cache, src, inPos, len, errors, mapping, builder);
                } else {
                    ++inPos;
                }
            }
            return builder.toArray();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CharmapEncodingErrorNode extends Node {

        abstract int execute(VirtualFrame frame, Node inliningTarget, ErrorHandlerCache cache, TruffleString src, int pos, int len, TruffleString errors, Object mapping, ByteArrayBuilder builder);

        @Specialization
        static int doIt(VirtualFrame frame, Node inliningTarget, ErrorHandlerCache cache, TruffleString src, int pos, int len, TruffleString errors, Object mapping, ByteArrayBuilder builder,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached CharmapEncodeLookupNode charmapEncodeLookupNode,
                        @Cached GetErrorHandlerNode getErrorHandlerNode,
                        @Cached CallEncodingErrorHandlerNode callEncodingErrorHandlerNode,
                        @Cached ByteArrayBuilder.AppendBytesNode appendBytesNode,
                        @Cached CharmapEncodeOutputNode charmapEncodeOutputNode,
                        @Cached RaiseEncodeException raiseEncodeException) {
            int errEnd = pos;
            while (errEnd < len) {
                int cp = codePointAtIndexNode.execute(src, errEnd, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                if (mapping instanceof PEncodingMap map) {
                    if (encodingMapLookup(cp, map) != -1) {
                        break;
                    }
                } else {
                    if (charmapEncodeLookupNode.execute(frame, inliningTarget, cp, mapping) != PNone.NONE) {
                        break;
                    }
                }
                ++errEnd;
            }
            if (cache.errorHandlerEnum == ErrorHandler.UNKNOWN) {
                cache.errorHandlerEnum = getErrorHandlerNode.execute(inliningTarget, errors);
            }
            // TODO switch (cache.errorHandlerEnum)
            EncodingErrorHandlerResult result = callEncodingErrorHandlerNode.execute(frame, inliningTarget, cache, errors, T_CHARMAP, src, pos, errEnd, CHARACTER_MAPS_TO_UNDEFINED);
            if (!result.isUnicode) {
                appendBytesNode.execute(frame, inliningTarget, builder, result.replacement);
                return result.newPos;
            }
            TruffleString replacement = castToTruffleStringNode.execute(inliningTarget, result.replacement);
            int repLen = codePointLengthNode.execute(replacement, TS_ENCODING);
            for (int i = 0; i < repLen; ++i) {
                int cp = codePointAtIndexNode.execute(replacement, i, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                if (!charmapEncodeOutputNode.execute(frame, inliningTarget, cp, mapping, builder)) {
                    raiseEncodeException.execute(frame, inliningTarget, cache, T_CHARMAP, src, pos, errEnd, CHARACTER_MAPS_TO_UNDEFINED);
                }
            }
            return result.newPos;
        }
    }

    // Equivalent of charmapencode_output
    @GenerateInline
    @GenerateCached(false)
    abstract static class CharmapEncodeOutputNode extends Node {
        abstract boolean execute(VirtualFrame frame, Node inliningTarget, int cp, Object mapping, ByteArrayBuilder builder);

        @Specialization
        static boolean doEncodingMap(int cp, PEncodingMap mapping, ByteArrayBuilder builder) {
            int res = encodingMapLookup(cp, mapping);
            if (res == -1) {
                return false;
            }
            assert res >= 0 && res <= 255;
            builder.add((byte) res);
            return true;
        }

        @Fallback
        static boolean doGenericMapping(VirtualFrame frame, Node inliningTarget, int cp, Object mapping, ByteArrayBuilder builder,
                        @Cached CharmapEncodeLookupNode charmapEncodeLookupNode,
                        @Cached ByteArrayBuilder.AppendBytesNode appendBytesNode) {
            Object rep = charmapEncodeLookupNode.execute(frame, inliningTarget, cp, mapping);
            if (rep == PNone.NONE) {
                return false;
            }
            if (rep instanceof Long value) {
                assert value >= 0 && value <= 255;
                builder.add(value.byteValue());
            } else {
                appendBytesNode.execute(frame, inliningTarget, builder, rep);
            }
            return true;
        }

    }

    // Equivalent of charmapencode_lookup
    @GenerateInline
    @GenerateCached(false)
    abstract static class CharmapEncodeLookupNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, int cp, Object mapping);

        @Specialization
        static Object doIt(VirtualFrame frame, Node inliningTarget, int cp, Object mapping,
                        @Cached PyObjectGetItem pyObjectGetItemNode,
                        @Cached IsBuiltinObjectProfile isLookupErrorProfile,
                        @Cached PyLongCheckNode pyLongCheckNode,
                        @Cached PyLongAsLongNode pyLongAsLongNode,
                        @Cached PyBytesCheckNode pyBytesCheckNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object item;
            try {
                item = pyObjectGetItemNode.execute(frame, inliningTarget, mapping, cp);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.LookupError, isLookupErrorProfile);
                return PNone.NONE;
            }
            if (item == PNone.NONE) {
                return item;
            }
            if (pyLongCheckNode.execute(inliningTarget, item)) {
                long value = pyLongAsLongNode.execute(frame, inliningTarget, item);
                if (value < 0 || value > 255) {
                    raiseNode.get(inliningTarget).raise(TypeError, CHARACTER_MAPPING_MUST_BE_IN_RANGE_256);
                }
                return value;
            }
            if (pyBytesCheckNode.execute(inliningTarget, item)) {
                return item;
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, CHARACTER_MAPPING_MUST_RETURN_INT_BYTES_OR_NONE_NOT_P, item);
        }
    }

    // Equivalent of PyUnicode_DecodeCharmap
    @GenerateInline(false) // footprint reduction 76 -> 57
    public abstract static class PyUnicodeDecodeCharmapNode extends PNodeWithContext {

        private static final int UNDEFINED_MAPPING = 0xfffe;

        public abstract TruffleString execute(VirtualFrame frame, Object data, TruffleString errors, Object mapping);

        @Specialization(limit = "3")
        TruffleString decodeLatin1(VirtualFrame frame, Object data, @SuppressWarnings("unused") TruffleString errors, @SuppressWarnings("unused") PNone mapping,
                        @Shared @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("data") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAccessLibrary bufferLib,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            // equivalent of PyUnicode_DecodeLatin1
            Object dataBuffer = bufferAcquireLib.acquireReadonly(data, frame, getContext(), getLanguage(), indirectCallData);
            try {
                int len = bufferLib.getBufferLength(dataBuffer);
                byte[] src = bufferLib.getInternalOrCopiedByteArray(dataBuffer);
                TruffleString latin1 = fromByteArrayNode.execute(src, 0, len, TruffleString.Encoding.ISO_8859_1, true);
                return switchEncodingNode.execute(latin1, TS_ENCODING);
            } finally {
                bufferLib.release(dataBuffer, frame, indirectCallData);
            }
        }

        @Specialization(limit = "3", guards = "isBuiltinString.execute(inliningTarget, mappingObj)")
        static TruffleString decodeStringMapping(VirtualFrame frame, Object data, TruffleString errors, Object mappingObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("data") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAccessLibrary bufferLib,
                        @SuppressWarnings("unused") @Cached @Exclusive PyUnicodeCheckExactNode isBuiltinString,
                        @Cached @Exclusive CastToTruffleStringNode castToTruffleStringNode,
                        @Cached @Shared TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached @Shared TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Exclusive CallDecodingErrorHandlerNode callErrorHandlerNode) {
            // equivalent of charmap_decode_string
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);

            ErrorHandlerCache cache = new ErrorHandlerCache();
            Object srcObj = data;
            int pos = 0;
            TruffleString mapping = castToTruffleStringNode.execute(inliningTarget, mappingObj);
            int mappingLen = codePointLengthNode.execute(mapping, TS_ENCODING);
            TruffleStringBuilder tsb = TruffleStringBuilder.create(TS_ENCODING);
            int errorStartPos;
            int srcLen;
            do {
                Object srcBuf = bufferAcquireLib.acquireReadonly(srcObj, frame, context, language, indirectCallData);
                try {
                    srcLen = bufferLib.getBufferLength(srcBuf);
                    byte[] src = bufferLib.getInternalOrCopiedByteArray(srcBuf);
                    errorStartPos = -1;
                    for (; pos < srcLen; ++pos) {
                        int index = src[pos] & 0xff;
                        if (index >= mappingLen) {
                            errorStartPos = pos;
                            break;
                        }
                        int cp = codePointAtIndexNode.execute(mapping, index, TS_ENCODING);
                        if (cp == UNDEFINED_MAPPING) {
                            errorStartPos = pos;
                            break;
                        }
                        appendCodePointNode.execute(tsb, cp, 1, true);
                    }
                } finally {
                    bufferLib.release(srcBuf, frame, context, language, indirectCallData);
                }
                if (errorStartPos != -1) {
                    DecodingErrorHandlerResult result = callErrorHandlerNode.execute(frame, inliningTarget, cache, errors, T_CHARMAP, srcObj, errorStartPos, errorStartPos + 1,
                                    ErrorMessages.CHARACTER_MAPS_TO_UNDEFINED);
                    appendStringNode.execute(tsb, result.str);
                    pos = result.newPos;
                    srcObj = result.newSrcObj;
                }
            } while (pos < srcLen);
            return toStringNode.execute(tsb, false);
        }

        @Specialization(limit = "3", guards = {"!isBuiltinString.execute(inliningTarget, mappingObj)", "!isPNone(mappingObj)"})
        static TruffleString decodeGenericMapping(VirtualFrame frame, Object data, TruffleString errors, Object mappingObj,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("data") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") @Shared PythonBufferAccessLibrary bufferLib,
                        @SuppressWarnings("unused") @Cached @Exclusive PyUnicodeCheckExactNode isBuiltinString,
                        @Cached PyObjectGetItem pyObjectGetItemNode,
                        @Cached @Exclusive IsBuiltinObjectProfile isLookupErrorProfile,
                        @Cached PyLongCheckNode pyLongCheckNode,
                        @Cached PyLongAsLongNode pyLongAsLongNode,
                        @Cached PyUnicodeCheckNode pyUnicodeCheckNode,
                        @Cached @Exclusive CastToTruffleStringNode castToTruffleStringNode,
                        @Cached @Shared TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached @Shared TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Exclusive CallDecodingErrorHandlerNode callErrorHandlerNode,
                        @Cached InlinedConditionProfile longValuesProfile,
                        @Cached InlinedConditionProfile strValuesProfile,
                        @Cached InlinedConditionProfile errProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            // equivalent of charmap_decode_mapping
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);

            ErrorHandlerCache cache = new ErrorHandlerCache();
            Object srcObj = data;
            int pos = 0;
            TruffleStringBuilder tsb = TruffleStringBuilder.create(TS_ENCODING);
            int errorStartPos;
            int srcLen;
            do {
                Object srcBuf = bufferAcquireLib.acquireReadonly(srcObj, frame, context, language, indirectCallData);
                try {
                    srcLen = bufferLib.getBufferLength(srcBuf);
                    byte[] src = bufferLib.getInternalOrCopiedByteArray(srcBuf);
                    errorStartPos = -1;
                    for (; pos < srcLen; ++pos) {
                        int key = src[pos] & 0xff;
                        Object item;
                        try {
                            item = pyObjectGetItemNode.execute(frame, inliningTarget, mappingObj, key);
                        } catch (PException e) {
                            e.expect(inliningTarget, PythonBuiltinClassType.LookupError, isLookupErrorProfile);
                            errorStartPos = pos;
                            break;
                        }
                        if (item == PNone.NONE) {
                            errorStartPos = pos;
                            break;
                        }
                        if (longValuesProfile.profile(inliningTarget, pyLongCheckNode.execute(inliningTarget, item))) {
                            long value = pyLongAsLongNode.execute(frame, inliningTarget, item);
                            if (value == UNDEFINED_MAPPING) {
                                errorStartPos = pos;
                                break;
                            }
                            if (value < 0 || value > Character.MAX_CODE_POINT) {
                                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CHARACTER_MAPPING_MUST_BE_IN_RANGE, PInt.toHexString(Character.MAX_CODE_POINT + 1));
                            } else {
                                appendCodePointNode.execute(tsb, (int) value, 1, true);
                            }
                        } else if (strValuesProfile.profile(inliningTarget, pyUnicodeCheckNode.execute(inliningTarget, item))) {
                            TruffleString ts = castToTruffleStringNode.execute(inliningTarget, item);
                            if (codePointLengthNode.execute(ts, TS_ENCODING) == 1) {
                                int cp = codePointAtIndexNode.execute(ts, 0, TS_ENCODING, ErrorHandling.BEST_EFFORT);
                                if (cp == UNDEFINED_MAPPING) {
                                    errorStartPos = pos;
                                    break;
                                }
                                appendCodePointNode.execute(tsb, cp, 1, true);
                            } else {
                                appendStringNode.execute(tsb, castToTruffleStringNode.execute(inliningTarget, item));
                            }
                        } else {
                            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CHARACTER_MAPPING_MUST_RETURN_INT_NONE_OR_STR);
                        }
                    }
                } finally {
                    bufferLib.release(srcBuf, frame, context, language, indirectCallData);
                }
                if (errProfile.profile(inliningTarget, errorStartPos != -1)) {
                    DecodingErrorHandlerResult result = callErrorHandlerNode.execute(frame, inliningTarget, cache, errors, T_CHARMAP, srcObj, errorStartPos, errorStartPos + 1,
                                    ErrorMessages.CHARACTER_MAPS_TO_UNDEFINED);
                    appendStringNode.execute(tsb, result.str);
                    pos = result.newPos;
                    srcObj = result.newSrcObj;
                }
            } while (pos < srcLen);
            return toStringNode.execute(tsb, false);
        }
    }

    // Equivalent of encoding_map_lookup
    static int encodingMapLookup(int cp, PEncodingMap map) {
        if (cp > 0xFFFF) {
            return -1;
        }
        if (cp == 0) {
            return 0;
        }

        int l1 = cp >> 11;
        int l2 = (cp >> 7) & 0xF;
        int l3 = cp & 0x7F;

        /* level 1 */
        int i = map.level1[l1] & 0xFF;
        if (i == 0xFF) {
            return -1;
        }
        /* level 2 */
        i = map.level23[16 * i + l2] & 0xFF;
        if (i == 0xFF) {
            return -1;
        }
        /* level 3 */
        i = map.level23[16 * map.count2 + 128 * i + l3] & 0xFF;
        if (i == 0) {
            return -1;
        }
        return i;
    }

}
