# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (c) 2013-2016, Regents of the University of California
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

# Oct 19, 2015
# numba-benchmarks
# https://github.com/numba/numba-benchmark
#
# originally by Antoine Pitrou
# modified by myq
# remove classes and add pure nbody python

"""
Benchmark an implementation of the N-body simulation.

As in the CUDA version, we only compute accelerations and don't care to
update speeds and positions.
"""

from __future__ import division

def test_nbody():
    n_bodies = 16

    positions = [[0.76805746460138, -0.5228139230316793], [-0.5991956149660083, -0.47555504642935853],
                [-0.7020359542044337, 0.3038111469357141], [-0.34670159144966406, 0.31014135724965075],
                [-0.10826187533509635, 0.6230733903479946], [0.3822781879247321, -0.08939168547494036],
                [0.34063510574388944, -0.6649603911271325], [0.1725668224080743, 0.011714728165011623],
                [0.8042952709018529, 0.2848322515728883], [0.09994722560095437, 0.093823184499354],
                [-0.6269597093918231, -0.8423311136485288], [-0.6132558984588423, 0.932848373509682],
                [0.7641438030155772, 0.41481249329138015], [0.08447688106204598, -0.7544715676685332],
                [0.345008023967718, 0.4345562265505478], [0.17280505970356752, -0.24661570490474816]]

    weights = [1.9796910127152212, 1.8793469121522173, 1.389219866350441, 1.4787098745714768,
                1.4392298363460903, 1.5718778884818412, 1.5473247242143708, 1.4978470382961593,
                1.3034495515007873, 1.4888096243800564, 1.010720089891421, 1.0492383262672078,
                1.1222540101360987, 1.7729637404264191, 1.274221952886791, 1.1803853964002908]

    accelerations = [[0.0 for i in range(2)] for j in range(n_bodies)]

    def run_nbody(n, positions, weights):
        for i in range(n):
            ax = 0.0
            ay = 0.0
            for j in range(n):
                rx = positions[j][0] - positions[i][0]
                ry = positions[j][1] - positions[i][1]
                sqr_dist = rx * rx + ry * ry + 1e-6
                sixth_dist = sqr_dist * sqr_dist * sqr_dist
                inv_dist_cube = 1.0 / (sixth_dist ** 0.5)
                s = weights[j] * inv_dist_cube
                ax += s * rx
                ay += s * ry
            accelerations[i][0] = ax
            accelerations[i][1] = ay

    def time_nbody():
        run_nbody(n_bodies, positions, weights)


    time_nbody()
    assert accelerations == [
        [-22.62210194193863, 9.618242744937728],
        [13.263051063854693, 0.8260066918954928],
        [25.120556640717176, -3.100621794584926],
        [11.53806350789511, -3.5463051095119624],
        [1.500557931736798, -24.709058028451864],
        [-49.42915670227889, 8.651936265335848],
        [-20.470135708339853, 15.86674631665035],
        [-55.89760749787459, 66.65201058516145],
        [-39.518026097616925, 48.26310880217063],
        [95.36866152288519, -103.54666946538075],
        [11.358044456308408, 21.5330532382369],
        [9.325062088265275, -14.679387967839677],
        [-0.4156364149293482, -78.34797412793763],
        [20.425929446025247, 25.609388671743652],
        [-7.203572107433892, -26.692863942051243],
        [17.390387793063407, 40.63086695509797]
    ]
