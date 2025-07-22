# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

def test_basic_functionality():
    """
    This is a basic test to ensure that the module can be imported.
    The main sqlite3 test suite will be silently skipped if the 
    "_sqlite3" module is not available.
    """
    import sqlite3
    import _sqlite3
    conn = sqlite3.connect(':memory:')
    rows = conn.execute("select sqlite_version()")
    assert len(next(rows)[0]) >= 5
    conn.close()


def test_fts5_works():
    # we explicitly enable those features below, but on CPython they might not
    # be available if using some system libsqlite that doesn't have them
    import sqlite3
    conn = sqlite3.connect(':memory:')
    try:
        conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS your_table USING fts5(column1, column2)")
        conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS your_table USING fts4(column1, column2)")
        conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS your_table USING fts3(column1, column2)")
        conn.execute("CREATE VIRTUAL TABLE IF NOT EXISTS your_table USING rtree(column1, column2)")
        sqpi = next(conn.execute("SELECT pi()"))[0]
        assert 3.14 == float(f'{sqpi:.2f}'), sqpi
    except sqlite3.OperationalError:
        import sys
        assert sys.implementation.name != "graalpy"
    conn.close()
