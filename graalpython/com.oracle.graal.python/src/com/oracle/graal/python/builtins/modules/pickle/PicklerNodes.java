/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_CP_IMPORT_MAPPING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_CP_NAME_MAPPING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_CP_REVERSE_IMPORT_MAPPING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_CP_REVERSE_NAME_MAPPING;
import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_STR_NOT_P;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.nodes.statement.AbstractImportNode.importModule;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.lib.PyObjectSetItem;
import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinConstructorsFactory;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.CachedHashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNodeGen;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PicklerNodes {
    abstract static class BasePickleNode extends Node {
        private static final TruffleString T_LOCALS = tsLiteral("<locals>");
        static final TruffleString T_SYS_MODULES = tsLiteral("sys.modules");
        public static final TruffleString T_CODEC_RAW_UNICODE_ESCAPE = tsLiteral("raw_unicode_escape");
        public static final TruffleString T_CODEC_BYTES = tsLiteral("bytes");
        public static final TruffleString T_CODEC_ASCII = tsLiteral("ascii");
        public static final TruffleString T_ERRORS_SURROGATEPASS = tsLiteral("surrogatepass");
        public static final TruffleString T_ERRORS_STRICT = tsLiteral("strict");

        @SuppressWarnings("FieldMayBeFinal") @Child private PyObjectGetItem getItemNode = PyObjectGetItem.create();
        @SuppressWarnings("FieldMayBeFinal") @Child private PyIterNextNode getNextNode = PyIterNextNode.create();
        @Child CastToTruffleStringNode toStringNode = CastToTruffleStringNode.create();

        @Child private PythonObjectFactory objectFactory;
        @Child private HiddenAttr.ReadNode readHiddenAttributeNode;
        @Child private IsBuiltinObjectProfile errProfile;
        @Child private InlineIsBuiltinClassProfile isBuiltinClassProfile;
        @Child private HashingStorageSetItem setHashingStorageItemNode;
        @Child private PyObjectSetItem pyObjectSetItemNode;
        @Child private CachedHashingStorageGetItem getHashingStorageItemNode;
        @Child private SequenceStorageNodes.GetItemNode getSeqStorageItemNode;
        @Child private PyNumberAsSizeNode asSizeNode;
        @Child private CastToTruffleStringNode castToTruffleStringNode;
        @Child private SequenceNodes.GetSequenceStorageNode getSequenceStorageNode;
        @Child private CallVarargsMethodNode callVarargsMethodNode;
        @Child private ExecutePositionalStarargsNode getArgsNode;
        @Child private ExpandKeywordStarargsNode getKwArgsNode;
        @Child private BuiltinConstructors.IntNode intNode;
        @Child private CodecsModuleBuiltins.CodecsDecodeNode codecsDecodeNode;
        @Child private CodecsModuleBuiltins.CodecsEscapeDecodeNode codecsEscapeDecodeNode;
        @Child private CodecsModuleBuiltins.CodecsEncodeNode codecsEncodeNode;
        @Child private PyIterCheckNode isIteratorObjectNode;
        @Child private GetClassNode getClassNode;
        @Child private PyObjectSizeNode sizeNode;
        @Child private PyObjectLookupAttr lookupAttrNode;
        @Child private BytesNodes.ToBytesNode toBytesNode;
        @Child private PyObjectReprAsTruffleStringNode reprNode;
        @Child private TruffleString.FromByteArrayNode tsFromByteArrayNode;
        @Child private TruffleString.CodePointLengthNode tsCodePointLengthNode;
        @Child private TruffleString.CodePointAtIndexNode tsCodePointAtIndexNode;
        @Child private TruffleString.FromLongNode tsFromLongNode;
        @Child private TruffleString.IndexOfStringNode tsIndexOfStringNode;
        @Child private TruffleString.SubstringNode tsSubstringNode;
        @Child private TruffleString.EqualNode tsEqualNode;
        @Child private TruffleString.CopyToByteArrayNode tsCopyToByteArrayNode;
        @Child private TruffleString.GetCodeRangeNode tsGetCodeRangeNode;
        @Child private TruffleString.SwitchEncodingNode tsSwitchEncodingNode;
        @Child private HashingStorageGetIterator getHashingStorageIteratorNode;
        @Child private HashingStorageIteratorNext hashingStorageItNext;
        @Child private HashingStorageIteratorKey hashingStorageItKey;
        @Child private HashingStorageIteratorValue hashingStorageItValue;
        @Child private PRaiseNode raiseNode;

        protected final PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (isAdoptable()) {
                    raiseNode = insert(PRaiseNode.create());
                } else {
                    raiseNode = PRaiseNode.getUncached();
                }
            }
            return raiseNode;
        }

        protected PException raise(PythonBuiltinClassType type, TruffleString string) {
            return getRaiseNode().raise(type, string);
        }

        protected PException raise(PythonBuiltinClassType exceptionType) {
            return getRaiseNode().raise(exceptionType);
        }

        protected final PException raise(PythonBuiltinClassType type, TruffleString format, Object... arguments) {
            return getRaiseNode().raise(type, format, arguments);
        }

        protected TruffleString.FromByteArrayNode ensureTsFromByteArray() {
            if (tsFromByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsFromByteArrayNode = insert(TruffleString.FromByteArrayNode.create());
            }
            return tsFromByteArrayNode;
        }

        protected TruffleString.CodePointLengthNode ensureTsCodePointLengthNode() {
            if (tsCodePointLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsCodePointLengthNode = insert(TruffleString.CodePointLengthNode.create());
            }
            return tsCodePointLengthNode;
        }

        protected TruffleString.CodePointAtIndexNode ensureTsCodePointAtIndexNode() {
            if (tsCodePointAtIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsCodePointAtIndexNode = insert(TruffleString.CodePointAtIndexNode.create());
            }
            return tsCodePointAtIndexNode;
        }

        protected TruffleString.FromLongNode ensureTsFromLongNode() {
            if (tsFromLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsFromLongNode = insert(TruffleString.FromLongNode.create());
            }
            return tsFromLongNode;
        }

        protected TruffleString.IndexOfStringNode ensureTsIndexOfStringNode() {
            if (tsIndexOfStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsIndexOfStringNode = insert(TruffleString.IndexOfStringNode.create());
            }
            return tsIndexOfStringNode;
        }

        protected TruffleString.SubstringNode ensureTsSubstringNode() {
            if (tsSubstringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsSubstringNode = insert(TruffleString.SubstringNode.create());
            }
            return tsSubstringNode;
        }

        protected TruffleString.EqualNode ensureTsEqualNode() {
            if (tsEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsEqualNode = insert(TruffleString.EqualNode.create());
            }
            return tsEqualNode;
        }

        protected TruffleString.CopyToByteArrayNode ensureTsCopyToByteArrayNode() {
            if (tsCopyToByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsCopyToByteArrayNode = insert(TruffleString.CopyToByteArrayNode.create());
            }
            return tsCopyToByteArrayNode;
        }

        protected TruffleString.GetCodeRangeNode ensureTsGetCodeRangeNode() {
            if (tsGetCodeRangeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsGetCodeRangeNode = insert(TruffleString.GetCodeRangeNode.create());
            }
            return tsGetCodeRangeNode;
        }

        protected TruffleString.SwitchEncodingNode ensureTsSwitchEncodingNode() {
            if (tsSwitchEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsSwitchEncodingNode = insert(TruffleString.SwitchEncodingNode.create());
            }
            return tsSwitchEncodingNode;
        }

        protected byte[] toBytes(VirtualFrame frame, Object obj) {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode.execute(frame, obj);
        }

        private PyObjectReprAsTruffleStringNode getReprNode() {
            if (reprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reprNode = insert(PyObjectReprAsTruffleStringNode.create());
            }
            return reprNode;
        }

        protected HashingStorageIterator getHashingStorageIterator(HashingStorage s) {
            if (getHashingStorageIteratorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getHashingStorageIteratorNode = insert(HashingStorageGetIterator.create());
            }
            return getHashingStorageIteratorNode.executeCached(s);
        }

        protected HashingStorageIteratorNext ensureHashingStorageIteratorNext() {
            if (hashingStorageItNext == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingStorageItNext = insert(HashingStorageIteratorNext.create());
            }
            return hashingStorageItNext;
        }

        protected HashingStorageIteratorKey ensureHashingStorageIteratorKey() {
            if (hashingStorageItKey == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingStorageItKey = insert(HashingStorageIteratorKey.create());
            }
            return hashingStorageItKey;
        }

        protected HashingStorageIteratorValue ensureHashingStorageIteratorValue() {
            if (hashingStorageItValue == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingStorageItValue = insert(HashingStorageIteratorValue.create());
            }
            return hashingStorageItValue;
        }

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectFactory = insert(PythonObjectFactory.create());
            }
            return objectFactory;
        }

        protected int length(VirtualFrame frame, Object object) {
            if (sizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sizeNode = insert(PyObjectSizeNode.create());
            }
            return sizeNode.executeCached(frame, object);
        }

        protected boolean isIterator(Object iter) {
            if (isIteratorObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isIteratorObjectNode = insert(PyIterCheckNode.create());
            }
            return isIteratorObjectNode.executeCached(iter);
        }

        protected Object encode(Object value, TruffleString encoding, TruffleString errors) {
            if (codecsEncodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codecsEncodeNode = insert(CodecsModuleBuiltinsFactory.CodecsEncodeNodeFactory.create());
            }
            return codecsEncodeNode.execute(value, encoding, errors);
        }

        private CodecsModuleBuiltins.CodecsDecodeNode ensureCodecsDecodeNode() {
            if (codecsDecodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codecsDecodeNode = insert(CodecsModuleBuiltinsFactory.CodecsDecodeNodeFactory.create());
            }
            return codecsDecodeNode;
        }

        protected CodecsModuleBuiltins.CodecsEscapeDecodeNode ensureEscapeDecodeNode() {
            if (codecsEscapeDecodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codecsEscapeDecodeNode = insert(CodecsModuleBuiltinsFactory.CodecsEscapeDecodeNodeFactory.create());
            }
            return codecsEscapeDecodeNode;
        }

        protected Object unicodeRawDecodeEscape(VirtualFrame frame, byte[] bytes, int len) {
            return decode(frame, factory().createBytes(bytes, 0, len), T_CODEC_RAW_UNICODE_ESCAPE);
        }

        protected Object decodeASCII(VirtualFrame frame, byte[] bytes, int len, TruffleString errors) {
            return decode(frame, factory().createBytes(bytes, 0, len), T_CODEC_ASCII, errors);
        }

        protected Object decodeUTF8(VirtualFrame frame, ByteArrayView bytes, int len, TruffleString errors) {
            return decode(frame, factory().createBytes(bytes.getBytes(len), 0, len), T_UTF8, errors);
        }

        protected Object decode(VirtualFrame frame, Object value, TruffleString encoding) {
            return getItem(frame, ensureCodecsDecodeNode().call(frame, value, encoding, T_ERRORS_STRICT, false), 0);
        }

        protected Object decode(VirtualFrame frame, Object value, TruffleString encoding, TruffleString errors) {
            return getItem(frame, ensureCodecsDecodeNode().call(frame, value, encoding, errors, false), 0);
        }

        protected Object escapeDecode(VirtualFrame frame, PythonObjectFactory factory, byte[] data, int offset, int len) {
            if (len == 0) {
                return factory.createEmptyBytes();
            }
            return escapeDecode(frame, PythonUtils.arrayCopyOfRange(data, offset, offset + len));
        }

        protected Object escapeDecode(VirtualFrame frame, byte[] data) {
            return getItem(frame, ensureEscapeDecodeNode().execute(frame, data, T_ERRORS_STRICT), 0);
        }

        protected Object parseInt(VirtualFrame frame, byte[] bytes) {
            return parseInt(frame, PickleUtils.getValidIntString(bytes));
        }

        protected Object parseInt(VirtualFrame frame, TruffleString number) {
            if (intNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                intNode = insert(BuiltinConstructorsFactory.IntNodeFactory.create());
            }
            return intNode.executeWith(frame, number);
        }

        protected CallVarargsMethodNode ensureCallVarargsNode() {
            if (callVarargsMethodNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callVarargsMethodNode = insert(CallVarargsMethodNode.create());
            }
            return callVarargsMethodNode;
        }

        protected ExecutePositionalStarargsNode ensureGetArgsNode() {
            if (getArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getArgsNode = insert(ExecutePositionalStarargsNode.create());
            }
            return getArgsNode;
        }

        protected ExpandKeywordStarargsNode ensureExpandKwArgsNode() {
            if (getKwArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getKwArgsNode = insert(ExpandKeywordStarargsNodeGen.create());
            }
            return getKwArgsNode;
        }

        protected PyObjectLookupAttr getLookupAttrNode() {
            if (lookupAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupAttrNode = insert(PyObjectLookupAttr.create());
            }
            return lookupAttrNode;
        }

        protected Object lookupAttribute(Frame frame, Object receiver, TruffleString name) {
            return getLookupAttrNode().executeCached(frame, receiver, name);
        }

        protected Object lookupAttributeStrict(Frame frame, Object receiver, TruffleString name) {
            Object attr = lookupAttribute(frame, receiver, name);
            if (attr == NO_VALUE) {
                throw raise(TypeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, attr, name);
            }
            return attr;
        }

        protected Object getClass(Object object) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.executeCached(object);
        }

        protected Object getClass(Frame frame, Object object) {
            Object cls = getLookupAttrNode().executeCached(frame, object, T___CLASS__);
            if (cls == NO_VALUE) {
                cls = getClass(object);
            }
            return cls;
        }

        protected Object callNew(VirtualFrame frame, Object tpNew, Object cls) {
            return ensureCallVarargsNode().execute(frame, tpNew, new Object[]{cls}, PKeyword.EMPTY_KEYWORDS);
        }

        protected Object callNew(VirtualFrame frame, Object tpNew, Object cls, Object args) {
            return callNew(frame, tpNew, cls, args, null);
        }

        protected Object callNew(VirtualFrame frame, Object tpNew, Object cls, Object args, Object kwargs) {
            final Object[] stargs = ensureGetArgsNode().executeWith(frame, args);
            final Object[] newArgs = new Object[stargs.length + 1];
            newArgs[0] = cls;
            PythonUtils.arraycopy(stargs, 0, newArgs, 1, stargs.length);
            PKeyword[] keywords = kwargs == null ? PKeyword.EMPTY_KEYWORDS : ensureExpandKwArgsNode().executeCached(kwargs);
            return ensureCallVarargsNode().execute(frame, tpNew, newArgs, keywords);
        }

        protected Object call(VirtualFrame frame, Object method, Object... args) {
            return ensureCallVarargsNode().execute(frame, method, args, PKeyword.EMPTY_KEYWORDS);
        }

        protected Object callStarArgs(VirtualFrame frame, Object method, Object args) {
            return callStarArgsAndKwArgs(frame, method, args, null);
        }

        protected Object callStarArgsAndKwArgs(VirtualFrame frame, Object method, Object args, Object kwargs) {
            PKeyword[] keywords = kwargs == null ? PKeyword.EMPTY_KEYWORDS : ensureExpandKwArgsNode().executeCached(kwargs);
            return ensureCallVarargsNode().execute(frame, method, ensureGetArgsNode().executeWith(frame, args), keywords);
        }

        protected SequenceStorage getSequenceStorage(Object iterator) {
            if (getSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSequenceStorageNode = insert(SequenceNodes.GetSequenceStorageNode.create());
            }
            return getSequenceStorageNode.executeCached(iterator);
        }

        protected TruffleString castToString(Object value) {
            if (castToTruffleStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToTruffleStringNode = insert(CastToTruffleStringNode.create());
            }
            return castToTruffleStringNode.executeCached(value);
        }

        public int asSizeExact(VirtualFrame frame, Object pyNumber) {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode.executeExactCached(frame, pyNumber);
        }

        protected HiddenAttr.ReadNode ensureReadHiddenAttrNode() {
            if (readHiddenAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readHiddenAttributeNode = insert(HiddenAttr.ReadNode.create());
            }
            return readHiddenAttributeNode;
        }

        public PickleState getGlobalState(Python3Core core) {
            final Object state = ensureReadHiddenAttrNode().executeCached(core.lookupType(PythonBuiltinClassType.Pickler), HiddenAttr.PICKLE_STATE, NO_VALUE);
            assert state instanceof PickleState;
            return (PickleState) state;
        }

        protected Object getDictItem(VirtualFrame frame, PDict dict, Object key) {
            if (getHashingStorageItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getHashingStorageItemNode = insert(CachedHashingStorageGetItem.create());
            }
            return getHashingStorageItemNode.execute(frame, dict.getDictStorage(), key);
        }

        protected void setDictItem(VirtualFrame frame, PDict dict, Object key, Object value) {
            HashingStorage newStorage = setHashingStorageItem(frame, dict.getDictStorage(), key, value);
            dict.setDictStorage(newStorage);
        }

        protected HashingStorage setHashingStorageItem(VirtualFrame frame, HashingStorage storage, Object key, Object value) {
            if (setHashingStorageItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setHashingStorageItemNode = insert(HashingStorageSetItem.create());
            }
            return setHashingStorageItemNode.executeCached(frame, storage, key, value);
        }

        protected void pyObjectSetItem(VirtualFrame frame, Object container, Object key, Object value) {
            if (pyObjectSetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyObjectSetItemNode = insert(PyObjectSetItem.create());
            }
            pyObjectSetItemNode.executeCached(frame, container, key, value);
        }

        protected IsBuiltinObjectProfile ensureErrProfile() {
            if (errProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                errProfile = insert(IsBuiltinObjectProfile.create());
            }
            return errProfile;
        }

        protected boolean isBuiltinClass(Object cls, PythonBuiltinClassType type) {
            if (isBuiltinClassProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinClassProfile = insert(InlineIsBuiltinClassProfile.create());
            }
            return isBuiltinClassProfile.profileClassCached(cls, type);
        }

        public Object getNextItem(VirtualFrame frame, Object iterator) {
            return getNextNode.execute(frame, iterator);
        }

        public Object getItem(VirtualFrame frame, SequenceStorage storage, int i) {
            if (getSeqStorageItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSeqStorageItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getSeqStorageItemNode.execute(frame, storage, i);
        }

        public Object getItem(VirtualFrame frame, Object obj, Object slice) {
            return getItemNode.executeCached(frame, obj, slice);
        }

        public Object getItem(VirtualFrame frame, Object obj, int size, int pos, Object defaultValue) {
            if (size > pos) {
                return getItemNode.executeCached(frame, obj, pos);
            }
            return defaultValue;
        }

        public TruffleString asString(Object value) {
            if (value instanceof TruffleString) {
                return (TruffleString) value;
            } else {
                try {
                    return toStringNode.executeCached(value);
                } catch (CannotCastException e) {
                    return null;
                }
            }
        }

        public TruffleString asStringStrict(Object value) {
            if (value instanceof TruffleString) {
                return (TruffleString) value;
            } else {
                try {
                    return toStringNode.executeCached(value);
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, MUST_BE_STR_NOT_P, value);
                }
            }
        }

        public static Pair<Object, Object> getDeepAttribute(VirtualFrame frame, PyObjectLookupAttr lookup, Object obj, TruffleString[] names) {
            Object parent = null;
            Object object = obj;
            for (TruffleString name : names) {
                parent = object;
                object = lookup.executeCached(frame, parent, name);
                if (object == NO_VALUE) {
                    return null;
                }
            }
            return Pair.create(object, parent);
        }

        public TruffleString[] getDottedPath(Object obj, TruffleString name) {
            TruffleString[] dottedPath = StringUtils.split(name, T_DOT, ensureTsCodePointLengthNode(), ensureTsIndexOfStringNode(), ensureTsSubstringNode(), ensureTsEqualNode());
            assert dottedPath.length > 0;
            for (TruffleString subPath : dottedPath) {
                if (ensureTsEqualNode().execute(subPath, T_LOCALS, TS_ENCODING)) {
                    if (obj == null) {
                        throw raise(AttributeError, ErrorMessages.CANT_PICKLE_LOCAL_OBJ_S, name);
                    } else {
                        throw raise(AttributeError, ErrorMessages.CANT_PICKLE_ATTR_S_OF_P, name, obj);
                    }
                }
            }
            return dottedPath;
        }

        public Object getattribute(VirtualFrame frame, Object obj, TruffleString name, boolean allowQualname) {
            if (allowQualname) {
                TruffleString[] dottedPath = getDottedPath(obj, name);
                Pair<Object, Object> result = getDeepAttribute(frame, getLookupAttrNode(), obj, dottedPath);
                if (result != null) {
                    return result.getLeft();
                } else {
                    throw raise(AttributeError, ErrorMessages.CANT_GET_ATTRIBUTE_S_ON_S, name, getReprNode().execute(frame, null, obj));
                }
            } else {
                return lookupAttributeStrict(frame, obj, name);
            }
        }

        protected Pair<TruffleString, TruffleString> get3to2Mapping(VirtualFrame frame, Python3Core core, TruffleString moduleName, TruffleString globalName) {
            PickleState state = getGlobalState(core);
            return getMapping(frame, state.nameMapping3To2, state.importMapping3To2, T_CP_REVERSE_NAME_MAPPING, T_CP_REVERSE_IMPORT_MAPPING, moduleName, globalName);
        }

        protected Pair<TruffleString, TruffleString> get2To3Mapping(VirtualFrame frame, Python3Core core, TruffleString moduleName, TruffleString globalName) {
            PickleState state = getGlobalState(core);
            return getMapping(frame, state.nameMapping2To3, state.importMapping2To3, T_CP_NAME_MAPPING, T_CP_IMPORT_MAPPING, moduleName, globalName);
        }

        private Pair<TruffleString, TruffleString> getMapping(VirtualFrame frame, PDict nameMapping, PDict importMapping,
                        TruffleString nameMappingLabel, TruffleString importMappingLabel, TruffleString moduleName, TruffleString globalName) {
            Object key = factory().createTuple(new Object[]{moduleName, globalName});
            Object item = getDictItem(frame, nameMapping, key);
            if (item != null) {
                if (!(item instanceof PTuple) || length(frame, item) != 2) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_S_NOT_P, nameMappingLabel, "2-tuples", item);
                }
                Object mappedModuleName = getItem(frame, item, 0);
                Object mappedGlobalName = getItem(frame, item, 1);
                if (!PGuards.isString(mappedModuleName) || !PGuards.isString(mappedGlobalName)) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_S_NOT_P_P, nameMappingLabel, "str", mappedModuleName, mappedGlobalName);
                }
                return Pair.create(asString(mappedModuleName), asString(mappedGlobalName));
            } else {
                // Check if the module was renamed.
                item = getDictItem(frame, importMapping, moduleName);
                if (item != null) {
                    if (!PGuards.isString(item)) {
                        throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_S_NOT_P, importMappingLabel, "strings", item);
                    }
                    return Pair.create(asString(item), globalName);
                }
            }

            return Pair.create(moduleName, globalName);
        }

        public Object findClass(VirtualFrame frame, Python3Core core, PUnpickler self, Object moduleName, Object globalName) {
            return findClass(frame, core, self, castToString(moduleName), castToString(globalName));
        }

        public Object findClass(VirtualFrame frame, Python3Core core, PUnpickler self, TruffleString moduleName, TruffleString globalName) {
            // Try to map the old names used in Python 2.x to the new ones used in Python 3.x. We do
            // this only with old pickle protocols and when the user has not disabled the feature.
            TruffleString mName = moduleName;
            TruffleString gName = globalName;
            if (self.getProto() < 3 && self.isFixImports()) {
                final Pair<TruffleString, TruffleString> to3Mapping = get2To3Mapping(frame, core, mName, gName);
                mName = to3Mapping.getLeft();
                gName = to3Mapping.getRight();
            }

            // we don't use PyImport_GetModule here, because it can return partially-initialised
            // modules, which then cause the getattribute to fail.
            Object module = importModule(mName);
            return getattribute(frame, module, gName, self.getProto() >= 4);
        }

        private static boolean checkModule(VirtualFrame frame, PyObjectLookupAttr lookup, Object moduleName, Object module, Object global, TruffleString[] dottedPath) {
            if (module == PNone.NONE) {
                return false;
            }

            assert !(moduleName instanceof String) : "moduleName shouldn't be j.l.String";

            if (moduleName instanceof TruffleString && ((TruffleString) moduleName).equalsUncached(T___MAIN__, TS_ENCODING)) {
                return false;
            }
            final Pair<Object, Object> pair = getDeepAttribute(frame, lookup, module, dottedPath);
            if (pair == null) {
                return false;
            }
            return pair.getLeft() == global;
        }

        public TruffleString whichModule(VirtualFrame frame, PythonContext context, Object global, TruffleString[] dottedPath) {
            Object moduleName = lookupAttribute(frame, global, T___MODULE__);
            if (moduleName != NO_VALUE) {
                // In some rare cases (e.g., bound methods of extension types), __module__ can be
                // None. If it is so, then search sys.modules for the module of global
                if (moduleName != PNone.NONE) {
                    return asStringStrict(moduleName);
                }
            }

            // Fallback on walking sys.modules
            final PDict sysModules = context.getSysModules();
            if (sysModules == null) {
                throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.UNABLE_TO_GET_S, T_SYS_MODULES);
            }

            final HashingStorage storage = sysModules.getDictStorage();
            HashingStorageIterator it = getHashingStorageIterator(storage);
            HashingStorageIteratorNext nextNode = ensureHashingStorageIteratorNext();
            HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
            HashingStorageIteratorValue getValueNode = ensureHashingStorageIteratorValue();
            while (nextNode.executeCached(storage, it)) {
                Object value = getValueNode.executeCached(storage, it);
                Object key = getKeyNode.executeCached(storage, it);
                if (checkModule(frame, getLookupAttrNode(), moduleName, value, global, dottedPath)) {
                    return asStringStrict(key);
                }
            }
            // If no module is found, use __main__
            return T___MAIN__;
        }
    }
}
