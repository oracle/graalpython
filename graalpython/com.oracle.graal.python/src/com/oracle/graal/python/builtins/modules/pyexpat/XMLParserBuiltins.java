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
package com.oracle.graal.python.builtins.modules.pyexpat;

import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.DefaultHandler;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked0Node;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.XMLParser)
public final class XMLParserBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = XMLParserBuiltinsSlotsGen.SLOTS;
    private static final TruffleString T_BUFFER_TEXT = tsLiteral("buffer_text");
    private static final TruffleString T_NAMESPACE_PREFIXES = tsLiteral("namespace_prefixes");
    private static final TruffleString T_ORDERED_ATTRIBUTES = tsLiteral("ordered_attributes");
    private static final TruffleString T_SPECIFIED_ATTRIBUTES = tsLiteral("specified_attributes");
    private static final TruffleString T_BUFFER_SIZE = tsLiteral("buffer_size");
    private static final TruffleString T_RETURNS_UNICODE = tsLiteral("returns_unicode");
    private static final TruffleString T_CURRENT_BYTE_INDEX = tsLiteral("CurrentByteIndex");
    private static final TruffleString T_CURRENT_LINE_NUMBER = tsLiteral("CurrentLineNumber");
    private static final TruffleString T_CURRENT_COLUMN_NUMBER = tsLiteral("CurrentColumnNumber");
    private static final TruffleString T_READ = tsLiteral("read");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return XMLParserBuiltinsFactory.getFactories();
    }

    static Object createParser(Node inliningTarget, Node raisingNode, Object namespaceSeparatorObj) {
        TruffleString sep = null;
        if (namespaceSeparatorObj != PNone.NONE && namespaceSeparatorObj != PNone.NO_VALUE) {
            if (!(namespaceSeparatorObj instanceof TruffleString ts)) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.TypeError,
                                toTruffleStringUncached("ParserCreate() argument 'namespace_separator' must be str or None, not int"));
            }
            int len = ts.codePointLengthUncached(TruffleString.Encoding.UTF_32);
            if (len > 1) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.ValueError,
                                toTruffleStringUncached("namespace_separator must be at most one character, omitted, or None"));
            }
            sep = ts;
        }
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        PXMLParser parser = PFactory.createXMLParser(PythonBuiltinClassType.XMLParser, PythonBuiltinClassType.XMLParser.getInstanceShape(language), sep);
        parser.setAttribute(T_BUFFER_TEXT, false);
        parser.setAttribute(T_NAMESPACE_PREFIXES, false);
        parser.setAttribute(T_ORDERED_ATTRIBUTES, false);
        parser.setAttribute(T_SPECIFIED_ATTRIBUTES, false);
        parser.setAttribute(T_BUFFER_SIZE, parser.bufferSize);
        parser.setAttribute(T_CURRENT_BYTE_INDEX, 0);
        parser.setAttribute(T_CURRENT_LINE_NUMBER, 1);
        parser.setAttribute(T_CURRENT_COLUMN_NUMBER, 0);
        return parser;
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "xmlparser", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doIt(Object cls, @SuppressWarnings("unused") Object arg1, Object arg2,
                        @Bind Node inliningTarget) {
            return createParser(inliningTarget, this, arg2 == PNone.NO_VALUE ? PNone.NONE : arg2);
        }
    }

    @Builtin(name = "Parse", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ParseNode extends PythonTernaryBuiltinNode {
        @Specialization
        int parse(PXMLParser self, Object data, Object isFinalObj) {
            boolean isFinal = isTrue(isFinalObj);
            if (self.finished) {
                throw raiseExpatError(this, toTruffleStringUncached("parsing finished"), PXMLParser.XML_ERROR_FINISHED, 0, 1, 0);
            }
            byte[] chunk = toBytes(data, this);
            byte[] merged = Arrays.copyOf(self.data, self.data.length + chunk.length);
            System.arraycopy(chunk, 0, merged, self.data.length, chunk.length);
            self.data = merged;

            if (!isFinal && self.reparseDeferralEnabled) {
                return 1;
            }

            try {
                parseNow(self, !isFinal);
                if (isFinal) {
                    self.finished = true;
                }
                return 1;
            } catch (RuntimeException e) {
                if (!isFinal) {
                    return 1;
                }
                throw e;
            }
        }
    }

    @Builtin(name = "ParseFile", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ParseFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        int parseFile(PXMLParser self, Object file) {
            while (true) {
                Object r = PyObjectCallMethodObjArgs.executeUncached(file, T_READ);
                byte[] b = toBytes(r, this);
                if (b.length == 0) {
                    break;
                }
                byte[] merged = Arrays.copyOf(self.data, self.data.length + b.length);
                System.arraycopy(b, 0, merged, self.data.length, b.length);
                self.data = merged;
            }
            parseNow(self, false);
            self.finished = true;
            return 1;
        }
    }

    @Builtin(name = "SetParamEntityParsing", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetParamEntityParsingNode extends PythonBinaryBuiltinNode {
        @Specialization
        int set(PXMLParser self, int value) {
            self.paramEntityParsing = value;
            return 1;
        }
    }

    @Builtin(name = "UseForeignDTD", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class UseForeignDTDNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone set(PXMLParser self, Object flag) {
            self.foreignDTD = flag == PNone.NO_VALUE || flag == PNone.NONE || Boolean.TRUE.equals(flag);
            return PNone.NONE;
        }
    }

    @Builtin(name = "GetReparseDeferralEnabled", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetReparseDeferralEnabledNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean get(PXMLParser self) {
            return self.reparseDeferralEnabled;
        }
    }

    @Builtin(name = "SetReparseDeferralEnabled", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetReparseDeferralEnabledNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone set(PXMLParser self, boolean value) {
            self.reparseDeferralEnabled = value;
            return PNone.NONE;
        }
    }

    @Builtin(name = "ExternalEntityParserCreate", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ExternalEntityParserCreateNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object create(PXMLParser self, @SuppressWarnings("unused") Object context, @SuppressWarnings("unused") Object encoding,
                        @Bind Node inliningTarget) {
            return createParser(inliningTarget, this, self.namespaceSeparator == null ? PNone.NONE : self.namespaceSeparator);
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetAttrNode extends SetAttrBuiltinNode {
        @Specialization
        static void set(VirtualFrame frame, PXMLParser self, Object keyObj, Object value,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringChecked0Node castKeyNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString key = castKeyNode.cast(inliningTarget, keyObj, ATTR_NAME_MUST_BE_STRING);
            if (key.equalsUncached(T_RETURNS_UNICODE, PythonUtils.TS_ENCODING)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, self, key);
            }
            if (key.equalsUncached(T_BUFFER_TEXT, PythonUtils.TS_ENCODING) ||
                            key.equalsUncached(T_NAMESPACE_PREFIXES, PythonUtils.TS_ENCODING) ||
                            key.equalsUncached(T_ORDERED_ATTRIBUTES, PythonUtils.TS_ENCODING) ||
                            key.equalsUncached(T_SPECIFIED_ATTRIBUTES, PythonUtils.TS_ENCODING)) {
                boolean b = !(value == PNone.NONE || value == PNone.NO_VALUE || Boolean.FALSE.equals(value) || (value instanceof Integer i && i == 0));
                value = b;
            } else if (key.equalsUncached(T_BUFFER_SIZE, PythonUtils.TS_ENCODING)) {
                if (!(value instanceof Integer i)) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, toTruffleStringUncached("an integer is required"));
                }
                if (i <= 0) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, toTruffleStringUncached("buffer_size must be greater than zero"));
                }
            }
            self.setAttribute(key, value);
            if (key.equalsUncached(T_BUFFER_SIZE, PythonUtils.TS_ENCODING)) {
                self.bufferSize = (int) value;
            }
        }
    }

    private static boolean isTrue(Object v) {
        if (v == PNone.NO_VALUE || v == PNone.NONE) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Integer i) {
            return i != 0;
        }
        return true;
    }

    private static byte[] toBytes(Object data, Node raisingNode) {
        if (data instanceof TruffleString ts) {
            return ts.toJavaStringUncached().getBytes(StandardCharsets.UTF_8);
        }
        if (data instanceof PBytes || data instanceof PByteArray) {
            return PythonBufferAccessLibrary.getUncached().getCopiedByteArray(BytesNodes.GetBytesStorage.executeUncached(data));
        }
        throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.TypeError, toTruffleStringUncached("a bytes-like object is required"));
    }

    @TruffleBoundary
    private static void parseNow(PXMLParser parser, boolean swallowErrors) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(parser.namespaceSeparator != null);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            Handler handler = new Handler(parser);
            reader.setContentHandler(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            reader.setDTDHandler(handler);
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(parser.data)));
            parser.currentByteIndex = parser.data.length;
            parser.currentLineNumber = handler.line;
            parser.currentColumnNumber = handler.col;
            parser.setAttribute(T_CURRENT_BYTE_INDEX, parser.currentByteIndex);
            parser.setAttribute(T_CURRENT_LINE_NUMBER, parser.currentLineNumber);
            parser.setAttribute(T_CURRENT_COLUMN_NUMBER, parser.currentColumnNumber);
        } catch (SAXParseException e) {
            parser.currentLineNumber = e.getLineNumber();
            parser.currentColumnNumber = Math.max(0, e.getColumnNumber() - 1);
            parser.setAttribute(T_CURRENT_LINE_NUMBER, parser.currentLineNumber);
            parser.setAttribute(T_CURRENT_COLUMN_NUMBER, parser.currentColumnNumber);
            if (!swallowErrors) {
                throw raiseExpatError(null, toTruffleStringUncached("syntax error"), PXMLParser.XML_ERROR_SYNTAX, parser.currentByteIndex, parser.currentLineNumber,
                                parser.currentColumnNumber);
            }
        } catch (Exception e) {
            if (!swallowErrors) {
                throw raiseExpatError(null, toTruffleStringUncached("unclosed token"), PXMLParser.XML_ERROR_UNCLOSED_TOKEN, parser.currentByteIndex,
                                parser.currentLineNumber,
                                parser.currentColumnNumber);
            }
        }
    }

    private static RuntimeException raiseExpatError(Node raisingNode, TruffleString msg, int code, int byteIndex, int line, int column) {
        PythonLanguage language = PythonLanguage.get(raisingNode);
        PBaseException exc = PFactory.createBaseException(language, PythonBuiltinClassType.PyExpatError, msg, EMPTY_OBJECT_ARRAY);
        exc.setAttribute(tsLiteral("code"), code);
        exc.setAttribute(tsLiteral("lineno"), line);
        exc.setAttribute(tsLiteral("offset"), column);
        exc.setAttribute(tsLiteral("message"), msg);
        exc.setAttribute(tsLiteral("byteindex"), byteIndex);
        throw PRaiseNode.raiseExceptionObjectStatic(raisingNode, exc, false);
    }

    private static final class Handler extends DefaultHandler2 {
        private final PXMLParser parser;
        private int line = 1;
        private int col;

        Handler(PXMLParser parser) {
            this.parser = parser;
            if (parser.foreignDTD && parser.paramEntityParsing == PXMLParser.XML_PARAM_ENTITY_PARSING_ALWAYS) {
                call("ExternalEntityRefHandler", PNone.NONE, PNone.NONE, PNone.NONE, PNone.NONE);
            }
        }

        @Override
        public void startPrefixMapping(String prefix, String uri) {
            call("StartNamespaceDeclHandler", toTs(prefix), toTs(uri));
        }

        @Override
        public void endPrefixMapping(String prefix) {
            call("EndNamespaceDeclHandler", toTs(prefix));
        }

        @Override
        public void processingInstruction(String target, String data) {
            call("ProcessingInstructionHandler", toTs(target), toTs(data));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs) {
            Object attrsObj;
            if (isTrue(T_ORDERED_ATTRIBUTES)) {
                List<Object> l = new ArrayList<>(attrs.getLength() * 2);
                for (int i = 0; i < attrs.getLength(); i++) {
                    l.add(toTs(attrs.getQName(i)));
                    l.add(toTs(attrs.getValue(i)));
                }
                attrsObj = PFactory.createList(PythonLanguage.get(null), l.toArray());
            } else {
                PDict d = PFactory.createDict(PythonLanguage.get(null));
                for (int i = 0; i < attrs.getLength(); i++) {
                    d.setItem(toTs(attrs.getQName(i)), toTs(attrs.getValue(i)));
                }
                attrsObj = d;
            }
            call("StartElementHandler", toTs(elementName(uri, localName, qName)), attrsObj);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            call("EndElementHandler", toTs(elementName(uri, localName, qName)));
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (length > 0) {
                call("CharacterDataHandler", toTs(new String(ch, start, length)));
            }
        }

        @Override
        public void comment(char[] ch, int start, int length) {
            call("CommentHandler", toTs(new String(ch, start, length)));
        }

        @Override
        public void startCDATA() {
            call("StartCdataSectionHandler");
        }

        @Override
        public void endCDATA() {
            call("EndCdataSectionHandler");
        }

        @Override
        public void skippedEntity(String name) {
            call("SkippedEntityHandler", toTs(name), 0);
        }

        private String elementName(String uri, String localName, String qName) {
            if (parser.namespaceSeparator != null && uri != null && !uri.isEmpty()) {
                return uri + parser.namespaceSeparator.toJavaStringUncached() + localName;
            }
            return qName == null || qName.isEmpty() ? localName : qName;
        }

        private boolean isTrue(TruffleString attr) {
            Object o = parser.getAttribute(attr);
            return o instanceof Boolean b && b;
        }

        private void call(String handlerName, Object... args) {
            Object cb = parser.getAttribute(toTruffleStringUncached(handlerName));
            if (cb != PNone.NO_VALUE) {
                try {
                    PyObjectCallMethodObjArgs.executeUncached(cb, tsLiteral("__call__"), args);
                } catch (PException e) {
                    throw e;
                }
            }
        }

        private static TruffleString toTs(String s) {
            return toTruffleStringUncached(s == null ? "" : s);
        }
    }
}
