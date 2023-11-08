package com.oracle.graal.python.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.time.LocalDate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

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

                        def get_data(t):
                            return t.data

                        polyglot.register_host_interop_behavior(MyType,
                            is_number=True,
                            fits_in_byte=lambda t: polyglot.fits_in_byte(t.data),
                            fits_in_short=lambda t: polyglot.fits_in_short(t.data),
                            fits_in_int=lambda t: polyglot.fits_in_int(t.data),
                            fits_in_long=lambda t: polyglot.fits_in_long(t.data),
                            fits_in_big_integer=lambda t: polyglot.fits_in_big_integer(t.data),
                            fits_in_float=lambda t: polyglot.fits_in_float(t.data),
                            fits_in_double=lambda t: polyglot.fits_in_double(t.data),
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
        t = context.eval("python", String.format(sourceTemplate, byteValue));
        assertTrue(t.isNumber());
        assertTrue(t.fitsInByte());
        assertEquals(byteValue, t.asByte());
        // short
        short shortValue = Short.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, shortValue));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertTrue(t.fitsInShort());
        assertEquals(shortValue, t.asShort());
        // int
        int intValue = Integer.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, intValue));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertTrue(t.fitsInInt());
        assertEquals(intValue, t.asInt());
        // long
        long longValue = Long.MAX_VALUE - 1;
        t = context.eval("python", String.format(sourceTemplate, longValue));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertTrue(t.fitsInLong());
        assertEquals(longValue, t.asLong());
        // big integer
        BigInteger bigInteger = new BigInteger("9223372036854775807123456789", 10);
        t = context.eval("python", String.format(sourceTemplate, bigInteger));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertTrue(t.fitsInBigInteger());
        assertEquals(bigInteger, t.asBigInteger());
        // float
        float floatValue = 0.5f;
        t = context.eval("python", String.format(sourceTemplate, floatValue));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertTrue(t.fitsInFloat());
        assertEquals(floatValue, t.asFloat(), 0);
        // double
        double doubleValue = 123.45678901234;
        t = context.eval("python", String.format(sourceTemplate, doubleValue));
        assertTrue(t.isNumber());
        assertFalse(t.fitsInByte());
        assertFalse(t.fitsInShort());
        assertFalse(t.fitsInInt());
        assertFalse(t.fitsInLong());
        assertFalse(t.fitsInFloat());
        assertTrue(t.fitsInDouble());
        assertEquals(doubleValue, t.asDouble(), 0);
    }

    @Test
    public void testString() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = 10

                        polyglot.register_host_interop_behavior(MyType,
                            is_string=True,
                            as_string=lambda t: f"MyType({t.data})"
                        )

                        MyType()
                        """);
        assertFalse(t.isBoolean());
        assertFalse(t.isNumber());
        assertTrue(t.isString());
        assertEquals("MyType(10)", t.asString());
    }

    @Test
    public void testArray() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = [0,1,2,3,4]

                        def write_array_element(t, i, v):
                            if i >= len(t.data):
                                t.data.insert(i, v)
                            else:
                                t.data[i] = v

                        polyglot.register_host_interop_behavior(MyType,
                            has_array_elements=True,
                            get_array_size=lambda t: len(t.data),
                            is_array_element_readable=lambda t, i: i < len(t.data),
                            read_array_element=lambda t, i: t.data[i],
                            is_array_element_removable=lambda t, i: i < len(t.data),
                            remove_array_element=lambda t, i: t.data.pop(i),
                            is_array_element_insertable=lambda t, i: True,
                            is_array_element_modifiable=lambda t, i: i < len(t.data),
                            write_array_element=write_array_element
                        )

                        MyType()
                        """);
        assertFalse(t.isBoolean());
        assertFalse(t.isNumber());
        assertFalse(t.isString());
        assertTrue(t.hasArrayElements());
        assertEquals(5, t.getArraySize());
        assertEquals(1, t.getArrayElement(1).asInt());
        // remove - [1,2,3,4]
        t.removeArrayElement(0);
        assertEquals(4, t.getArraySize());
        assertEquals(2, t.getArrayElement(1).asInt());
        // append - [1,2,3,4,5]
        t.setArrayElement(100, 5);
        assertEquals(5, t.getArraySize());
        assertEquals(5, t.getArrayElement(4).asInt());
        // edit - [1,20,3,4,5]
        t.setArrayElement(1, 20);
        assertEquals(5, t.getArraySize());
        assertEquals(20, t.getArrayElement(1).asInt());
    }

    @Test
    public void testIterator() {
        // existing iterators
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = [0,1,2]

                        polyglot.register_host_interop_behavior(MyType,
                            is_iterator=False,
                            has_iterator=True,
                            get_iterator=lambda t: iter(t.data)
                        )

                        MyType()
                        """);
        Value iterator = t.getIterator();
        assertTrue(iterator.isIterator());
        assertTrue(iterator.hasIteratorNextElement());
        assertEquals(0, iterator.getIteratorNextElement().asInt());
        assertEquals(1, iterator.getIteratorNextElement().asInt());
        assertEquals(2, iterator.getIteratorNextElement().asInt());
        assertFalse(iterator.hasIteratorNextElement());

        // as iterator behavior
        t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = [0,1,2]
                            idx = 0

                        def has_next_element(t):
                            return t.idx < len(t.data)

                        def get_next_element(t):
                            if t.idx < len(t.data):
                                v = t.data[t.idx]
                                t.idx += 1
                                return v
                            raise StopIteration

                        polyglot.register_host_interop_behavior(MyType,
                            is_iterator=True,
                            has_iterator_next_element=has_next_element,
                            get_iterator_next_element=get_next_element
                        )

                        MyType()
                        """);
        assertTrue(t.isIterator());
        assertTrue(t.hasIteratorNextElement());
        assertEquals(0, t.getIteratorNextElement().asInt());
        assertEquals(1, t.getIteratorNextElement().asInt());
        assertEquals(2, t.getIteratorNextElement().asInt());
        assertFalse(t.hasIteratorNextElement());
    }

    @Test
    public void testExecutable() {
        // existing iterators
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            def foobar(self, *a):
                                return ",".join((str(e) for e in a))

                        polyglot.register_host_interop_behavior(MyType,
                            is_executable=True,
                            execute=lambda t, *a: t.foobar(*a)
                        )

                        MyType()
                        """);
        assertTrue(t.canExecute());
        assertEquals("1,2,3", t.execute(1, 2, 3).asString());
    }

    @Test
    public void testHash() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = {'a': 1, 'b': 2, 'c': 3}

                            def remove(self, k):
                                del self.data[k]

                            def put(self, k, v):
                                self.data[k] = v

                        polyglot.register_host_interop_behavior(MyType,
                            has_hash_entries=True,
                            get_hash_size=lambda t: len(t.data),
                            get_hash_entries_iterator=lambda t: iter(t.data.items()),
                            get_hash_keys_iterator=lambda t: iter(t.data.keys()),
                            get_hash_values_iterator=lambda t: iter(t.data.values()),
                            is_hash_entry_readable=lambda t, k: k in t.data,
                            is_hash_entry_modifiable=lambda t, k: k in t.data,
                            is_hash_entry_removable=lambda t, k: k in t.data,
                            is_hash_entry_insertable=lambda t, k: k not in t.data,
                            read_hash_value=lambda t, k: t.data.get(k),
                            read_hash_value_or_default=lambda t, k, d: t.data.get(k, d),
                            remove_hash_entry=lambda t, k: t.remove(k),
                            write_hash_entry=lambda t, k, v: t.put(k, v)
                        )

                        MyType()
                        """);
        assertTrue(t.hasHashEntries());
        // size
        assertEquals(3, t.getHashSize());
        // iterators
        Value hashEntriesIter = t.getHashEntriesIterator();
        assertTrue(hashEntriesIter.isIterator());
        assertTrue(hashEntriesIter.hasIteratorNextElement());
        for (int i = 0; i < t.getHashSize(); i++) {
            hashEntriesIter.getIteratorNextElement();
        }
        assertFalse(hashEntriesIter.hasIteratorNextElement());
        assertTrue(t.getHashKeysIterator().isIterator());
        assertTrue(t.getHashValuesIterator().isIterator());
        // read entries
        assertTrue(t.hasHashEntry("a"));
        assertFalse(t.hasHashEntry("x"));
        assertEquals(1, t.getHashValue("a").asInt());
        assertEquals(3, t.getHashValue("c").asInt());
        // remove entries
        t.removeHashEntry("a");
        assertFalse(t.hasHashEntry("a"));
        assertEquals(2, t.getHashSize());
        // write entries
        t.putHashEntry("a", 10);
        assertEquals(3, t.getHashSize());
        assertEquals(10, t.getHashValue("a").asInt());
    }

    @Test
    public void testDate() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            year = 2023
                            month = 12
                            day = 12

                        polyglot.register_host_interop_behavior(MyType,
                            is_date=True,
                            as_date=lambda t: t
                        )

                        MyType()
                        """);
        assertTrue(t.isDate());
        assertEquals(LocalDate.of(2023, 12, 12), t.asDate());
    }
}
