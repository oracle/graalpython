# Copyright (c) 2017, 2026, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# micro benchmark: megamorphic attribute access inspired by richards

iteration = 20000


class TaskState(object):
    pass


class Task(TaskState):
    def __init__(self, foo):
        self.foo = foo


class Task0(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task1(Task0):
    def __init__(self, foo):
        Task.__init__(self, foo)


class Task2(Task1):
    def __init__(self, foo):
        Task.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task3(Task2):
    def __init__(self, foo):
        Task.__init__(self, foo)


class Task4(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task5(Task4):
    def __init__(self, foo):
        Task.__init__(self, foo)


class Task6(Task5):
    def __init__(self, foo):
        Task.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task7(Task6):
    def __init__(self, foo):
        Task.__init__(self, foo)


class Task8(TaskState):
    def __init__(self, foo):
        self.foo = foo


class Task9(Task8):
    def __init__(self, foo):
        Task8.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task10(Task9):
    def __init__(self, foo):
        Task8.__init__(self, foo)


class Task11(Task10):
    def __init__(self, foo):
        Task8.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task12(TaskState):
    def __init__(self, foo):
        self.foo = foo


class Task13(Task12):
    def __init__(self, foo):
        Task12.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


class Task14(Task13):
    def __init__(self, foo):
        Task12.__init__(self, foo)


class Task15(Task14):
    def __init__(self, foo):
        Task12.__init__(self, foo)
        self.bar = foo + 1
        self.baz = foo + 2


TASK_LIST = [Task0(0), Task1(1), Task2(2), Task3(3),
             Task4(4), Task5(5), Task6(6), Task7(7),
             Task8(8), Task9(9), Task10(10), Task11(11),
             Task12(12), Task13(13), Task14(14), Task15(15)]


def do_stuff():
    task_list = TASK_LIST
    total = 0
    for i in range(iteration):
        for t in task_list:
            total = (total + t.foo) % 7

    return total


def measure(num):
    result = 0
    for i in range(num):  # 50000
        result |= do_stuff()

    return result


def __benchmark__(num=1000):
    return measure(num)


def run():
    __benchmark__(num=10)

