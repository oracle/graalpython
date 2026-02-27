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

import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.DefaultHandler;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
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
    private static final TruffleString T_ERROR_BYTE_INDEX = tsLiteral("ErrorByteIndex");
    private static final TruffleString T_ERROR_LINE_NUMBER = tsLiteral("ErrorLineNumber");
    private static final TruffleString T_ERROR_COLUMN_NUMBER = tsLiteral("ErrorColumnNumber");
    private static final TruffleString T_READ = tsLiteral("read");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return XMLParserBuiltinsFactory.getFactories();
    }

    static Object createParser(Node inliningTarget, Node raisingNode, Object namespaceSeparatorObj, Object intern) {
        TruffleString sep = null;
        if (!(namespaceSeparatorObj instanceof PNone)) {
            if (!(namespaceSeparatorObj instanceof TruffleString ts)) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.ValueError, ErrorMessages.NAMESPACE_SEPARATOR_MUST_BE);
            }
            int len = ts.codePointLengthUncached(TS_ENCODING);
            if (len > 1) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.ValueError, ErrorMessages.NAMESPACE_SEPARATOR_MUST_BE);
            }
            sep = ts;
        }
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        PXMLParser parser = PFactory.createXMLParser(PythonBuiltinClassType.XMLParser, PythonBuiltinClassType.XMLParser.getInstanceShape(language), sep);
        parser.setAttribute(T_BUFFER_TEXT, false);
        parser.setAttribute(T_NAMESPACE_PREFIXES, false);
        parser.setAttribute(T_ORDERED_ATTRIBUTES, false);
        parser.setAttribute(T_SPECIFIED_ATTRIBUTES, false);
        parser.setAttribute(T_BUFFER_SIZE, parser.getBufferSize());
        parser.setAttribute(T_CURRENT_BYTE_INDEX, 0);
        parser.setAttribute(T_CURRENT_LINE_NUMBER, 1);
        parser.setAttribute(T_CURRENT_COLUMN_NUMBER, 0);
        parser.setAttribute(T_ERROR_BYTE_INDEX, 0);
        parser.setAttribute(T_ERROR_LINE_NUMBER, 1);
        parser.setAttribute(T_ERROR_COLUMN_NUMBER, 0);
        parser.setIntern(intern);
        parser.setAttribute(tsLiteral("intern"), intern);
        return parser;
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "xmlparser", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doIt(Object cls, @SuppressWarnings("unused") Object arg1, Object arg2,
                        @Bind Node inliningTarget) {
            return createParser(inliningTarget, this, arg2 == PNone.NO_VALUE ? PNone.NONE : arg2, PNone.NONE);
        }
    }

    @Builtin(name = "Parse", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$cls", "data", "isfinal"})
    @ArgumentClinic(name = "isfinal", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class ParseNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        int parse(VirtualFrame frame, PXMLParser self, Object data, boolean isFinal,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            try {
                Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
                try {
                    doParse(self, data, isFinal, boundaryCallData);
                } finally {
                    BoundaryCallContext.exit(frame, boundaryCallData, savedState);
                }
                if (isFinal) {
                    self.setFinished(true);
                }
                return 1;
            } catch (RuntimeException e) {
                if (!isFinal) {
                    return 1;
                }
                throw e;
            }
        }

        @TruffleBoundary
        private void doParse(PXMLParser self, Object data, boolean isFinal, BoundaryCallData boundaryCallData) {
            if (self.isFinished()) {
                throw raiseExpatError(this, ErrorMessages.PARSING_FINISHED, PXMLParser.XML_ERROR_FINISHED, 0, 1, 0);
            }
            byte[] chunk = toBytes(data);
            byte[] merged = Arrays.copyOf(self.getData(), self.getData().length + chunk.length);
            System.arraycopy(chunk, 0, merged, self.getData().length, chunk.length);
            self.setData(merged);
            parseNow(self, !isFinal, boundaryCallData);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.ParseNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "ParseFile", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ParseFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        int parseFile(VirtualFrame frame, PXMLParser self, Object file,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData) {
            Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                doParseFile(self, file, boundaryCallData);
            } finally {
                BoundaryCallContext.exit(frame, boundaryCallData, savedState);
            }
            self.setFinished(true);
            return 1;
        }

        private void doParseFile(PXMLParser self, Object file, BoundaryCallData boundaryCallData) {
            while (true) {
                Object r = PyObjectCallMethodObjArgs.executeUncached(file, T_READ);
                byte[] b = toBytes(r);
                if (b.length == 0) {
                    break;
                }
                byte[] merged = Arrays.copyOf(self.getData(), self.getData().length + b.length);
                System.arraycopy(b, 0, merged, self.getData().length, b.length);
                self.setData(merged);
            }
            parseNow(self, false, boundaryCallData);
        }
    }

    @Builtin(name = "SetParamEntityParsing", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "flag"})
    @ArgumentClinic(name = "flag", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetParamEntityParsingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static int set(PXMLParser self, int value) {
            self.setParamEntityParsing(value);
            return 1;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetParamEntityParsingNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "UseForeignDTD", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "flag"})
    @ArgumentClinic(name = "flag", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class UseForeignDTDNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, boolean flag) {
            self.setForeignDTD(flag);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.UseForeignDTDNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "GetReparseDeferralEnabled", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetReparseDeferralEnabledNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean get(PXMLParser self) {
            return self.isReparseDeferralEnabled();
        }
    }

    @Builtin(name = "SetReparseDeferralEnabled", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "enabled"})
    @ArgumentClinic(name = "enabled", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class SetReparseDeferralEnabledNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, boolean value) {
            self.setReparseDeferralEnabled(value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetReparseDeferralEnabledNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "SetBase", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "base"})
    @ArgumentClinic(name = "base", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class SetBaseNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static PNone set(PXMLParser self, TruffleString base) {
            self.setBase(base);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return XMLParserBuiltinsClinicProviders.SetBaseNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "GetBase", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBaseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PXMLParser self) {
            return self.getBase() == null ? PNone.NONE : self.getBase();
        }
    }

    @Builtin(name = "ExternalEntityParserCreate", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ExternalEntityParserCreateNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object create(PXMLParser self, @SuppressWarnings("unused") Object context, @SuppressWarnings("unused") Object encoding,
                        @Bind Node inliningTarget) {
            return createParser(inliningTarget, this, self.getNamespaceSeparator() == null ? PNone.NONE : self.getNamespaceSeparator(), self.getIntern());
        }
    }

    @TruffleBoundary
    private static byte[] toBytes(Object data) {
        if (PyUnicodeCheckNode.executeUncached(data)) {
            TruffleString utf8 = CastToTruffleStringNode.castKnownStringUncached(data).switchEncodingUncached(TruffleString.Encoding.UTF_8);
            return utf8.copyToByteArrayUncached(TruffleString.Encoding.UTF_8);
        }
        Object buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(data);
        try {
            return PythonBufferAccessLibrary.getUncached().getCopiedByteArray(buffer);
        } finally {
            PythonBufferAccessLibrary.getUncached().release(buffer);
        }
    }

    @TruffleBoundary
    private static void parseNow(PXMLParser parser, boolean swallowErrors, BoundaryCallData boundaryCallData) {
        final class Handler extends DefaultHandler2 {
            int line = 1;
            int col;
            int eventOrdinal;
            Locator locator;
            boolean keepCurrentPositionForNextCall;

            @Override
            public void setDocumentLocator(Locator locator) {
                this.locator = locator;
            }

            Handler() {
                if (parser.isForeignDTD() && parser.getParamEntityParsing() == PXMLParser.XML_PARAM_ENTITY_PARSING_ALWAYS) {
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
            public void startDTD(String name, String publicId, String systemId) {
                // We conservatively report an internal subset. This matches minidom builder
                // expectations and enables DTD callback wiring for entity/notation handling.
                call("StartDoctypeDeclHandler", toTs(name), toTs(systemId), toTs(publicId), 1);
            }

            @Override
            public void endDTD() {
                call("EndDoctypeDeclHandler");
            }

            @Override
            public void internalEntityDecl(String name, String value) {
                boolean isParameterEntity = name != null && name.startsWith("%");
                call("EntityDeclHandler", toTs(name), isParameterEntity ? 1 : 0, toTs(value), parser.getBase() == null ? PNone.NONE : parser.getBase(), PNone.NONE, PNone.NONE, PNone.NONE);
            }

            @Override
            public void externalEntityDecl(String name, String publicId, String systemId) {
                boolean isParameterEntity = name != null && name.startsWith("%");
                call("EntityDeclHandler", toTs(name), isParameterEntity ? 1 : 0, PNone.NONE, parser.getBase() == null ? PNone.NONE : parser.getBase(), toOptionalTs(normalizeSystemId(systemId)),
                                toOptionalTs(publicId), PNone.NONE);
            }

            @Override
            public void notationDecl(String name, String publicId, String systemId) {
                call("NotationDeclHandler", toTs(name), parser.getBase() == null ? PNone.NONE : parser.getBase(), toOptionalTs(normalizeSystemId(systemId)), toOptionalTs(publicId));
            }

            @Override
            public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) {
                call("UnparsedEntityDeclHandler", toTs(name), parser.getBase() == null ? PNone.NONE : parser.getBase(), toOptionalTs(normalizeSystemId(systemId)), toOptionalTs(publicId),
                                toTs(notationName));
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                Object attrsObj;
                if (isTrue(T_ORDERED_ATTRIBUTES)) {
                    List<Object> l = new ArrayList<>(attrs.getLength() * 2);
                    for (int i = 0; i < attrs.getLength(); i++) {
                        l.add(toTs(attributeName(attrs, i)));
                        l.add(toTs(attrs.getValue(i)));
                    }
                    attrsObj = PFactory.createList(PythonLanguage.get(null), l.toArray());
                } else {
                    PDict d = PFactory.createDict(PythonLanguage.get(null));
                    for (int i = 0; i < attrs.getLength(); i++) {
                        d.setItem(toTs(attributeName(attrs, i)), toTs(attrs.getValue(i)));
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
                if (locator != null) {
                    int entityLen = name.length() + 2; // '&' + ';'
                    line = Math.max(1, locator.getLineNumber());
                    col = Math.max(0, locator.getColumnNumber() - 1 - entityLen);
                    parser.setCurrentLineNumber(line);
                    parser.setCurrentColumnNumber(col);
                    parser.setAttribute(T_CURRENT_LINE_NUMBER, line);
                    parser.setAttribute(T_CURRENT_COLUMN_NUMBER, col);
                    parser.setAttribute(T_ERROR_LINE_NUMBER, line);
                    parser.setAttribute(T_ERROR_COLUMN_NUMBER, col);
                }
                keepCurrentPositionForNextCall = true;
                call("DefaultHandlerExpand", toTs("&" + name + ";"));
            }

            private String elementName(String uri, String localName, String qName) {
                if (parser.getNamespaceSeparator() != null && uri != null && !uri.isEmpty()) {
                    String sep = parser.getNamespaceSeparator().toJavaStringUncached();
                    if (isTrue(T_NAMESPACE_PREFIXES) && qName != null && !qName.isEmpty()) {
                        int colon = qName.indexOf(':');
                        if (colon > 0) {
                            String prefix = qName.substring(0, colon);
                            return uri + sep + localName + sep + prefix;
                        }
                    }
                    return uri + sep + localName;
                }
                return qName == null || qName.isEmpty() ? localName : qName;
            }

            private String attributeName(Attributes attrs, int i) {
                String uri = attrs.getURI(i);
                String localName = attrs.getLocalName(i);
                String qName = attrs.getQName(i);
                if (parser.getNamespaceSeparator() != null && uri != null && !uri.isEmpty()) {
                    String sep = parser.getNamespaceSeparator().toJavaStringUncached();
                    if (isTrue(T_NAMESPACE_PREFIXES) && qName != null && !qName.isEmpty()) {
                        int colon = qName.indexOf(':');
                        if (colon > 0) {
                            String prefix = qName.substring(0, colon);
                            return uri + sep + localName + sep + prefix;
                        }
                    }
                    return uri + sep + localName;
                }
                return qName == null || qName.isEmpty() ? localName : qName;
            }

            private boolean isTrue(TruffleString attr) {
                Object o = parser.getAttribute(attr);
                return o instanceof Boolean b && b;
            }

            private void call(String handlerName, Object... args) {
                boolean shouldDeliver = eventOrdinal++ >= parser.getDeliveredEventCount();
                if (!shouldDeliver) {
                    return;
                }
                if (!keepCurrentPositionForNextCall && locator != null) {
                    line = Math.max(1, locator.getLineNumber());
                    col = Math.max(0, locator.getColumnNumber() - 1);
                    parser.setCurrentLineNumber(line);
                    parser.setCurrentColumnNumber(col);
                    parser.setAttribute(T_CURRENT_LINE_NUMBER, line);
                    parser.setAttribute(T_CURRENT_COLUMN_NUMBER, col);
                    parser.setAttribute(T_ERROR_LINE_NUMBER, line);
                    parser.setAttribute(T_ERROR_COLUMN_NUMBER, col);
                }
                keepCurrentPositionForNextCall = false;
                Object cb = parser.getAttribute(toTruffleStringUncached(handlerName));
                if (cb != PNone.NO_VALUE) {
                    Node prevEncapsulatingNode = EncapsulatingNodeReference.getCurrent().set(boundaryCallData);
                    try {
                        PyObjectCallMethodObjArgs.executeUncached(cb, tsLiteral("__call__"), args);
                    } finally {
                        EncapsulatingNodeReference.getCurrent().set(prevEncapsulatingNode);
                    }
                }
            }

            private TruffleString toTs(String s) {
                return toTruffleStringUncached(s == null ? "" : s);
            }

            private Object toOptionalTs(String s) {
                return s == null || s.isEmpty() ? PNone.NONE : toTruffleStringUncached(s);
            }

            private String normalizeSystemId(String systemId) {
                if (systemId == null || parser.getBase() != null) {
                    return systemId;
                }
                String s = systemId;
                while (s.startsWith("file://")) {
                    s = s.substring("file://".length());
                }
                if (s.startsWith("/")) {
                    int idx = s.lastIndexOf('/');
                    if (idx >= 0 && idx + 1 < s.length()) {
                        return s.substring(idx + 1);
                    }
                }
                return systemId;
            }
        }

        Handler handler = new Handler();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(parser.getNamespaceSeparator() != null);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            try {
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
            }
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            reader.setContentHandler(handler);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler);
            reader.setProperty("http://xml.org/sax/properties/declaration-handler", handler);
            reader.setDTDHandler(handler);
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(parser.getData())));
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setCurrentByteIndex(parser.getData().length);
            parser.setCurrentLineNumber(handler.line);
            parser.setCurrentColumnNumber(handler.col);
            parser.setAttribute(T_CURRENT_BYTE_INDEX, parser.getCurrentByteIndex());
            parser.setAttribute(T_CURRENT_LINE_NUMBER, parser.getCurrentLineNumber());
            parser.setAttribute(T_CURRENT_COLUMN_NUMBER, parser.getCurrentColumnNumber());
            parser.setAttribute(T_ERROR_BYTE_INDEX, parser.getCurrentByteIndex());
            parser.setAttribute(T_ERROR_LINE_NUMBER, parser.getCurrentLineNumber());
            parser.setAttribute(T_ERROR_COLUMN_NUMBER, parser.getCurrentColumnNumber());
        } catch (SAXParseException e) {
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setCurrentLineNumber(e.getLineNumber());
            parser.setCurrentColumnNumber(Math.max(0, e.getColumnNumber() - 1));
            parser.setAttribute(T_CURRENT_LINE_NUMBER, parser.getCurrentLineNumber());
            parser.setAttribute(T_CURRENT_COLUMN_NUMBER, parser.getCurrentColumnNumber());
            parser.setAttribute(T_ERROR_BYTE_INDEX, parser.getCurrentByteIndex());
            parser.setAttribute(T_ERROR_LINE_NUMBER, parser.getCurrentLineNumber());
            parser.setAttribute(T_ERROR_COLUMN_NUMBER, parser.getCurrentColumnNumber());
            if (!swallowErrors) {
                TruffleString msg = toTruffleStringUncached(formatErrorMessage(e));
                throw raiseExpatError(null, msg, PXMLParser.XML_ERROR_SYNTAX, parser.getCurrentByteIndex(), parser.getCurrentLineNumber(),
                                parser.getCurrentColumnNumber());
            }
        } catch (PException e) {
            throw e;
        } catch (Exception e) {
            parser.setDeliveredEventCount(handler.eventOrdinal);
            parser.setAttribute(T_ERROR_BYTE_INDEX, parser.getCurrentByteIndex());
            parser.setAttribute(T_ERROR_LINE_NUMBER, parser.getCurrentLineNumber());
            parser.setAttribute(T_ERROR_COLUMN_NUMBER, parser.getCurrentColumnNumber());
            if (!swallowErrors) {
                TruffleString msg = toTruffleStringUncached(e.getMessage() == null ? "unclosed token" : e.getMessage());
                throw raiseExpatError(null, msg, PXMLParser.XML_ERROR_UNCLOSED_TOKEN, parser.getCurrentByteIndex(),
                                parser.getCurrentLineNumber(),
                                parser.getCurrentColumnNumber());
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

    private static String formatErrorMessage(SAXParseException e) {
        String message = e.getMessage();
        if (message == null) {
            return "syntax error";
        }
        if (message.contains("entity") && message.contains("not declared")) {
            int firstQuote = message.indexOf('"');
            int secondQuote = firstQuote >= 0 ? message.indexOf('"', firstQuote + 1) : -1;
            if (firstQuote >= 0 && secondQuote > firstQuote + 1) {
                String entity = message.substring(firstQuote + 1, secondQuote);
                return "undefined entity &" + entity + ";: line " + e.getLineNumber() + ", column " + Math.max(0, e.getColumnNumber() - 1);
            }
        }
        return message;
    }
}
