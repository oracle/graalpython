/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltinsFactory;
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
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyLongFromUnicodeObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNodeGen;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PicklerNodes {
    @GenerateInline(false)
    @ImportStatic(PythonUtils.class)
    abstract static class LookupGlobalAttributeNode extends Node {
        abstract Object execute(VirtualFrame frame, Object receiver, TruffleString name);

        // Unpickling decodes fresh strings. Give attribute lookup a stable name identity without
        // caching the attribute value or interning arbitrary pickle contents.
        @Specialization(guards = "equal.execute(name, cachedName, TS_ENCODING)", limit = "3")
        static Object cached(VirtualFrame frame, Object receiver, TruffleString name,
                        @Cached("name") TruffleString cachedName,
                        @Cached TruffleString.EqualNode equal,
                        @Exclusive @Cached(inline = false) PyObjectLookupAttr lookup) {
            return lookup.executeCached(frame, receiver, cachedName);
        }

        @Specialization(replaces = "cached")
        static Object generic(VirtualFrame frame, Object receiver, TruffleString name,
                        @Exclusive @Cached(inline = false) PyObjectLookupAttr lookup) {
            return lookup.executeCached(frame, receiver, name);
        }
    }

    abstract static class BasePickleNode extends Node {
        private static final TruffleString T_LOCALS = tsLiteral("<locals>");
        public static final TruffleString T_CODEC_RAW_UNICODE_ESCAPE = tsLiteral("raw_unicode_escape");
        public static final TruffleString T_CODEC_BYTES = tsLiteral("bytes");
        public static final TruffleString T_CODEC_ASCII = tsLiteral("ascii");
        public static final TruffleString T_ERRORS_SURROGATEPASS = tsLiteral("surrogatepass");
        public static final TruffleString T_ERRORS_STRICT = tsLiteral("strict");

        @SuppressWarnings("FieldMayBeFinal") @Child private PyObjectGetItem getItemNode = PyObjectGetItem.create();
        @SuppressWarnings("FieldMayBeFinal") @Child private PyIterNextNode getNextNode = PyIterNextNode.create();
        @Child CastToTruffleStringNode toStringNode = CastToTruffleStringNode.create();

        @Child private HiddenAttr.ReadNode readHiddenAttributeNode;
        @Child private IsBuiltinObjectProfile errProfile;
        @Child private InlineIsBuiltinClassProfile isBuiltinClassProfile;
        @Child private HashingStorageSetItem setHashingStorageItemNode;
        @Child private PyObjectSetItem pyObjectSetItemNode;
        @Child private CachedHashingStorageGetItem getHashingStorageItemNode;
        @Child private SequenceStorageNodes.GetItemScalarNode getSeqStorageItemNode;
        @Child private PyNumberAsSizeNode asSizeNode;
        @Child private SequenceNodes.GetSequenceStorageNode getSequenceStorageNode;
        @Child private CallNode callNode;
        @Child private ExecutePositionalStarargsNode getArgsNode;
        @Child private ExpandKeywordStarargsNode getKwArgsNode;
        @Child private PyLongFromUnicodeObject pyLongFromUnicodeObject;
        @Child private CodecsModuleBuiltins.CodecsDecodeNode codecsDecodeNode;
        @Child private CodecsModuleBuiltins.CodecsEscapeDecodeNode codecsEscapeDecodeNode;
        @Child private CodecsModuleBuiltins.CodecsEncodeNode codecsEncodeNode;
        @Child private PyIterCheckNode isIteratorObjectNode;
        @Child private GetClassNode getClassNode;
        @Child private PyObjectSizeNode sizeNode;
        @Child private PyObjectLookupAttr lookupAttrNode;
        @Child private LookupGlobalAttributeNode lookupGlobalAttrNode;
        @Child private BytesNodes.ToBytesNode toBytesNode;
        @Child private TruffleString.FromByteArrayNode tsFromByteArrayNode;
        @Child private TruffleString.FromByteArrayWithCompactionUTF32Node tsFromByteArrayWithCompactionNode;
        @Child private TruffleString.CodePointLengthNode tsCodePointLengthNode;
        @Child private TruffleString.IndexOfCodePointNode tsIndexOfCodePointNode;
        @Child private TruffleString.SubstringNode tsSubstringNode;
        @Child private TruffleString.EqualNode tsEqualNode;
        @Child private TruffleString.SwitchEncodingNode tsSwitchEncodingNode;
        @Child private TruffleString.IsValidNode tsIsValidNode;
        @Child private HashingStorageGetIterator getHashingStorageIteratorNode;
        @Child private HashingStorageIteratorNext hashingStorageItNext;
        @Child private HashingStorageIteratorKey hashingStorageItKey;
        @Child private HashingStorageIteratorValue hashingStorageItValue;
        protected final BranchProfile errorProfile = BranchProfile.create();

        protected PException raise(PythonBuiltinClassType type, TruffleString string) {
            errorProfile.enter();
            return PRaiseNode.raiseStatic(this, type, string);
        }

        protected PException raise(PythonBuiltinClassType exceptionType) {
            errorProfile.enter();
            return PRaiseNode.raiseStatic(this, exceptionType);
        }

        protected final PException raise(PythonBuiltinClassType type, TruffleString format, Object... arguments) {
            errorProfile.enter();
            return PRaiseNode.raiseStatic(this, type, format, arguments);
        }

        protected TruffleString.FromByteArrayNode ensureTsFromByteArray() {
            if (tsFromByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsFromByteArrayNode = insert(TruffleString.FromByteArrayNode.create());
            }
            return tsFromByteArrayNode;
        }

        protected TruffleString.FromByteArrayWithCompactionUTF32Node ensureTsFromByteArrayWithCompaction() {
            if (tsFromByteArrayWithCompactionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsFromByteArrayWithCompactionNode = insert(TruffleString.FromByteArrayWithCompactionUTF32Node.create());
            }
            return tsFromByteArrayWithCompactionNode;
        }

        protected TruffleString.CodePointLengthNode ensureTsCodePointLengthNode() {
            if (tsCodePointLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsCodePointLengthNode = insert(TruffleString.CodePointLengthNode.create());
            }
            return tsCodePointLengthNode;
        }

        protected TruffleString.IndexOfCodePointNode ensureTsIndexOfCodePointNode() {
            if (tsIndexOfCodePointNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsIndexOfCodePointNode = insert(TruffleString.IndexOfCodePointNode.create());
            }
            return tsIndexOfCodePointNode;
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

        protected TruffleString.SwitchEncodingNode ensureTsSwitchEncodingNode() {
            if (tsSwitchEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsSwitchEncodingNode = insert(TruffleString.SwitchEncodingNode.create());
            }
            return tsSwitchEncodingNode;
        }

        protected TruffleString.IsValidNode ensureTsIsValidNode() {
            if (tsIsValidNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsIsValidNode = insert(TruffleString.IsValidNode.create());
            }
            return tsIsValidNode;
        }

        protected byte[] toBytes(VirtualFrame frame, Object obj) {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode.execute(frame, obj);
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

        protected Object encode(VirtualFrame frame, Object value, TruffleString encoding, TruffleString errors) {
            if (codecsEncodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                codecsEncodeNode = insert(CodecsModuleBuiltinsFactory.CodecsEncodeNodeFactory.create());
            }
            return codecsEncodeNode.execute(frame, value, encoding, errors);
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
            return decode(frame, PFactory.createBytes(PythonLanguage.get(this), bytes, len), T_CODEC_RAW_UNICODE_ESCAPE);
        }

        protected Object decodeASCII(VirtualFrame frame, byte[] bytes, int len, TruffleString errors) {
            return decode(frame, PFactory.createBytes(PythonLanguage.get(this), bytes, len), T_CODEC_ASCII, errors);
        }

        protected Object decodeUTF8(VirtualFrame frame, byte[] bytes, int offset, int len, TruffleString errors) {
            TruffleString utf8 = ensureTsFromByteArray().execute(bytes, offset, len, TruffleString.Encoding.UTF_8, true);
            if (ensureTsIsValidNode().execute(utf8, TruffleString.Encoding.UTF_8)) {
                return ensureTsSwitchEncodingNode().execute(utf8, TS_ENCODING);
            }
            return decode(frame, PFactory.createBytes(PythonLanguage.get(this), PythonUtils.arrayCopyOfRange(bytes, offset, offset + len)), T_UTF8, errors);
        }

        protected Object decode(VirtualFrame frame, Object value, TruffleString encoding) {
            return getItem(frame, ensureCodecsDecodeNode().call(frame, value, encoding, T_ERRORS_STRICT, false), 0);
        }

        protected Object decode(VirtualFrame frame, Object value, TruffleString encoding, TruffleString errors) {
            return getItem(frame, ensureCodecsDecodeNode().call(frame, value, encoding, errors, false), 0);
        }

        protected Object escapeDecode(VirtualFrame frame, byte[] data) {
            return getItem(frame, ensureEscapeDecodeNode().execute(frame, data, T_ERRORS_STRICT), 0);
        }

        protected Object parseInt(byte[] bytes) {
            return parseInt(PickleUtils.getValidIntString(bytes));
        }

        protected Object parseInt(TruffleString number) {
            if (pyLongFromUnicodeObject == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongFromUnicodeObject = insert(PyLongFromUnicodeObject.create());
            }
            return pyLongFromUnicodeObject.executeCached(number, 0);
        }

        protected CallNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
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

        protected LookupGlobalAttributeNode getLookupGlobalAttrNode() {
            if (lookupGlobalAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupGlobalAttrNode = insert(PicklerNodesFactory.LookupGlobalAttributeNodeGen.create());
            }
            return lookupGlobalAttrNode;
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

        protected Object call(VirtualFrame frame, Object method, Object... args) {
            return ensureCallNode().execute(frame, method, args, PKeyword.EMPTY_KEYWORDS);
        }

        protected Object callStarArgs(VirtualFrame frame, Object method, Object args) {
            return callStarArgsAndKwArgs(frame, method, args, null);
        }

        protected Object callStarArgsAndKwArgs(VirtualFrame frame, Object method, Object args, Object kwargs) {
            PKeyword[] keywords = kwargs == null ? PKeyword.EMPTY_KEYWORDS : ensureExpandKwArgsNode().executeCached(kwargs);
            return ensureCallNode().execute(frame, method, ensureGetArgsNode().executeWith(frame, args), keywords);
        }

        protected SequenceStorage getSequenceStorage(Object iterator) {
            if (getSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSequenceStorageNode = insert(SequenceNodes.GetSequenceStorageNode.create());
            }
            return getSequenceStorageNode.executeCached(iterator);
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
            return getNextNode.executeCached(frame, iterator);
        }

        public Object getItem(SequenceStorage storage, int i) {
            if (getSeqStorageItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSeqStorageItemNode = insert(SequenceStorageNodes.GetItemScalarNode.create());
            }
            return getSeqStorageItemNode.executeCached(storage, i);
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

        public static Pair<Object, Object> getDeepAttribute(VirtualFrame frame, LookupGlobalAttributeNode lookup, Object obj, TruffleString[] names) {
            Object parent = null;
            Object object = obj;
            for (int i = 0; i < names.length; i++) {
                parent = object;
                object = lookup.execute(frame, parent, names[i]);
                if (object == NO_VALUE) {
                    LoopNode.reportLoopCount(lookup, i);
                    return null;
                }
            }
            LoopNode.reportLoopCount(lookup, names.length);
            return Pair.create(object, parent);
        }

        private TruffleString[] splitDottedPath(TruffleString name) {
            int length = ensureTsCodePointLengthNode().execute(name, TS_ENCODING);
            TruffleString.IndexOfCodePointNode indexOf = ensureTsIndexOfCodePointNode();
            int firstDot = length == 0 ? -1 : indexOf.execute(name, '.', 0, length, TS_ENCODING);
            if (firstDot < 0) {
                return new TruffleString[]{name};
            }

            // Count the components so that we can allocate the result without growing or copying it.
            int parts = 2;
            int start = firstDot + 1;
            while (start < length) {
                int dot = indexOf.execute(name, '.', start, length, TS_ENCODING);
                if (dot < 0) {
                    break;
                }
                parts++;
                start = dot + 1;
            }
            TruffleString[] dottedPath = new TruffleString[parts];
            TruffleString.SubstringNode substring = ensureTsSubstringNode();
            start = 0;
            int part = 0;
            for (int dot = firstDot; dot >= 0; dot = start < length ? indexOf.execute(name, '.', start, length, TS_ENCODING) : -1) {
                dottedPath[part++] = substring.execute(name, start, dot - start, TS_ENCODING, false);
                start = dot + 1;
            }
            dottedPath[part] = substring.execute(name, start, length - start, TS_ENCODING, false);
            return dottedPath;
        }

        public TruffleString[] getDottedPath(Object obj, TruffleString name) {
            TruffleString[] dottedPath = splitDottedPath(name);
            for (int i = 0; i < dottedPath.length; i++) {
                if (ensureTsEqualNode().execute(dottedPath[i], T_LOCALS, TS_ENCODING)) {
                    if (obj == null) {
                        throw raise(AttributeError, ErrorMessages.CANT_PICKLE_LOCAL_OBJ_S, name);
                    } else {
                        throw raise(AttributeError, ErrorMessages.CANT_PICKLE_ATTR_S_OF_P, name, obj);
                    }
                }
            }
            LoopNode.reportLoopCount(this, dottedPath.length);
            return dottedPath;
        }

        protected Pair<TruffleString, TruffleString> getMapping(VirtualFrame frame, PDict nameMapping, PDict importMapping,
                        TruffleString nameMappingLabel, TruffleString importMappingLabel, TruffleString moduleName, TruffleString globalName) {
            Object key = PFactory.createTuple(PythonLanguage.get(this), new Object[]{moduleName, globalName});
            Object item = getDictItem(frame, nameMapping, key);
            if (item != null) {
                if (!(item instanceof PTuple) || ((PTuple) item).getSequenceStorage().length() != 2) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.S_SHOULD_BE_S_NOT_P, nameMappingLabel, "2-tuples", item);
                }
                SequenceStorage storage = ((PTuple) item).getSequenceStorage();
                Object mappedModuleName = getItem(storage, 0);
                Object mappedGlobalName = getItem(storage, 1);
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
    }
}
