/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LookupError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NAME;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CODECS_TRUFFLE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CODECS_TRUFFLE;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_TEXT_ENCODING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_DECODE;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsDecodeNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.CodecsEncodeNode;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CallApplyNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CodecDecodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.CodecInitNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.EncodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.IncrementalDecodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.IncrementalEncodeNodeFactory;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltinsFactory.StreamDecodeNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotVarargs;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__CODECS_TRUFFLE)
public final class CodecsTruffleModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_CODEC_INFO_NAME = tsLiteral("CodecInfo");
    private static final TruffleString T_CODEC = tsLiteral("Codec");
    private static final TruffleString T_INCREMENTAL_ENCODER = tsLiteral("IncrementalEncoder");
    private static final TruffleString T_BUFFERED_INCREMENTAL_DECODER = tsLiteral("BufferedIncrementalDecoder");
    private static final TruffleString T_STREAM_READER = tsLiteral("StreamReader");
    private static final TruffleString T_STREAM_WRITER = tsLiteral("StreamWriter");

    private static final String J_BUFFER_DECODE = "_buffer_decode";

    private static final TruffleString T_TRUFFLE_CODEC = tsLiteral("TruffleCodec");
    private static final TruffleString T_TRUFFLE_INCREMENTAL_ENCODER = tsLiteral("TruffleIncrementalEncoder");
    private static final TruffleString T_TRUFFLE_INCREMENTAL_DECODER = tsLiteral("TruffleIncrementalDecoder");
    private static final TruffleString T_TRUFFLE_STREAM_WRITER = tsLiteral("TruffleStreamWriter");
    private static final TruffleString T_TRUFFLE_STREAM_READER = tsLiteral("TruffleStreamReader");
    private static final TruffleString T_APPLY_ENCODING = tsLiteral("ApplyEncoding");

    private static final TruffleString T_ATTR_ENCODING = tsLiteral("encoding");
    private static final TruffleString T_ATTR_ERRORS = tsLiteral("errors");
    private static final TruffleString T_ATTR_FN = tsLiteral("fn");
    public static final TruffleString T_INCREMENTALENCODER = tsLiteral("incrementalencoder");
    public static final TruffleString T_INCREMENTALDECODER = tsLiteral("incrementaldecoder");
    private static final TruffleString T_STREAMREADER = tsLiteral("streamreader");
    private static final TruffleString T_STREAMWRITER = tsLiteral("streamwriter");
    private static final TruffleString T_CODECS = tsLiteral("codecs");

    private PythonClass truffleCodecClass;
    private PythonClass truffleIncrementalEncoderClass;
    private PythonClass truffleIncrementalDecoderClass;
    private PythonClass truffleStreamReaderClass;
    private PythonClass truffleStreamWriterClass;
    private PythonClass applyEncodingClass;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    private static PythonClass initClass(TruffleString className, TruffleString superClassName, BuiltinDescr[] descrs, PythonModule codecsTruffleModule, PythonModule codecsModule,
                    PythonLanguage language) {
        PythonAbstractClass superClass = (PythonAbstractClass) codecsModule.getAttribute(superClassName);
        return initClass(className, superClass, descrs, codecsTruffleModule, language);
    }

    private static PythonClass initClass(TruffleString className, PythonAbstractClass superClass, BuiltinDescr[] descrs, PythonModule codecsTruffleModule, PythonLanguage language) {
        PythonClass clazz = PFactory.createPythonClassAndFixupSlots(language, className, superClass, new PythonAbstractClass[]{superClass});
        for (BuiltinDescr d : descrs) {
            PythonUtils.createMethod(language, clazz, d.nodeFactory(), d.enclosingType ? clazz : null, 1);
        }
        clazz.setAttribute(T___MODULE__, T__CODECS_TRUFFLE);
        clazz.setAttribute(T___QUALNAME__, T__CODECS_TRUFFLE);
        codecsTruffleModule.setAttribute(className, clazz);
        return clazz;
    }

    private record BuiltinDescr(NodeFactory<? extends PythonBuiltinBaseNode> nodeFactory, boolean enclosingType) {
    }

    @TruffleBoundary
    static PTuple codecsInfo(PythonModule self, TruffleString encoding, PythonContext context) {
        PythonModule codecsModule = AbstractImportNode.importModule(T_CODECS);
        CodecsTruffleModuleBuiltins codecsTruffleBuiltins = (CodecsTruffleModuleBuiltins) self.getBuiltins();
        if (self.getAttribute(T_TRUFFLE_CODEC) instanceof PNone) {
            initCodecClasses(self, codecsModule, context);
        }

        PythonLanguage language = context.getLanguage();
        // encode/decode methods for codecs.CodecInfo
        PythonObject truffleCodec = createPythonObject(language, codecsTruffleBuiltins.truffleCodecClass);
        truffleCodec.setAttribute(T_ATTR_ENCODING, encoding);
        Object encodeMethod = PyObjectGetAttr.executeUncached(truffleCodec, T_ENCODE);
        Object decodeMethod = PyObjectGetAttr.executeUncached(truffleCodec, T_DECODE);

        // incrementalencoder factory function for codecs.CodecInfo
        PythonObject tie = createPythonObject(language, codecsTruffleBuiltins.applyEncodingClass);
        tie.setAttribute(T_ATTR_FN, codecsTruffleBuiltins.truffleIncrementalEncoderClass);
        tie.setAttribute(T_ATTR_ENCODING, encoding);

        // incrementaldecoder factory function for codecs.CodecInfo
        PythonObject tid = createPythonObject(language, codecsTruffleBuiltins.applyEncodingClass);
        tid.setAttribute(T_ATTR_FN, codecsTruffleBuiltins.truffleIncrementalDecoderClass);
        tid.setAttribute(T_ATTR_ENCODING, encoding);

        // streamwriter factory function for codecs.CodecInfo
        PythonObject sr = createPythonObject(language, codecsTruffleBuiltins.applyEncodingClass);
        sr.setAttribute(T_ATTR_FN, codecsTruffleBuiltins.truffleStreamReaderClass);
        sr.setAttribute(T_ATTR_ENCODING, encoding);

        // streamreader factory function for codecs.CodecInfo
        PythonObject sw = createPythonObject(language, codecsTruffleBuiltins.applyEncodingClass);
        sw.setAttribute(T_ATTR_FN, codecsTruffleBuiltins.truffleStreamWriterClass);
        sw.setAttribute(T_ATTR_ENCODING, encoding);

        // codecs.CodecInfo
        PythonAbstractClass codecInfoClass = (PythonAbstractClass) codecsModule.getAttribute(T_CODEC_INFO_NAME);
        return (PTuple) CallNode.getUncached().execute(null, codecInfoClass, new Object[]{}, createCodecInfoArgs(encoding, encodeMethod, decodeMethod, tie, tid, sr, sw));
    }

    private static PythonObject createPythonObject(PythonLanguage language, PythonClass cls) {
        return PFactory.createPythonObject(language, cls, cls.getInstanceShape());
    }

    private static PKeyword[] createCodecInfoArgs(TruffleString encoding, Object encodeMethod, Object decodeMethod, PythonObject tie, PythonObject tid, PythonObject sr, PythonObject sw) {
        return new PKeyword[]{
                        new PKeyword(T_NAME, encoding),
                        new PKeyword(T_ENCODE, encodeMethod),
                        new PKeyword(T_DECODE, decodeMethod),
                        new PKeyword(T_INCREMENTALENCODER, tie),
                        new PKeyword(T_INCREMENTALDECODER, tid),
                        new PKeyword(T_STREAMREADER, sr),
                        new PKeyword(T_STREAMWRITER, sw)
        };
    }

    /**
     * create classes based on types declared in lib/3/codes.py
     */
    // @formatter:off
    private static void initCodecClasses(PythonModule codecsTruffleModule, PythonModule codecsModule, PythonContext context) {

        // TODO - the incremental codec and reader/writer won't work well with stateful
        // encodings, like some of the CJK encodings
        CodecsTruffleModuleBuiltins codecsTruffleBuiltins = (CodecsTruffleModuleBuiltins) codecsTruffleModule.getBuiltins();
        PythonLanguage language = context.getLanguage();

        // class TruffleCodec(codecs.Codec):
        //     def encode(self, input, errors='strict'):
        //         return _codecs.__truffle_encode__(input, self.encoding, errors)
        //     def decode(self, input, errors='strict'):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors, True)
        codecsTruffleBuiltins.truffleCodecClass = initClass(T_TRUFFLE_CODEC, (PythonClass) codecsModule.getAttribute(T_CODEC),
                        new BuiltinDescr[]{
                                        new BuiltinDescr(EncodeNodeFactory.getInstance(), false),
                                        new BuiltinDescr(CodecDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, language);

        // class TruffleIncrementalEncoder(codecs.IncrementalEncoder):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def encode(self, input, final=False):
        //         return _codecs.__truffle_encode__(input, self.encoding, self.errors)[0]
        codecsTruffleBuiltins.truffleIncrementalEncoderClass = initClass(T_TRUFFLE_INCREMENTAL_ENCODER, T_INCREMENTAL_ENCODER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(IncrementalEncodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language);

        // class TruffleIncrementalDecoder(codecs.BufferedIncrementalDecoder):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def _buffer_decode(self, input, errors, final):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors, final)
        codecsTruffleBuiltins.truffleIncrementalDecoderClass = initClass(T_TRUFFLE_INCREMENTAL_DECODER, T_BUFFERED_INCREMENTAL_DECODER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(IncrementalDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language);

        // class TruffleStreamWriter(codecs.StreamWriter):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def encode(self, input, errors='strict'):
        //         return _codecs.__truffle_encode__(input, self.encoding, errors)
        codecsTruffleBuiltins.truffleStreamWriterClass = initClass(T_TRUFFLE_STREAM_WRITER, T_STREAM_WRITER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(EncodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language);

        // class TruffleStreamReader(codecs.StreamReader):
        //     def __init__(self, encoding, *args, **kwargs):
        //         super().__init__(*args, **kwargs)
        //         self.encoding = encoding
        //     def decode(self, input, errors='strict'):
        //         return _codecs.__truffle_decode__(input, self.encoding, errors)
        codecsTruffleBuiltins.truffleStreamReaderClass = initClass(T_TRUFFLE_STREAM_READER, T_STREAM_READER,
                        new BuiltinDescr[]{
                                        new BuiltinDescr(CodecInitNodeFactory.getInstance(), false),
                                        new BuiltinDescr(StreamDecodeNodeFactory.getInstance(), true)},
                        codecsTruffleModule, codecsModule, language);

        // serves as factory function for CodecInfo-s incrementalencoder/decode and streamwriter/reader
        // class apply_encoding:
        //     def __call__(self, *args, **kwargs):
        //         return self.fn(self.encoding, *args, **kwargs)
        codecsTruffleBuiltins.applyEncodingClass = initClass(T_APPLY_ENCODING, context.lookupType(PythonBuiltinClassType.PythonObject),
                        new BuiltinDescr[]{new BuiltinDescr(CallApplyNodeFactory.getInstance(), false)},
                        codecsTruffleModule, language);
    }
    // @formatter:on

    @Builtin(name = J___INIT__, autoRegister = false, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class CodecInitNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object init(VirtualFrame frame, PythonObject self, Object[] args, PKeyword[] kw,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttr setAttrNode,
                        @Cached GetPythonObjectClassNode getClass,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached GetCachedTpSlotsNode getSlots,
                        @Cached TpSlotVarargs.CallSlotTpInitNode callInit) {
            assert args.length > 0;
            Object base = getBaseClassNode.execute(inliningTarget, getClass.execute(inliningTarget, self));
            TpSlots baseSlots = getSlots.execute(inliningTarget, base);
            callInit.execute(frame, inliningTarget, baseSlots.tp_init(), self, PythonUtils.arrayCopyOfRange(args, 1, args.length), kw);
            setAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING, args[0]);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___CALL__, autoRegister = false, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class CallApplyNode extends PythonVarargsBuiltinNode {
        @Specialization
        Object call(VirtualFrame frame, PythonObject self, Object[] args, PKeyword[] kw,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode) {
            Object[] callArgs = new Object[args.length + 1];
            callArgs[0] = getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING);
            PythonUtils.arraycopy(args, 0, callArgs, 1, args.length);
            return callNode.execute(frame, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_FN), callArgs, kw);
        }
    }

    @Builtin(name = J_ENCODE, autoRegister = false, minNumOfPositionalArgs = 2, parameterNames = {"self", "input", "errors"})
    @GenerateNodeFactory
    protected abstract static class EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, PythonObject self, Object input, Object errors,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, input, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING), errors);
        }
    }

    @Builtin(name = J_DECODE, autoRegister = false, minNumOfPositionalArgs = 1, parameterNames = {"self", "input", "errors"})
    @GenerateNodeFactory
    protected abstract static class CodecDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, input, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING), errors, true);
        }
    }

    @Builtin(name = J_ENCODE, autoRegister = false, minNumOfPositionalArgs = 2, parameterNames = {"self", "input", "final"})
    @GenerateNodeFactory
    protected abstract static class IncrementalEncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, PythonObject self, Object input, @SuppressWarnings("unused") Object ffinal,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsEncodeNode encode,
                        @Cached PyTupleGetItem getItemNode) {
            PTuple result = (PTuple) encode.execute(frame, input, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING), getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ERRORS));
            return getItemNode.execute(inliningTarget, result, 0);
        }
    }

    @Builtin(name = J_BUFFER_DECODE, autoRegister = false, minNumOfPositionalArgs = 1, parameterNames = {"self", "input", "errors", "final"})
    @GenerateNodeFactory
    protected abstract static class IncrementalDecodeNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors, Object ffinal,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, input, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING), errors, ffinal);
        }
    }

    @Builtin(name = J_DECODE, autoRegister = false, minNumOfPositionalArgs = 1, parameterNames = {"self", "input", "errors"})
    @GenerateNodeFactory
    protected abstract static class StreamDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, PythonObject self, Object input, Object errors,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, input, getAttrNode.execute(frame, inliningTarget, self, T_ATTR_ENCODING), errors, false);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 64 -> 45
    public abstract static class LookupTextEncoding extends PNodeWithContext {

        public static final TruffleString T_IS_TEXT_ENCODING = tsLiteral("_is_text_encoding");

        public abstract Object execute(Frame frame, TruffleString encoding, TruffleString alternateCommand);

        @Specialization
        Object lookup(VirtualFrame frame, TruffleString encoding, TruffleString alternateCommand,
                        @Bind Node inliningTarget,
                        @Cached CodecsModuleBuiltins.PyCodecLookupNode lookupNode,
                        @Cached PyObjectGetAttr getAttributeNode,
                        @Cached PRaiseNode raiseNode) {
            PTuple codecInfo = lookupNode.execute(frame, inliningTarget, encoding);
            Object isTextObj = getAttributeNode.execute(frame, inliningTarget, codecInfo, T_IS_TEXT_ENCODING);
            if (!((isTextObj instanceof Boolean) && (boolean) isTextObj)) {
                throw raiseNode.raise(inliningTarget, LookupError, IS_NOT_TEXT_ENCODING, encoding, alternateCommand);
            }
            return codecInfo;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 92 -> 75
    public abstract static class GetEncodingNode extends PNodeWithContext {

        public static final TruffleString T_GETENCODING = tsLiteral("getencoding");

        public abstract TruffleString execute(Frame frame);

        @Specialization
        TruffleString getpreferredencoding(VirtualFrame frame,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodNode,
                        @Cached PyObjectStrAsTruffleStringNode strNode) {

            Object locale = AbstractImportNode.importModule(BuiltinNames.T_LOCALE);
            Object e = callMethodNode.execute(frame, inliningTarget, locale, T_GETENCODING);
            return strNode.execute(frame, inliningTarget, e);
        }
    }

    @GenerateUncached
    @GenerateCached
    @GenerateInline(false) // Used only lazily
    @ImportStatic(PGuards.class)
    public abstract static class MakeIncrementalcodecNode extends PNodeWithContext {

        public abstract Object execute(VirtualFrame frame, Object codecInfo, Object errors, TruffleString attrName);

        @Specialization
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, @SuppressWarnings("unused") PNone errors, TruffleString attrName,
                        @Bind Node inliningTarget,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, codecInfo, attrName);
        }

        @Specialization(guards = "!isPNone(errors)")
        static Object getIncEncoder(VirtualFrame frame, Object codecInfo, Object errors, TruffleString attrName,
                        @Bind Node inliningTarget,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, codecInfo, attrName, errors);
        }
    }
}
