/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectReprAsJavaStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;

public final class StringFormatProcessor extends FormatProcessor<String> {
    private final String formatText;

    public StringFormatProcessor(Python3Core core, PRaiseNode raiseNode, PyObjectGetItem getItemNode, TupleBuiltins.GetItemNode getTupleItemNode, String format) {
        super(core, raiseNode, getItemNode, getTupleItemNode, new FormattingBuffer.StringFormattingBuffer(format.length() + 100));
        index = 0;
        this.formatText = format;
    }

    @Override
    protected String getFormatType() {
        return "string";
    }

    @Override
    public char pop() {
        try {
            return formatText.charAt(index++);
        } catch (StringIndexOutOfBoundsException e) {
            throw raiseNode.raise(ValueError, ErrorMessages.INCOMPLETE_FORMAT);
        }
    }

    @Override
    boolean hasNext() {
        return index < formatText.length();
    }

    @Override
    int parseNumber(int start, int end) {
        return Integer.parseInt(formatText.substring(start, end));
    }

    @Override
    Object parseMappingKey(int start, int end) {
        return formatText.substring(start, end);
    }

    @Override
    protected boolean isMapping(Object obj) {
        // unicodeobject.c PyUnicode_Format()
        return !(obj instanceof PTuple || obj instanceof PString || obj instanceof String) && PyMappingCheckNode.getUncached().execute(obj);
    }

    private static boolean isOneCharacter(String str) {
        return str.length() == 1 || (str.length() == 2 && str.codePointCount(0, 2) == 1);
    }

    @Override
    protected InternalFormat.Formatter handleSingleCharacterFormat(Spec spec) {
        InternalFormat.Formatter f;
        TextFormatter ft;
        Object arg = getArg();
        if (arg instanceof String && isOneCharacter((String) arg)) {
            f = ft = setupFormat(new TextFormatter(raiseNode, buffer, spec));
            ft.format((String) arg);
        } else if (arg instanceof PString && isOneCharacter(((PString) arg).getValue())) {
            f = ft = new TextFormatter(raiseNode, buffer, spec);
            ft.format(((PString) arg).getCharSequence());
        } else {
            f = formatInteger(asNumber(arg, spec.type), spec);
            if (f == null) {
                throw raiseNode.raise(TypeError, ErrorMessages.REQUIRES_INT_OR_CHAR, spec.type);
            }
        }
        return f;
    }

    @Override
    protected InternalFormat.Formatter handleRemainingFormats(InternalFormat.Spec spec) {
        Object arg = getArg();
        String result;
        switch (spec.type) {
            case 'a': // repr as ascii
                result = PyObjectAsciiNode.getUncached().execute(null, arg);
                break;
            case 's': // String: converts any object using __str__(), __unicode__() ...
                result = PyObjectStrAsJavaStringNode.getUncached().execute(null, arg);
                break;
            case 'r': // ... or repr().
                result = PyObjectReprAsJavaStringNode.getUncached().execute(null, arg);
                break;
            default:
                return null;
        }
        TextFormatter ft = new TextFormatter(raiseNode, buffer, spec);
        ft.format(result);
        return ft;
    }
}
