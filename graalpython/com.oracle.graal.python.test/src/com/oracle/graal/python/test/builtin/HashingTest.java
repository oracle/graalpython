/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin;

import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class HashingTest {

    @Test
    public void mappingproxyTest() {
        String source = "_mappingproxy = type(type.__dict__)\n" +
                        "d = {\"a\": 1, \"b\": 2, \"c\": 3}\n" +
                        "mp = _mappingproxy(d)\n" +
                        "assert len(mp) == 3\n" +
                        "assert d.keys() == {'a', 'b', 'c'}\n" +
                        "assert mp.keys() == d.keys()\n" +
                        "assert len(mp.keys()) == 3, \"keys view has invalid length\"\n" +
                        "assert set(mp.keys()) == {'a', 'b', 'c'}, \"keys view invalid\"\n" +
                        "assert len(mp.values()) == 3, \"values view has invalid length\"\n" +
                        "assert set(mp.values()) == {1, 2, 3}, \"values view invalid\"\n" +
                        "assert len(mp.items()) == 3, \"items view has invalid length\"\n" +
                        "assert set(mp.items()) == {('a', 1), ('b', 2), ('c', 3)}, \"items view invalid\"\n";
        assertPrints("", source);
    }

    @Test
    public void customMappingObjectTest() {
        String source = "class CustomMappingObject:\n" +
                        "    def __init__(self, keys, values):\n" +
                        "        self._keys = keys\n" +
                        "        self._values = values\n" +
                        "    def __getitem__(self, k):\n" +
                        "        for i in range(len(self._keys)):\n" +
                        "            if k == self._keys[i]:\n" +
                        "                return self._values[i]\n" +
                        "        raise KeyError\n" +
                        "    def __setitem__(self, k, v):\n" +
                        "        for i in range(len(self._keys)):\n" +
                        "            if k == self._keys[i]:\n" +
                        "                self._values[i] = v\n" +
                        "                return v\n" +
                        "        raise KeyError\n" +
                        "    def keys(self):\n" +
                        "        return set(self._keys)\n" +
                        "    def values(self):\n" +
                        "        return self._values\n" +
                        "    def items(self):\n" +
                        "        return {(self._keys[i], self._values[i]) for i in range(len(self._keys))}\n" +
                        "    def __len__(self):\n" +
                        "        return len(self._keys)\n" +
                        "_mappingproxy = type(type.__dict__)\n" +
                        "mp_list = _mappingproxy(CustomMappingObject([\"a\", \"b\", \"c\"], [1, 2, 3]))\n" +
                        "assert mp_list.keys() == {\"a\", \"b\", \"c\"}\n" +
                        "";
        assertPrints("", source);
    }

    @Test
    public void dictViewTest() {
        String source = "d = dict()\n" +
                        "d['a'] = 1\n" +
                        "d['b'] = 2\n" +
                        "d['c'] = 3\n" +
                        "assert len(d) == 3\n" +
                        "assert len(d.keys()) == 3, \"keys view has invalid length\"\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}, \"keys view invalid\"\n" +
                        "assert len(d.values()) == 3, \"values view has invalid length\"\n" +
                        "assert set(d.values()) == {1, 2, 3}, \"values view invalid\"\n" +
                        "assert len(d.items()) == 3, \"items view has invalid length\"\n" +
                        "assert set(d.items()) == {('a', 1), ('b', 2), ('c', 3)}, \"items view invalid\"\n" +
                        "";
        assertPrints("", source);
    }

    @Test
    public void dictEqualTest1() {
        String source = "d = dict.fromkeys(['a', 'b', 'c'])\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {None}\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], 1)\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {1}\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], 1.0)\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {1.0}\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], 'd')\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {'d'}\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], None)\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {None}\n" +
                        "o = object()\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], o)\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert set(d.values()) == {o}\n" +
                        "";
        assertPrints("", source);
    }

    @Test
    public void dictEqualTest2() {
        String source = "d = dict(a=1, b=2, c=3)\n" +
                        "def assert_raises(err, fn, *args, **kwargs):\n" +
                        "    raised = False\n" +
                        "    try:\n" +
                        "        fn(*args, **kwargs)\n" +
                        "    except err:\n" +
                        "        raised = True\n" +
                        "    assert raised\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'])\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert list(d.values()) == [None, None, None]\n" +
                        "d = dict.fromkeys(['a', 'b', 'c'], 1)\n" +
                        "assert len(d) == 3\n" +
                        "assert set(d.keys()) == {'a', 'b', 'c'}\n" +
                        "assert list(d.values()) == [1, 1, 1]\n" +
                        "assert_raises(TypeError, dict.fromkeys, 10)\n" +
                        "";
        assertPrints("", source);
    }

    @Test
    public void dictEqualTest3() {
        String source = "key_set = {'a', 'b', 'c', 'd'}\n" +
                        "\n" +
                        "class CustomMappingObject:\n" +
                        "    def __init__(self, keys):\n" +
                        "        self.__keys = keys\n" +
                        "\n" +
                        "    def keys(self):\n" +
                        "        return self.__keys\n" +
                        "\n" +
                        "    def __getitem__(self, key):\n" +
                        "        if key in self.__keys:\n" +
                        "            return ord(key)\n" +
                        "        raise KeyError(key)\n" +
                        "\n" +
                        "    def __len__(self):\n" +
                        "        return len(self.keys)\n" +
                        "\n" +
                        "d = dict(CustomMappingObject(key_set))\n" +
                        "assert len(d) == 4, \"invalid length, expected 4 but was %d\" % len(d)\n" +
                        "assert set(d.keys()) == key_set, \"unexpected keys: %s\" % str(d.keys())\n" +
                        "assert set(d.values()) == {97, 98, 99, 100}, \"unexpected values: %s\" % str(d.values())\n" +
                        "";
        assertPrints("", source);
    }

    @Test
    public void setAndTest() {
        assertPrints("{2}\n", "print({2, 3} ^ {3})\n");
        assertPrints("{'c', 'b'}\n", "print({'a', 'c'} ^ frozenset({'a', 'b'}))\n");
        assertPrints("frozenset({'b'})\n", "print(frozenset({'a', 'c'}) ^ {'a', 'b', 'c'})\n");
    }
}
