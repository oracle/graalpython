# Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import json
import os
import sys
import unittest

BIGINT_JSON_DATA = '''
{
  "int_values": [
    1521583201297000000,
    13,
    5,
    1521583201297000000,
    67,
    87,
    1521583201331000000,
    1521583201347000000,
    10
  ]
}
'''


class JsonTest(unittest.TestCase):
    def test_callable_object_hook(self):
        called = []
        class Hook:
            def __call__(self, obj):
                called.append(True)
                return obj

        hook_instance = Hook()
        result = json.loads('{"a": 1, "b": 2}', object_hook=hook_instance)
        assert len(called) == 1
        assert result == {"a": 1, "b": 2}

    def test_invalid_object_hook(self):
        with self.assertRaises(TypeError):
            json.loads('{"a": 1}', object_hook="not_a_function")

    def test_invalid_object_pairs_hook(self):
        with self.assertRaises(TypeError):
            json.loads('{"a": 1}', object_pairs_hook=12345)

    def test_dump(self):
        cwd = os.getcwd()
        new_file_path = os.path.join(cwd, 'myFile.json')
        with open(new_file_path, 'w') as f:
            json.dump(['a', 'b', 'c'], f)
        with open(new_file_path) as f:
            assert json.load(f) == ['a', 'b', 'c']
        os.remove(new_file_path)

    def test_load_bigint(self):
        data = json.loads(BIGINT_JSON_DATA)
        assert "int_values" in data
        int_values_ = data['int_values']
        assert isinstance(int_values_, list)
        assert set(int_values_) == {
            1521583201297000000,
            13,
            5,
            1521583201297000000,
            67,
            87,
            1521583201331000000,
            1521583201347000000,
            10,
        }

    def test_encode_surrogate(self):
        s = json.dumps({'foo': "\uda6a"})
        assert s == '{"foo": "\\uda6a"}'
        s = json.dumps({'foo': "\uda6a"}, ensure_ascii=False)
        assert s == '{"foo": "\uda6a"}'

    def test_dump_skipkeys_invalid_middle_key(self):
        assert json.dumps({"first": 1, b"bad": 2, "last": 3}, skipkeys=True) == '{"first": 1, "last": 3}'

    def test_dump_skipkeys_invalid_trailing_key(self):
        assert json.dumps({"first": 1, b"bad": 2}, skipkeys=True) == '{"first": 1}'

    def test_dump_skipkeys_default_returned_dict(self):
        class InvalidKey:
            pass

        class Unsupported:
            pass

        def default(obj):
            if isinstance(obj, Unsupported):
                return {"first": 1, InvalidKey(): 2, "last": 3}
            raise TypeError

        assert json.dumps(Unsupported(), default=default, skipkeys=True) == '{"first": 1, "last": 3}'

    @unittest.skipUnless(sys.implementation.name == "graalpy", "fixed only in later CPython versions, bug gh-110941")
    def test_dump_empty_storage_dict_subclass_with_items(self):
        class StaticDict(dict):
            def keys(self):
                return ["a", "b"]

            def values(self):
                return [1, 2]

            def items(self):
                return zip(self.keys(), self.values())

            def __len__(self):
                return 2

        assert json.dumps(StaticDict()) == '{"a": 1, "b": 2}'

    def test_object_hook_nested(self):
        def hook(obj):
            return "hooked"

        assert json.loads('{"outer": {"inner": {"leaf": 1}}}', object_hook=hook) == "hooked"
        assert json.loads('{"outer": {"inner": {"leaf": 1}}}', object_pairs_hook=hook) == "hooked"

    def test_object_hook_nested_list_and_object(self):
        def hook(obj):
            return obj

        payload = (
            '{"seq": 2, "type": "request", "command": "attach", '
            '"arguments": {"justMyCode": true, "name": "Test", "type": "python", '
            '"program": "/tmp/code.py", "args": [], '
            '"connect": {"host": "127.0.0.1", "port": 5681}, '
            '"debugOptions": ["ShowReturnValue"]}}'
        )
        expected = {
            "seq": 2,
            "type": "request",
            "command": "attach",
            "arguments": {
                "justMyCode": True,
                "name": "Test",
                "type": "python",
                "program": "/tmp/code.py",
                "args": [],
                "connect": {"host": "127.0.0.1", "port": 5681},
                "debugOptions": ["ShowReturnValue"],
            },
        }
        assert json.loads(payload, object_hook=hook) == expected
        assert json.loads(payload, object_pairs_hook=dict) == expected
