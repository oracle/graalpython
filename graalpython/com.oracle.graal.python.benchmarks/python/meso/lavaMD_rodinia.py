# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


import random
import math

NUMBER_PER_BOX = 3

# indices representation ### CONSTANTS ###
x = 0  # x index in box
y = 1  # y index in box
z = 2  # z index in box
v = 3  # v index in box
number = 3  # number index in box
offset = 4  # offset index in box
nn = 5  # nn index in box

random.seed(7)


def lavaMD(alpha, boxes1d_arg, number_boxes, box, box_nei, rv, qv, fv):
    a2 = 2.0*alpha*alpha

    #	PROCESS INTERACTIONS
    for ii in range(boxes1d_arg):
        #  home boxes in y direction
        for jj in range(boxes1d_arg):
            #  home boxes in x direction
            for kk in range(boxes1d_arg):
                #	home box - box parameters
                l = ii * boxes1d_arg * boxes1d_arg + jj * boxes1d_arg + kk
                first_i = l * NUMBER_PER_BOX  # offset to common arrays
                #	Do for the # of (home+neighbor) boxes
                for k in range((1+box[l * 6 + nn])):
                    #	neighbor box - get pointer to the right box
                    if (k == 0):
                        pointer = l  # set first box to be processed to home box
                    else:
                        # remaining boxes are neighbor boxes
                        pointer = box_nei[l * 5 * 26 + (k-1) * 5 + number]

                    #	neighbor box - box parameters
                    first_j = box[pointer * 6 + offset]
                    #	Do for the # of particles in home box
                    for i in range(NUMBER_PER_BOX):
                        # do for the # of particles in current (home or neighbor) box
                        for j in range(NUMBER_PER_BOX):
                            #   coefficients			DOT product
                            r1 = rv[(first_i + i) * 4 + x] * \
                                rv[(first_j + j) * 4 + x]
                            r1 += rv[(first_i + i) * 4 + y] * \
                                rv[(first_j + j) * 4 + y]
                            r1 += rv[(first_i + i) * 4 + z] * \
                                rv[(first_j + j) * 4 + z]
                            r2 = rv[(first_i + i) * 4 + v] + \
                                rv[(first_j + j) * 4 + v]
                            r2 -= r1
                            u2 = a2*r2
                            vij = math.exp(-u2)
                            fs = 2.0*vij
                            fxij = fs*(rv[(first_i + i) * 4 + x] -
                                       rv[(first_j + j) * 4 + x])
                            fyij = fs*(rv[(first_i + i) * 4 + y] -
                                       rv[(first_j + j) * 4 + y])
                            fzij = fs*(rv[(first_i + i) * 4 + z] -
                                       rv[(first_j + j) * 4 + z])

                            # forces
                            fv[(first_i + i) * 4 + v] += qv[(first_j + j)]*vij
                            fv[(first_i + i) * 4 + x] += qv[(first_j + j)]*fxij
                            fv[(first_i + i) * 4 + y] += qv[(first_j + j)]*fyij
                            fv[(first_i + i) * 4 + z] += qv[(first_j + j)]*fzij


def initializeHomeBoxes(boxes1d_arg, box_py, box_py_nei):
    #  home boxes in z direction
    for i in range(boxes1d_arg):
        #  home boxes in y direction
        for j in range(boxes1d_arg):
            #  home boxes in x direction
            for k in range(boxes1d_arg):
                #  current home box
                nh = i * boxes1d_arg * boxes1d_arg + j * boxes1d_arg + k
                box_py[nh * 6 + x] = k
                box_py[nh * 6 + y] = j
                box_py[nh * 6 + z] = i
                box_py[nh * 6 + number] = nh
                box_py[nh * 6 + offset] = nh * NUMBER_PER_BOX
                #  initialize number of neighbor boxes
                box_py[nh * 6 + nn] = 0
                #  neighbor boxes in z direction
                for l in range(-1, 2):
                    #  neighbor boxes in y direction
                    for m in range(-1, 2):
                        #  neighbor boxes in x direction
                        for n in range(-1, 2):
                            #  check if (this neighbor exists) and (it is not the same as home box)
                            if((((i+l) >= 0 and (j+m) >= 0 and (k+n) >= 0) and ((i+l) < boxes1d_arg and (j+m) < boxes1d_arg and (k+n) < boxes1d_arg)) and not (l == 0 and m == 0 and n == 0)):
                                #  current neighbor box
                                nnn = box_py[nh * 6 + nn]
                                idx = nh * 5 * 26 + nnn * 5
                                box_py_nei[idx + x] = (k+n)
                                box_py_nei[idx + y] = (j+m)
                                box_py_nei[idx + z] = (i+l)
                                box_py_nei[idx + number] = (box_py_nei[idx + z] * boxes1d_arg * boxes1d_arg) + (
                                    box_py_nei[idx + y] * boxes1d_arg) + box_py_nei[idx + x]
                                box_py_nei[idx + offset] = box_py_nei[nh *
                                                                      5 * 26 + nnn * 5 + number] * NUMBER_PER_BOX

                                #  increment neighbor box
                                box_py[nh * 6 + nn] += 1


class Data:
    def __init__(self):
        self.box_py = None
        self.box_py_nei = None
        self.rv_py = None
        self.qv_py = None
        self.fv_py = None


data = Data()


def measure(boxes1d_arg, alpha=0.5):
    print("Initializing...")
    initializeHomeBoxes(boxes1d_arg, data.box_py, data.box_py_nei)

    #  total number of boxes
    number_boxes = boxes1d_arg * boxes1d_arg * boxes1d_arg

    print("Starting...")
    lavaMD(alpha, boxes1d_arg, number_boxes, data.box_py,
           data.box_py_nei, data.rv_py, data.qv_py, data.fv_py)


default_size = 32


def __benchmark__(boxes1d_arg=default_size):
    measure(boxes1d_arg)


def __setup__(boxes1d_arg=default_size):
    #  Print configuration
    print("Configuration used: boxes1d = %d\n" % (boxes1d_arg))
    #  total number of boxes
    number_boxes = boxes1d_arg * boxes1d_arg * boxes1d_arg

    #  how many particles space has in each direction
    space_elem = number_boxes * 3

    # 	BOX
    #  allocate boxes
    data.box_py = [0 for i in range(6 * number_boxes)]
    data.box_py_nei = [0 for i in range(5 * 26 * number_boxes)]

    #  input (distances): get a number in the range 0.1 - 1.0
    data.rv_py = [(random.randint(0, 10) + 1) /
                  10.0 for i in range(4 * space_elem)]

    #  input (charge): get a number in the range 0.1 - 1.0
    data.qv_py = [(random.randint(0, 10) + 1) /
                  10.0 for i in range(space_elem)]

    #  output (forces): set to 0, because kernels keeps adding to initial value
    data.fv_py = [0. for i in range(4 * space_elem)]


def __cleanup__(boxes1d_arg=default_size):
    # clean up written data
    number_boxes = boxes1d_arg * boxes1d_arg * boxes1d_arg
    space_elem = number_boxes * 3
    for i in range(4 * space_elem):
        data.fv_py[i] = 0.
