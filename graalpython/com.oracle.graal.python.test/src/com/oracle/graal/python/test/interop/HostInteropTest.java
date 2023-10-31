package com.oracle.graal.python.test.interop;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostInteropTest extends PythonTests {
    private Context context;

    @Before
    public void setUpTest() {
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testConstantInteropBehavior() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            pass

                        polyglot.register_host_interop_behavior(MyType,
                            is_boolean=False,
                            is_number=True,
                            is_string=False,
                            # is_date=False,
                            # is_duration=False,
                            # is_instant=True,
                            # is_iterator=False,
                            # is_time=True,
                            # is_time_zone=False
                        )

                        MyType()
                        """);
        assertFalse(t.isBoolean());
        assertTrue(t.isNumber());
        assertFalse(t.isString());
        // todo (cbasca): implement redefinition of behavior for the following
        // assertFalse(t.isDate());
        // assertFalse(t.isDuration());
        // assertTrue(t.isInstant());
        // assertFalse(t.isIterator());
        // assertTrue(t.isTime());
        // assertFalse(t.isTimeZone());
    }

    @Test
    public void testConstantDefaults() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            pass

                        polyglot.register_host_interop_behavior(MyType, is_number=True)

                        MyType()
                        """);
        assertFalse(t.isBoolean());
        assertTrue(t.isNumber());
        assertFalse(t.isString());
    }

    @Test
    public void testBoolean() {
        String source = """
                        import polyglot

                        class MyType(object):
                            def __init__(self, data):
                                self._data = data

                        def as_boolean(t):
                            return t._data == "x"

                        polyglot.register_host_interop_behavior(MyType,
                            is_boolean=True,
                            as_boolean=as_boolean
                        )
                        """;
        Value t = context.eval("python", source + "\nMyType('x')");
        assertTrue(t.isBoolean());
        assertTrue(t.asBoolean());
        t = context.eval("python", source + "\nMyType('y')");
        assertTrue(t.isBoolean());
        assertFalse(t.asBoolean());
    }

    @Test
    public void testNumber() {
        String sourceTemplate = """
                        import polyglot

                        class MyType(object):
                            data = %s

                        def fits_in_byte(t):
                            return t.data >= 0 and t.data < 256

                        def fits_in_short(t):
                            return t.data >= %s and t.data < %s

                        def _fits_in_int(n):
                            return n >= %s and n < %s

                        def fits_in_int(t):
                            return _fits_in_int(t.data)

                        def _fits_in_long(n):
                            return n >= %s and n < %s

                        def fits_in_long(t):
                            return _fits_in_long(t.data)

                        def ieee754_bits(num):
                            import struct
                            return struct.unpack('!I', struct.pack('!f', num))[0]

                        def ieee754_bits2(num):
                            import struct
                            return struct.unpack('!Q', struct.pack('!d', num))[0]

                        def fits_in_float(t):
                            if isinstance(t.data, float):
                                try:
                                    bits = ieee754_bits(t.data)
                                    return bits >= 0x00800000 and bits < 0x7f7fffff
                                except OverflowError:
                                    return False
                            return fits_in_int(t)

                        def fits_in_double(t):
                            if isinstance(t.data, float):
                                try:
                                    bits = ieee754_bits2(t.data)
                                    return bits >= 0x0010000000000000 and bits < 0x7fefffffffffffff
                                except OverflowError:
                                    return False
                            return fits_in_long(t)

                        def get_data(t):
                            return t.data

                        polyglot.register_host_interop_behavior(MyType,
                            is_number=True,
                            fits_in_byte=fits_in_byte,
                            fits_in_short=fits_in_short,
                            fits_in_int=fits_in_int,
                            fits_in_long=fits_in_long,
                            fits_in_big_integer=lambda t: True,
                            fits_in_float=fits_in_float,
                            fits_in_double=fits_in_double,
                            as_byte=get_data,
                            as_short=get_data,
                            as_int=get_data,
                            as_long=get_data,
                            as_big_integer=get_data,
                            as_float=get_data,
                            as_double=get_data,
                        )

                        MyType()
                        """;
        Value t;
        // byte
        byte byteValue = (byte) 0x7F;
        t = context.eval("python", String.format(sourceTemplate, byteValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertTrue(t.fitsInByte());
        assertEquals(byteValue, t.asByte());
        // short
        short shortValue = Short.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, shortValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertTrue(t.fitsInShort());
        assertEquals(shortValue, t.asShort());
        // int
        int intValue = Integer.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, intValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertTrue(t.fitsInInt());
        assertEquals(intValue, t.asInt());
        // long
        long longValue = Long.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, longValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertTrue(t.fitsInLong());
        assertEquals(longValue, t.asLong());
        // big integer
        BigInteger bigInteger = new BigInteger("9223372036854775807123456789", 10);
        t = context.eval("python", String.format(sourceTemplate, bigInteger, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertTrue(t.fitsInBigInteger());
        assertEquals(bigInteger, t.asBigInteger());
        // float
        float floatValue = Float.MAX_VALUE / 1000;
        t = context.eval("python", String.format(sourceTemplate, floatValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertTrue(t.fitsInFloat());
        assertEquals(floatValue, t.asFloat(), 0);
        // double
        double doubleValue = Double.MAX_VALUE / 1000;
        t = context.eval("python", String.format(sourceTemplate, doubleValue, Short.MIN_VALUE, Short.MAX_VALUE,
                        Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertFalse(t.fitsInFloat());
        assertTrue(t.fitsInDouble());
        assertEquals(doubleValue, t.asDouble(), 0);
    }
}
