/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) -2016 Jython Developers
 *
 * Licensed under PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.runtime.formatting;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyNumberLongNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Processes a printf-style formatting string or byte array and creates the resulting string or byte
 * array. The task of formatting individual elements is delegated to subclasses of
 * {@link InternalFormat.Formatter}. The result is buffered in an appropriate subclass of
 * {@link FormattingBuffer}.
 *
 * This class contains logic common to {@link BytesFormatProcessor} and
 * {@link StringFormatProcessor}.
 *
 * @param <T> The type of the result: {@code String} or {@code byte[]}.
 */
abstract class FormatProcessor<T> {
    /** see {@link #getArg()} for the meaning of this value. */
    private int argIndex = -1;
    private Object args;

    protected int index;
    protected final Python3Core core;
    protected final Node raisingNode;
    protected final FormattingBuffer buffer;

    public FormatProcessor(Python3Core core, FormattingBuffer buffer, Node raisingNode) {
        this.core = core;
        this.raisingNode = raisingNode;
        this.buffer = buffer;
        index = 0;
    }

    protected abstract String getFormatType();

    abstract char pop();

    final void push() {
        index--;
    }

    abstract boolean hasNext();

    <F extends InternalFormat.Formatter> F setupFormat(F f) {
        return f;
    }

    abstract int parseNumber(int start, int end);

    abstract Object parseMappingKey(int start, int end);

    protected abstract boolean isMapping(Object obj);

    static Object lookupAttribute(Object owner, TruffleString name) {
        return LookupSpecialMethodNode.Dynamic.executeUncached(GetClassNode.executeUncached(owner), name, owner);
    }

    static Object call(Object callable, Object... args) {
        return CallNode.executeUncached(callable, args, PKeyword.EMPTY_KEYWORDS);
    }

    Object getArg() {
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
                // args is definitely a tuple at this point. CPython access the tuple storage
                // directly, so the only error can be IndexError, which we ignore and transform into
                // the TypeError below.
                try {
                    ret = PyTupleGetItem.executeUncached(args, argIndex++);
                } catch (PException e) {
                    // fall through
                }
                break;
        }
        if (ret == null) {
            throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.NOT_ENOUGH_ARGS_FOR_FORMAT_STRING);
        }
        return ret;
    }

    int getNumber() {
        char c = pop();
        if (c == '*') {
            Object o = getArg();
            try {
                long value = CastToJavaLongLossyNode.executeUncached(o);
                if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                    throw PRaiseNode.raiseStatic(raisingNode, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "size");
                }
                return (int) value;
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.STAR_WANTS_INT);
            }
        } else {
            if (Character.isDigit(c)) {
                int numStart = index - 1;
                while (Character.isDigit(c = pop())) {
                    // empty
                }
                index -= 1;
                try {
                    return parseNumber(numStart, index);
                } catch (NumberFormatException e) {
                    throw PRaiseNode.raiseStatic(raisingNode, ValueError, ErrorMessages.TOO_MANY_DECIMAL_DIGITS_IN_FORMAT_STRING);
                }
            }
            index -= 1;
            return 0;
        }
    }

    // Whether an integer format allows floats
    private static boolean allowsFloat(char specType) {
        return !(specType == 'x' || specType == 'X' || specType == 'o' || specType == 'c');
    }

    // Whether we should use __index__ or __int__ for given spec type
    private static boolean useIndexMagicMethod(char specType) {
        return specType == 'x' || specType == 'X' || specType == 'o' || specType == 'c';
    }

    protected static Object asNumber(Object arg, char specType) {
        if (arg instanceof Integer || arg instanceof Long || arg instanceof PInt) {
            // arg is already acceptable
            return arg;
        } else if (arg instanceof Double) {
            if (allowsFloat(specType)) {
                // Fast path for simple double values, instead of __int__
                BigDecimal decimal = new BigDecimal((Double) arg, MathContext.UNLIMITED);
                return PFactory.createInt(PythonLanguage.get(null), decimal.toBigInteger());
            } else {
                // non-integer result indicates to the caller that it could not be converted
                return arg;
            }
        } else if (arg instanceof Boolean) {
            // Fast path for simple booleans
            return (Boolean) arg ? 1 : 0;
        } else if (PyNumberCheckNode.executeUncached(arg)) {
            try {
                if (useIndexMagicMethod(specType)) {
                    return PyNumberIndexNode.executeUncached(arg);
                } else {
                    return PyNumberLongNode.executeUncached(arg);
                }
            } catch (PException e) {
                e.expectUncached(TypeError);
            }
        }
        return null;
    }

    protected double asFloat(Object arg) {
        return PyFloatAsDoubleNode.executeUncached(arg);
    }

    protected abstract InternalFormat.Formatter handleRemainingFormats(InternalFormat.Spec spec);

    protected abstract InternalFormat.Formatter handleSingleCharacterFormat(InternalFormat.Spec spec);

    protected InternalFormat.Formatter formatInteger(Object intObj, InternalFormat.Spec spec) {
        IntegerFormatter.Traditional fi;
        if (intObj instanceof Integer) {
            fi = setupFormat(new IntegerFormatter.Traditional(buffer, spec, raisingNode));
            fi.format((Integer) intObj);
        } else if (intObj instanceof Long) {
            fi = setupFormat(new IntegerFormatter.Traditional(buffer, spec, raisingNode));
            fi.format((BigInteger.valueOf((Long) intObj)));
        } else if (intObj instanceof PInt) {
            fi = setupFormat(new IntegerFormatter.Traditional(buffer, spec, raisingNode));
            fi.format(((PInt) intObj).getValue());
        } else {
            // It couldn't be converted, null indicates error
            return null;
        }
        return fi;
    }

    protected static boolean isString(Object args1, Object lazyClass) {
        return PGuards.isString(args1) || isSubtype(lazyClass, PythonBuiltinClassType.PString);
    }

    protected static boolean isSubtype(Object lazyClass, PythonBuiltinClassType clazz) {
        return IsSubtypeNodeGen.getUncached().execute(lazyClass, clazz);
    }

    /**
     * Main service of this class: format one or more arguments with the format string supplied at
     * construction.
     */
    @TruffleBoundary
    public T format(Object args1) {
        try {
            return formatImpl(args1);
        } catch (OutOfMemoryError e) {
            throw PRaiseNode.raiseStatic(raisingNode, MemoryError);
        }
    }

    private T formatImpl(Object args1) {
        Object mapping = null;
        this.args = args1;

        // We need to do a full subtype-check because native objects may inherit from tuple but have
        // Java type 'PythonNativeObject' (e.g. 'namedtuple' alias 'structseq').
        final Object args1LazyClass = GetClassNode.executeUncached(args1);
        boolean tupleArgs = PGuards.isPTuple(args1) || isSubtype(args1LazyClass, PythonBuiltinClassType.PTuple);
        if (tupleArgs) {
            // We will simply work through the tuple elements
            argIndex = 0;
        } else {
            // Not a tuple, but possibly still some kind of container: use
            // special argIndex values.
            if (isMapping(args1)) {
                mapping = args1;
                argIndex = -3;
            }
        } // otherwise argIndex is left as -1

        while (hasNext()) {
            // Read one character from the format string
            char c = pop();
            if (c != '%') {
                // In the case of bytes formatter, we're going to convert it back to a byte and
                // append
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
            if (c == '%') {
                // '%' was escaped using '%%'
                // note that, unlike python2, python3 does not support padding in such case, e.g.,
                // %5% is not supported anymore
                buffer.append(c);
                continue;
            }
            if (c == '(') {
                // Mapping key, consisting of a parenthesised sequence of characters.
                if (mapping == null) {
                    throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.FORMAT_REQUIRES_MAPPING);
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
                Object tmp = parseMappingKey(keyStart, index - 1);
                // Look it up using this extent as the (right type of) key. The caller must have
                // pushed the frame.
                this.args = PyObjectGetItem.executeUncached(mapping, tmp);
            } else {
                // Not a mapping key: next clause will re-read c.
                push();
            }

            // Conversion flags (optional) that affect the result of some conversion types.
            while (true) {
                switch (pop()) {
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
            width = getNumber();
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
                precision = getNumber();
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
            InternalFormat.Spec spec = new InternalFormat.Spec(fill, align, sign, altFlag, width, Spec.NONE, precision, c);

            /*
             * Process argument according to format specification decoded from the string. It is
             * important we don't read the argument from the list until this point because of the
             * possibility that width and precision were specified via the argument list.
             */

            // Depending on the type of conversion, we use one of these formatters:
            FloatFormatter ff;
            InternalFormat.Formatter f; // = ff, fi or ft, whichever we actually use.
            Object arg;

            switch (spec.type) {
                case 'c': // Single character (accepts integer or single character string).
                    f = handleSingleCharacterFormat(spec);
                    break;

                case 'd': // All integer formats (+case for X).
                case 'o':
                case 'x':
                case 'X':
                case 'u': // Obsolete type identical to 'd'.
                case 'i': // Compatibility with scanf().
                    // Format the argument using this Spec.
                    // Note various types accepted here as long as they have an __int__ method.
                    arg = getArg();
                    f = formatInteger(asNumber(arg, spec.type), spec);
                    if (f == null) {
                        if (allowsFloat(spec.type)) {
                            throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.S_FORMAT_NUMBER_IS_REQUIRED_NOT_S, spec.type, arg);
                        } else {
                            throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.S_FORMAT_INTEGER_IS_REQUIRED_NOT_S, spec.type, arg);
                        }
                    }
                    break;

                case 'e': // All floating point formats (+case).
                case 'E':
                case 'f':
                case 'F':
                case 'g':
                case 'G':
                    // Format using this Spec the double form of the argument.
                    f = ff = new FloatFormatter(buffer, spec, raisingNode);

                    // Note various types accepted here as long as they have a __float__ method.
                    arg = getArg();
                    ff.format(asFloat(arg));
                    break;

                default:
                    f = handleRemainingFormats(spec);
                    if (f == null) {
                        throw PRaiseNode.raiseStatic(raisingNode, ValueError, ErrorMessages.UNSUPPORTED_FORMAT_CHAR_AT_INDEX, spec.type, (int) spec.type, index - 1);
                    }
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
        if (argIndex == -1 || (argIndex >= 0 && PyTupleSizeNode.executeUncached(args1) >= argIndex + 1)) {
            throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.NOT_ALL_ARGS_CONVERTED_DURING_FORMATTING, getFormatType());
        }

        // Return the final buffer contents as a str or unicode as appropriate.
        return toResult();
    }

    @SuppressWarnings("unchecked")
    private T toResult() {
        // We do unchecked cast to avoid proliferation of the generic type everywhere. Implementors
        // are responsible for providing "buffer" that returns the right type from "toResult"
        return (T) buffer.toResult();
    }
}
