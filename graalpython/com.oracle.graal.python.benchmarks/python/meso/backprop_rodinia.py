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

"""
 ******************************************************************
 * HISTORY
 * 15-Oct-94  Jeff Shufelt (js), Carnegie Mellon University
 *	Prepared for 15-681, Fall 1994.
 * Modified by Shuai Che
 ******************************************************************
"""

import random
import math

# eta value
ETA = 0.3

# momentum value
MOMENTUM = 0.3

# (16, 1 can not be changed)
n_hidden = 16
n_out = 1


def bpnn_layerforward(l1, l2, conn, n1, n2):
    ## Set up thresholding unit ##
    l1[0] = 1.0
    ## For each unit in second layer ##
    r1 = 1 + n2
    r2 = 1 + n1
    for j in range(1, r1):
        ## Compute weighted Sum of its inputs ##
        Sum = 0.0
        for k in range(r2):
            Sum += conn[k*r1 + j] * l1[k]

        l2[j] = (1.0 / (1.0 + math.exp(-Sum)))


def bpnn_output_error(delta, target, output, nj):
    errSum = 0.0
    for j in range(1, 1 + nj):
        o = output[j]
        t = target[j]
        v = o * (1.0 - o) * (t - o)
        delta[j] = v
        errSum += abs(v)

    return errSum


def bpnn_hidden_error(delta_h, nh, delta_o, no, who, hidden):
    errSum = 0.0
    for j in range(1, 1 + nh):
        Sum = 0.0
        for k in range(1, 1 + no):
            Sum += delta_o[k] * who[j * (1 + no) + k]
        h = hidden[j]
        delta_h[j] = h * (1.0 - h) * Sum
        errSum += abs(delta_h[j])

    return errSum


def bpnn_adjust_weights(delta, ndelta, ly, nly, w, oldw):
    ly[0] = 1.0
    for j in range(1, (1 + ndelta)):
        for k in range(nly + 1):
            val = ETA * delta[j] * ly[k]
            val += MOMENTUM * oldw[k*(1 + ndelta) + j]
            oldw[k*(1 + ndelta) + j] = val
            w[k*(1 + ndelta) + j] += val


def bpnn_train_kernel(_iu_list, _hu_list, _iw_list, _ou_list, _hw_list, _od_list, _t_list, _hd_list, _hw_prev_list, _iw_prev_list, layer_size):
    bpnn_layerforward(_iu_list, _hu_list, _iw_list, layer_size, n_hidden)
    bpnn_layerforward(_hu_list, _ou_list, _hw_list, n_hidden, n_out)
    out_err = bpnn_output_error(_od_list, _t_list, _ou_list, n_out)
    hid_err = bpnn_hidden_error(
        _hd_list, n_hidden, _od_list, n_out, _hw_list, _hu_list)
    bpnn_adjust_weights(_od_list, n_out, _hu_list,
                        n_hidden, _hw_list, _hw_prev_list)
    bpnn_adjust_weights(_hd_list, n_hidden, _iu_list,
                        layer_size, _iw_list, _iw_prev_list)

    return (out_err, hid_err)


class Data:
    def __init__(self):
        self._iu_list = None
        self._hw_list = None
        self._hu_list = None
        self._ou_list = None
        self._hd_list = None
        self._od_list = None
        self._iw_list = None
        self._iw_prev_list = None
        self._hw_prev_list = None
        self._t_list = None

        self.zeros_list = None
        self.random_list = None


data = Data()

default_size = 2 ** 16


def measure(layer_size=default_size):
    print("Starting training kernel")
    bpnn_train_kernel(data._iu_list, data._hu_list, data._iw_list, data._ou_list, data._hw_list,
                      data._od_list, data._t_list, data._hd_list, data._hw_prev_list, data._iw_prev_list, layer_size)


def __benchmark__(layer_size=default_size):
    measure(layer_size)


def __setup__(layer_size=default_size):
    random.seed(7)
    print("Input layer size : %d" % layer_size)
    # Creates a new fully-connected network from scratch,
    # with the given numbers of input, hidden, and output units.
    # Threshold units are automatically included.  All weights are
    # randomly initialized.

    # Space is also allocated for temporary storage (momentum weights,
    # error computations, etc).

    ## the input units ##
    data._iu_list = [random.random() for i in range(layer_size + 1)]

    ## weights from hidden to output layer ##
    data._hw_list = [random.random()
                     for i in range((n_out + 1) * (n_hidden + 1))]

    ## the hidden units ##
    data._hu_list = [0. for i in range(n_hidden + 1)]
    ## the output units ##
    data._ou_list = [0. for i in range(n_out + 1)]

    ## storage for hidden unit error ##
    data._hd_list = [0. for i in range(n_hidden + 1)]
    ## storage for output unit error ##
    data._od_list = [0. for i in range(n_out + 1)]

    ## weights from input to hidden layer ##
    data._iw_list = [0. for i in range((n_hidden + 1) * (layer_size + 1))]

    ## The next two are for momentum ##
    ## previous change on input to hidden wgt ##
    data._iw_prev_list = [0. for i in range((n_hidden + 1) * (layer_size + 1))]
    ## previous change on hidden to output wgt ##
    data._hw_prev_list = [0. for i in range((n_out + 1) * (n_hidden + 1))]

    ## storage for target vector ##
    data._t_list = [0.1 for i in range(n_out + 1)]

    data.zeros_list = [data._hu_list, data._ou_list, data._hd_list,
                       data._od_list, data._iw_list, data._iw_prev_list, data._hw_prev_list]
    data.random_list = [data._iu_list, data._hw_list]


def __cleanup__(layer_size=default_size):
    # clean up written data
    for l in data.zeros_list:
        for i in range(len(l)):
            l[i] = 0.
    for l in data.random_list:
        for i in range(len(l)):
            l[i] = random.random()
    for i in range(len(data._t_list)):
        data._t_list[i] = 0.1
