# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import subprocess
import sys
import textwrap
import unittest


def entity_expansion_payload(levels=5, fanout=8):
    entities = ['<!ENTITY e0 "x">']
    for level in range(1, levels + 1):
        value = f"&e{level - 1};" * fanout
        entities.append(f'<!ENTITY e{level} "{value}">')
    dtd = "\n".join(entities)
    return f"<!DOCTYPE doc [\n{dtd}\n]><doc>&e{levels};</doc>".encode()


class PyExpatEntityExpansionLimitTest(unittest.TestCase):

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy-specific test")
    def test_java_backend_internal_entity_expansion_is_limited(self):
        code = textwrap.dedent(f"""
            from xml.parsers import expat

            expat.ParserCreate().Parse({entity_expansion_payload(levels=6)!r}, True)

            parser = expat.ParserCreate()
            try:
                parser.Parse({entity_expansion_payload(levels=7)!r}, True)
            except expat.ExpatError as e:
                expected_code = expat.errors.codes[expat.errors.XML_ERROR_AMPLIFICATION_LIMIT_BREACH]
                if e.code != expected_code:
                    raise SystemExit(f"unexpected error code: {{e.code}} != {{expected_code}}")
                if expat.ErrorString(e.code) != expat.errors.XML_ERROR_AMPLIFICATION_LIMIT_BREACH:
                    raise SystemExit(f"unexpected error string: {{expat.ErrorString(e.code)!r}}")
                raise SystemExit(0)
            raise SystemExit("entity expansion was not limited")
        """)

        result = subprocess.run([
            sys.executable,
            "--vm.Djdk.xml.entityExpansionLimit=0",
            "--vm.Djdk.xml.totalEntitySizeLimit=0",
            "--vm.Djdk.xml.entityReplacementLimit=0",
            "--python.PyExpatModuleBackend=java",
            "-c",
            code,
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
