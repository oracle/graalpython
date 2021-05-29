def foo():
    import os


def bar():
    import enum


def baz():
    import traceback


def measure(num):
    for i in range(num):
        import sys
        foo()
        bar()
        baz()


def __benchmark__(num=5):
    measure(num)
