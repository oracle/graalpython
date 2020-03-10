# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
# micro benchmark: attribute access polymorphic inspired by richards

iteration = 20000  # 50000


class TaskState(object):
    pass


class Task(TaskState):
    def __init__(self, foo):
        self.foo = foo


class DeviceTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class HandlerTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class IdleTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


class WorkTask(Task):
    def __init__(self, foo):
        Task.__init__(self, foo)


TASK_LIST = [DeviceTask(0), DeviceTask(1), DeviceTask(2), DeviceTask(3),
             HandlerTask(4), HandlerTask(5), HandlerTask(6), HandlerTask(7),
             IdleTask(8), IdleTask(9), IdleTask(10), IdleTask(11),
             WorkTask(12), WorkTask(13), WorkTask(14), WorkTask(15)]


def do_stuff():
    task_list = TASK_LIST
    total = 0
    for i in range(iteration):
        for t in task_list:
            total = (total + t.foo) % 7

    return total


def measure(num):
    for i in range(num):  # 50000
        result = do_stuff()

    print(result)


def __benchmark__(num=1000):
    measure(num)
