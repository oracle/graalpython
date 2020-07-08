/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;

public final class StringFormatProcessor extends FormatProcessor<String> {
    private final String formatText;

    public StringFormatProcessor(PythonCore core, LookupAndCallBinaryNode getItemNode, TupleBuiltins.GetItemNode getTupleItemNode, String format) {
        super(core, getItemNode, getTupleItemNode, new FormattingBuffer.StringFormattingBuffer(format.length() + 100));
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
            throw core.raise(ValueError, ErrorMessages.INCOMPLETE_FORMAT);
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
    protected boolean useAsMapping(Object args1, PythonObjectLibrary lib, Object lazyClass) {
        return !isString(args1, lazyClass) && isMapping(args1);
    }

    @Override
    protected InternalFormat.Formatter handleSingleCharacterFormat(Spec spec) {
        InternalFormat.Formatter f;
        TextFormatter ft;
        Object arg = getArg();
        if (arg instanceof String && ((String) arg).length() == 1) {
            f = ft = setupFormat(new TextFormatter(core, buffer, spec));
            ft.format((String) arg);
        } else if (arg instanceof PString && ((PString) arg).getValue().length() == 1) {
            f = ft = new TextFormatter(core, buffer, spec);
            ft.format(((PString) arg).getCharSequence());
        } else {
            f = formatInteger(asNumber(arg, spec.type), spec);
            if (f == null) {
                throw core.raise(TypeError, ErrorMessages.REQUIRES_INT_OR_CHAR, spec.type);
            }
        }
        return f;
    }

    @Override
    protected InternalFormat.Formatter handleRemainingFormats(InternalFormat.Spec spec) {
        switch (spec.type) {
            case 'a': // repr as ascii
            case 's': // String: converts any object using __str__(), __unicode__() ...
            case 'r': // ... or repr().
                Object arg = getArg();
                // Get hold of the actual object to display (may set needUnicode)
                Object attribute = spec.type == 's' ? lookupAttribute(arg, __STR__) : lookupAttribute(arg, __REPR__);
                if (attribute != PNone.NO_VALUE) {
                    Object result = call(attribute, arg);
                    if (PGuards.isString(result)) {
                        if (spec.type == 'a') {
                            // this is mostly what encode('ascii', 'backslashreplace') would do
                            result = new String(BytesUtils.unicodeNonAsciiEscape(result.toString()), StandardCharsets.US_ASCII);
                        }
                        TextFormatter ft = new TextFormatter(core, buffer, spec);
                        ft.format(result.toString());
                        return ft;
                    }
                }
                throw core.raise(TypeError, ErrorMessages.REQUIRES_OBJ_THAT_IMPLEMENTS_S, (spec.type == 's' ? __STR__ : __REPR__));
            default:
                return null;
        }
    }
}
