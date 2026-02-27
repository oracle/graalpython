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
    public static final int XML_PARAM_ENTITY_PARSING_NEVER = 0;
    public static final int XML_PARAM_ENTITY_PARSING_UNLESS_STANDALONE = 1;
    public static final int XML_PARAM_ENTITY_PARSING_ALWAYS = 2;

    public static final int XML_ERROR_FINISHED = 1;
    public static final int XML_ERROR_SYNTAX = 2;
    public static final int XML_ERROR_UNCLOSED_TOKEN = 3;

    private final TruffleString namespaceSeparator;

    private boolean bufferText;
    private boolean namespacePrefixes;
    private boolean orderedAttributes;
    private boolean specifiedAttributes;
    private int bufferSize = 8192;
    private int bufferUsed;

    private int currentByteIndex;
    private int currentLineNumber = 1;
    private int currentColumnNumber;
    private int errorByteIndex;
    private int errorLineNumber = 1;
    private int errorColumnNumber;

    private boolean finished;
    private boolean foreignDTD;
    private int paramEntityParsing = XML_PARAM_ENTITY_PARSING_NEVER;
    private boolean reparseDeferralEnabled = true;
    private int deliveredEventCount;

    private byte[] data = new byte[0];
    private TruffleString base;
    private Object intern;

    public PXMLParser(Object cls, Shape instanceShape, TruffleString namespaceSeparator) {
        super(cls, instanceShape);
        this.namespaceSeparator = namespaceSeparator;
    }

    public TruffleString getNamespaceSeparator() {
        return namespaceSeparator;
    }

    public boolean isBufferText() {
        return bufferText;
    }

    public void setBufferText(boolean bufferText) {
        this.bufferText = bufferText;
    }

    public boolean isNamespacePrefixes() {
        return namespacePrefixes;
    }

    public void setNamespacePrefixes(boolean namespacePrefixes) {
        this.namespacePrefixes = namespacePrefixes;
    }

    public boolean isOrderedAttributes() {
        return orderedAttributes;
    }

    public void setOrderedAttributes(boolean orderedAttributes) {
        this.orderedAttributes = orderedAttributes;
    }

    public boolean isSpecifiedAttributes() {
        return specifiedAttributes;
    }

    public void setSpecifiedAttributes(boolean specifiedAttributes) {
        this.specifiedAttributes = specifiedAttributes;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getBufferUsed() {
        return bufferUsed;
    }

    public void setBufferUsed(int bufferUsed) {
        this.bufferUsed = bufferUsed;
    }

    public int getCurrentByteIndex() {
        return currentByteIndex;
    }

    public void setCurrentByteIndex(int currentByteIndex) {
        this.currentByteIndex = currentByteIndex;
    }

    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    public void setCurrentLineNumber(int currentLineNumber) {
        this.currentLineNumber = currentLineNumber;
    }

    public int getCurrentColumnNumber() {
        return currentColumnNumber;
    }

    public void setCurrentColumnNumber(int currentColumnNumber) {
        this.currentColumnNumber = currentColumnNumber;
    }

    public int getErrorByteIndex() {
        return errorByteIndex;
    }

    public void setErrorByteIndex(int errorByteIndex) {
        this.errorByteIndex = errorByteIndex;
    }

    public int getErrorLineNumber() {
        return errorLineNumber;
    }

    public void setErrorLineNumber(int errorLineNumber) {
        this.errorLineNumber = errorLineNumber;
    }

    public int getErrorColumnNumber() {
        return errorColumnNumber;
    }

    public void setErrorColumnNumber(int errorColumnNumber) {
        this.errorColumnNumber = errorColumnNumber;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isForeignDTD() {
        return foreignDTD;
    }

    public void setForeignDTD(boolean foreignDTD) {
        this.foreignDTD = foreignDTD;
    }

    public int getParamEntityParsing() {
        return paramEntityParsing;
    }

    public void setParamEntityParsing(int paramEntityParsing) {
        this.paramEntityParsing = paramEntityParsing;
    }

    public boolean isReparseDeferralEnabled() {
        return reparseDeferralEnabled;
    }

    public void setReparseDeferralEnabled(boolean reparseDeferralEnabled) {
        this.reparseDeferralEnabled = reparseDeferralEnabled;
    }

    public int getDeliveredEventCount() {
        return deliveredEventCount;
    }

    public void setDeliveredEventCount(int deliveredEventCount) {
        this.deliveredEventCount = deliveredEventCount;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public TruffleString getBase() {
        return base;
    }

    public void setBase(TruffleString base) {
        this.base = base;
    }

    public Object getIntern() {
        return intern;
    }

    public void setIntern(Object intern) {
        this.intern = intern;
    }
}
