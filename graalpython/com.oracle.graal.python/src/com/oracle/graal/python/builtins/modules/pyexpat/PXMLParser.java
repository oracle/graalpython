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

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PXMLParser extends PythonBuiltinObject {
    static final int XML_PARAM_ENTITY_PARSING_NEVER = 0;
    static final int XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE = 1;
    static final int XML_PARAM_ENTITY_PARSING_ALWAYS = 2;

    static final int XML_ERROR_FINISHED = 1;
    static final int XML_ERROR_SYNTAX = 2;
    static final int XML_ERROR_UNCLOSED_TOKEN = 3;

    final TruffleString namespaceSeparator;

    boolean bufferText;
    boolean namespacePrefixes;
    boolean orderedAttributes;
    boolean specifiedAttributes;
    int bufferSize = 8192;

    int currentByteIndex;
    int currentLineNumber = 1;
    int currentColumnNumber;

    boolean finished;
    boolean foreignDTD;
    int paramEntityParsing = XML_PARAM_ENTITY_PARSING_NEVER;
    boolean reparseDeferralEnabled = true;

    byte[] data = new byte[0];

    Object startElementHandler;
    Object endElementHandler;
    Object characterDataHandler;
    Object processingInstructionHandler;
    Object unparsedEntityDeclHandler;
    Object notationDeclHandler;
    Object startNamespaceDeclHandler;
    Object endNamespaceDeclHandler;
    Object commentHandler;
    Object startCdataSectionHandler;
    Object endCdataSectionHandler;
    Object defaultHandler;
    Object defaultHandlerExpand;
    Object notStandaloneHandler;
    Object externalEntityRefHandler;
    Object startDoctypeDeclHandler;
    Object endDoctypeDeclHandler;
    Object entityDeclHandler;
    Object xmlDeclHandler;
    Object elementDeclHandler;
    Object attlistDeclHandler;
    Object skippedEntityHandler;

    public PXMLParser(Object cls, Shape instanceShape, TruffleString namespaceSeparator) {
        super(cls, instanceShape);
        this.namespaceSeparator = namespaceSeparator;
    }
}
