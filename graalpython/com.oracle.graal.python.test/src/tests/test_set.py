# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
# Test of a sorted() written in Python


def test_set_or():
    s1 = {1, 2, 3}
    s2 = {4, 5, 6}
    s3 = {1, 2, 4}
    s4 = {1, 2, 3}

    union = s1 | s2
    assert union == {1, 2, 3, 4, 5, 6}

    union = s1 | s3
    assert union == {1, 2, 3, 4}

    union = s1 | s4
    assert union == {1, 2, 3}
