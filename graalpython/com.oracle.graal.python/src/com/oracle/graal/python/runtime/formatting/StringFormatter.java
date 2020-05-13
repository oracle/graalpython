/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.function.BiFunction;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class StringFormatter {
    int index;
    String formatText;
    StringBuilder buffer;
    int argIndex;
    Object args;
    private final PythonCore core;

    final char pop() {
        try {
            return formatText.charAt(index++);
        } catch (StringIndexOutOfBoundsException e) {
            throw core.raise(ValueError, "incomplete format");
        }
    }

    final char peek() {
        return formatText.charAt(index);
    }

    final void push() {
        index--;
    }

    public StringFormatter(PythonCore core, String format) {
        this.core = core;
        index = 0;
        this.formatText = format;
        buffer = new StringBuilder(format.length() + 100);
    }

    Object getarg(LookupAndCallBinaryNode getItemNode) {
        Object ret = null;
        switch (argIndex) {
            case -3: // special index indicating a mapping
                return args;
            case -2: // special index indicating a single item that has already been used
                break;
            case -1: // special index indicating a single item that has not yet been used
                argIndex = -2;
                return args;
            default:
                // NOTE: passing 'null' frame means we already took care of the global state earlier
                ret = getItemNode.executeObject(null, args, argIndex++);
                break;
        }
        if (ret == null) {
            throw core.raise(TypeError, "not enough arguments for format string");
        }
        return ret;
    }

    int getNumber(LookupAndCallBinaryNode getItemNode) {
        char c = pop();
        if (c == '*') {
            Object o = getarg(getItemNode);
            if (o instanceof Long) {
                return ((Long) o).intValue();
            } else if (o instanceof Integer) {
                return (int) o;
            } else if (o instanceof PInt) {
                return ((PInt) o).intValue();
            } else if (o instanceof Double) {
                return ((Double) o).intValue();
            } else if (o instanceof PFloat) {
                return (int) ((PFloat) o).getValue();
            }
            throw core.raise(TypeError, "* wants int");
        } else {
            if (Character.isDigit(c)) {
                int numStart = index - 1;
                while (Character.isDigit(c = pop())) {
                    // empty
                }
                index -= 1;
                Integer i = Integer.valueOf(formatText.substring(numStart, index));
                return i.intValue();
            }
            index -= 1;
            return 0;
        }
    }

    private static Object asNumber(Object arg, CallNode callNode, BiFunction<Object, String, Object> lookupAttribute) {
        if (arg instanceof Integer || arg instanceof Long || arg instanceof PInt) {
            // arg is already acceptable
            return arg;
        } else if (arg instanceof Double) {
            // A common case where it is safe to return arg.__int__()
            return ((Double) arg).intValue();
        } else if (arg instanceof Boolean) {
            return (Boolean) arg ? 1 : 0;
        } else if (arg instanceof PFloat) {
            return (int) ((PFloat) arg).getValue();
        } else if (arg instanceof PythonAbstractObject) {
            // Try again with arg.__int__()
            try {
                // Result is the result of arg.__int__() if that works
                Object attribute = lookupAttribute.apply(arg, __INT__);
                return callNode.execute(null, attribute, createArgs(arg), PKeyword.EMPTY_KEYWORDS);
            } catch (PException e) {
                // No __int__ defined (at Python level)
            }
        }
        return arg;
    }

    private static Object asFloat(Object arg, CallNode callNode, BiFunction<Object, String, Object> lookupAttribute) {
        if (arg instanceof Double) {
            // arg is already acceptable
            return arg;
        } else if (arg instanceof PFloat) {
            return ((PFloat) arg).getValue();
        } else {
            try {
                Object attribute = lookupAttribute.apply(arg, __FLOAT__);
                return callNode.execute(null, attribute, createArgs(arg), PKeyword.EMPTY_KEYWORDS);
            } catch (PException e) {
            }
        }
        return arg;
    }

    /**
     * Main service of this class: format one or more arguments with the format string supplied at
     * construction.
     */
    @TruffleBoundary
    public Object format(Object args1, CallNode callNode, BiFunction<Object, String, Object> lookupAttribute, LookupAndCallBinaryNode getItemNode) {
        Object mapping = null;
        this.args = args1;

        // We need to do a full subtype-check because native objects may inherit from tuple but have
        // Java type 'PythonNativeObject' (e.g. 'namedtuple' alias 'structseq').
        boolean tupleArgs = PGuards.isPTuple(args1) || IsSubtypeNodeGen.getUncached().execute(GetLazyClassNode.getUncached().execute(args1), PythonBuiltinClassType.PTuple);
        assert tupleArgs || !PGuards.isPTuple(args1);
        if (tupleArgs) {
            // We will simply work through the tuple elements
            argIndex = 0;
        } else {
            // Not a tuple, but possibly still some kind of container: use
            // special argIndex values.
            argIndex = -1;
            if (lookupAttribute.apply(args1, __GETITEM__) != PNone.NO_VALUE) {
                mapping = args1;
                argIndex = -3;
            }
        }

        while (index < formatText.length()) {
            // Read one character from the format string
            char c = pop();
            if (c != '%') {
                buffer.append(c);
                continue;
            }

            // It's a %, so the beginning of a conversion specifier. Parse it.

            // Attributes to be parsed from the next format specifier
            boolean altFlag = false;
            char sign = InternalFormat.Spec.NONE;
            char fill = ' ';
            char align = '>';
            int width = InternalFormat.Spec.UNSPECIFIED;
            int precision = InternalFormat.Spec.UNSPECIFIED;

            // A conversion specifier contains the following components, in this order:
            // + The '%' character, which marks the start of the specifier.
            // + Mapping key (optional), consisting of a parenthesised sequence of characters.
            // + Conversion flags (optional), which affect the result of some conversion types.
            // + Minimum field width (optional), or an '*' (asterisk).
            // + Precision (optional), given as a '.' (dot) followed by the precision or '*'.
            // + Length modifier (optional).
            // + Conversion type.

            c = pop();
            if (c == '(') {
                // Mapping key, consisting of a parenthesised sequence of characters.
                if (mapping == null) {
                    throw core.raise(TypeError, "format requires a mapping");
                }
                // Scan along until a matching close parenthesis is found
                int parens = 1;
                int keyStart = index;
                while (parens > 0) {
                    c = pop();
                    if (c == ')') {
                        parens--;
                    } else if (c == '(') {
                        parens++;
                    }
                }
                // Last c=pop() is the closing ')' while indexKey is just after the opening '('
                String tmp = formatText.substring(keyStart, index - 1);
                // Look it up using this extent as the (right type of) key. The caller must have
                // pushed the frame.
                this.args = getItemNode.executeObject(null, mapping, tmp);
            } else {
                // Not a mapping key: next clause will re-read c.
                push();
            }

            // Conversion flags (optional) that affect the result of some conversion types.
            while (true) {
                switch (c = pop()) {
                    case '-':
                        align = '<';
                        continue;
                    case '+':
                        sign = '+';
                        continue;
                    case ' ':
                        if (!InternalFormat.Spec.specified(sign)) {
                            // Blank sign only wins if '+' not specified.
                            sign = ' ';
                        }
                        continue;
                    case '#':
                        altFlag = true;
                        continue;
                    case '0':
                        fill = '0';
                        continue;
                }
                break;
            }
            // Push back c as next clause will re-read c.
            push();

            /*
             * Minimum field width (optional). If specified as an '*' (asterisk), the actual width
             * is read from the next element of the tuple in values, and the object to convert comes
             * after the minimum field width and optional precision. A custom getNumber() takes care
             * of the '*' case.
             */
            width = getNumber(getItemNode);
            if (width < 0) {
                width = -width;
                align = '<';
            }

            /*
             * Precision (optional), given as a '.' (dot) followed by the precision. If specified as
             * '*' (an asterisk), the actual precision is read from the next element of the tuple in
             * values, and the value to convert comes after the precision. A custom getNumber()
             * takes care of the '*' case.
             */
            c = pop();
            if (c == '.') {
                precision = getNumber(getItemNode);
                if (precision < -1) {
                    precision = 0;
                }
                c = pop();
            }

            // Length modifier (optional). (Compatibility feature?) It has no effect.
            if (c == 'h' || c == 'l' || c == 'L') {
                c = pop();
            }

            /*
             * As a function of the conversion type (currently in c) override some of the formatting
             * flags we read from the format specification.
             */
            switch (c) {
                case 's':
                case 'r':
                case 'c':
                case '%':
                    // These have string-like results: fill, if needed, is always blank.
                    fill = ' ';
                    break;

                default:
                    if (fill == '0' && align == '>') {
                        // Zero-fill comes after the sign in right-justification.
                        align = '=';
                    } else {
                        // If left-justifying, the fill is always blank.
                        fill = ' ';
                    }
            }

            /*
             * Encode as an InternalFormat.Spec. The values in the constructor always have specified
             * values, except for sign, width and precision.
             */
            InternalFormat.Spec spec = new InternalFormat.Spec(fill, align, sign, altFlag, width, false, precision, c);

            /*
             * Process argument according to format specification decoded from the string. It is
             * important we don't read the argument from the list until this point because of the
             * possibility that width and precision were specified via the argument list.
             */

            // Depending on the type of conversion, we use one of these formatters:
            FloatFormatter ff;
            IntegerFormatter fi;
            TextFormatter ft;
            InternalFormat.Formatter f; // = ff, fi or ft, whichever we actually use.

            switch (spec.type) {
                case 'b':
                    Object arg = getarg(getItemNode);
                    f = ft = new TextFormatter(core, buffer, spec);
                    ft.setBytes(true);
                    Object bytesAttribute;
                    if (arg instanceof String) {
                        ft.format((String) arg);
                    } else if (arg instanceof PString) {
                        ft.format(((PString) arg).toString());
                    } else if (arg instanceof PBytes) {
                        ft.format(((PBytes) arg).toString());
                    } else if (arg instanceof PythonAbstractObject && ((bytesAttribute = lookupAttribute.apply(arg, __BYTES__)) != PNone.NO_VALUE)) {
                        Object result = callNode.execute(null, bytesAttribute, createArgs(arg), PKeyword.EMPTY_KEYWORDS);
                        ft.format(result.toString());
                    } else {
                        throw core.raise(TypeError, " %%b requires bytes, or an object that implements %s, not '%p'", __BYTES__, arg);
                    }
                    break;
                case 's': // String: converts any object using __str__(), __unicode__() ...
                case 'r': // ... or repr().
                    arg = getarg(getItemNode);
                    // Get hold of the actual object to display (may set needUnicode)
                    Object attribute = spec.type == 's' ? lookupAttribute.apply(arg, __STR__) : lookupAttribute.apply(arg, __REPR__);
                    if (attribute != PNone.NO_VALUE) {
                        Object result = callNode.execute(null, attribute, createArgs(arg), PKeyword.EMPTY_KEYWORDS);
                        if (PGuards.isString(result)) {
                            // Format the str/unicode form of the argument using this Spec.
                            f = ft = new TextFormatter(core, buffer, spec);
                            ft.format(result.toString());
                            break;
                        }
                    }
                    throw core.raise(TypeError, " %%r requires an object that implements %s", (spec.type == 's' ? __STR__ : __REPR__));

                case 'd': // All integer formats (+case for X).
                case 'o':
                case 'x':
                case 'X':
                case 'c': // Single character (accepts integer or single character string).
                case 'u': // Obsolete type identical to 'd'.
                case 'i': // Compatibility with scanf().
                    // Format the argument using this Spec.

                    arg = getarg(getItemNode);

                    // Note various types accepted here as long as they have an __int__ method.
                    Object argAsNumber = asNumber(arg, callNode, lookupAttribute);

                    // We have to check what we got back.
                    if (argAsNumber instanceof Integer) {
                        f = fi = new IntegerFormatter.Traditional(core, buffer, spec);
                        fi.format((Integer) argAsNumber);
                    } else if (argAsNumber instanceof Long) {
                        f = fi = new IntegerFormatter.Traditional(core, buffer, spec);
                        fi.format((BigInteger.valueOf((Long) argAsNumber)));
                    } else if (argAsNumber instanceof PInt) {
                        f = fi = new IntegerFormatter.Traditional(core, buffer, spec);
                        fi.format(((PInt) argAsNumber).getValue());
                    } else if (arg instanceof String && ((String) arg).length() == 1) {
                        f = ft = new TextFormatter(core, buffer, spec);
                        ft.format((String) arg);
                    } else if (arg instanceof PString && ((PString) arg).getValue().length() == 1) {
                        f = ft = new TextFormatter(core, buffer, spec);
                        ft.format(((PString) arg).getCharSequence());
                    } else {
                        // It couldn't be converted, raise the error here
                        throw core.raise(TypeError, "%%%c requires int or char");
                    }

                    break;

                case 'e': // All floating point formats (+case).
                case 'E':
                case 'f':
                case 'F':
                case 'g':
                case 'G':

                    // Format using this Spec the double form of the argument.
                    f = ff = new FloatFormatter(core, buffer, spec);

                    // Note various types accepted here as long as they have a __float__ method.
                    arg = getarg(getItemNode);
                    Object argAsFloat = asFloat(arg, callNode, lookupAttribute);

                    // We have to check what we got back..
                    if (argAsFloat instanceof Double) {
                        ff.format((Double) argAsFloat);
                    } else if (argAsFloat instanceof PFloat) {
                        ff.format(((PFloat) argAsFloat).getValue());
                    } else {
                        // It couldn't be converted, raise the error here
                        throw core.raise(TypeError, "float argument required, not %p", arg);
                    }

                    break;
                case '%': // Percent symbol, but surprisingly, padded.
                    // We use an integer formatter.
                    f = fi = new IntegerFormatter.Traditional(core, buffer, spec);
                    fi.format('%');
                    break;

                default:
                    throw core.raise(ValueError, "unsupported format character '%c' (0x%x) at index %d", spec.type, (int) spec.type, index - 1);
            }

            // Pad the result as specified (in-place, in the buffer).
            f.pad();
        }

        /*
         * All fields in the format string have been used to convert arguments (or used the argument
         * as a width, etc.). This had better not leave any arguments unused. Note argIndex is an
         * index into args or has a special value. If args is a 'proper' index, It should now be out
         * of range; if a special value, it would be wrong if it were -1, indicating a single item
         * that has not yet been used.
         */
        if (argIndex == -1 || (argIndex >= 0 && PythonObjectLibrary.getUncached().length(args1) > argIndex + 1)) {
            throw core.raise(TypeError, "not all arguments converted during string formatting");
        }

        // Return the final buffer contents as a str or unicode as appropriate.
        return buffer.toString();
    }

    private static Object[] createArgs(Object self) {
        return new Object[]{self};
    }

}
