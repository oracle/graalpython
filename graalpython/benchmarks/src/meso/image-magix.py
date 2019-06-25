# Copyright (c) 2017, 2019, Oracle and/or its affiliates.
# Copyright (c) 2013, Pablo Mouzo
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from array import array
from math import sqrt, atan2, sqrt, sin, cos, ceil, floor


class Image(object):
    def __init__(self, width, height, data=None):
        self.width = width
        self.height = height
        if data:
            self.data = data
        else:
            self.data = [0] * (width * height)

    def _idx(self, x, y):
        if 0 <= x < self.width and 0 <= y < self.height:
            return y * self.width + x
        raise IndexError

    def __getitem__(self, t):
        x, y = t
        return self.data[self._idx(x, y)]

    def __setitem__(self, t, val):
        x, y = t
        self.data[self._idx(x, y)] = val

    def pixels(self, border=0):
        for y in xrange(border, self.height - border):
            for x in xrange(border, self.width - border):
                yield x, y

    def sobel(self, horizontal=True, vertical=True):
        out = Image(self.width, self.height)
        for y in range(1, self.height - 1):
            for x in range(1, self.width - 1):
                if horizontal:
                    dx = -1.0 * self[x - 1, y - 1] + 1.0 * self[x + 1, y - 1] + \
                         -2.0 * self[x - 1, y]     + 2.0 * self[x + 1, y] + \
                         -1.0 * self[x - 1, y + 1] + 1.0 * self[x + 1, y + 1]
                else:
                    dx = self[x, y]
                if vertical:
                    dy = -1.0 * self[x - 1, y - 1] - 2.0 * self[x, y - 1] - 1.0 * self[x + 1, y - 1] + \
                         1.0 * self[x - 1, y + 1] + 2.0 * self[x, y + 1] + 1.0 * self[x + 1, y + 1]
                else:
                    dy = self[x, y]
                out[x, y] = min(int(sqrt(dx * dx + dy * dy) / 4.0), 255)
        return out

    def fisheye(img, fraction=2, bilinear=False):
        if bilinear:
            img = BilinImage(img.width, img.height, data=img.data)
        else:
            img = NNImage(img.width, img.height, data=img.data)
        out = Image(img.width, img.height, data=img.data[:])
        maxr = img.height / (fraction + 1)
        for y in range(int(img.height / 2 - maxr), int(img.height / 2 + maxr)):
            for x in range(int(img.width / 2 - maxr), int(img.width / 2 + maxr)):
                dx, dy = x - img.width / 2, y - img.height / 2
                a = atan2(dy, dx)
                r = sqrt(dx ** 2 + dy ** 2)
                if r < maxr:
                    nr = r * r / maxr
                    nx, ny = nr * cos(a), nr * sin(a)
                    out[x,y] = min(int(img[nx + img.width / 2, ny + img.height / 2]), 255)
                else:
                    out[x,y] = img[x,y]
        return out


class NNImage(Image):
    def __getitem__(self, t):
        x, y = t
        return Image.__getitem__(self, (int(x + 0.5), int(y + 0.5)))


class BilinImage(Image):
    def __getitem__(self, t):
        x, y = t
        if isinstance(x, float) and isinstance(y, float):
            x0, x1 = int(floor(x)), int(ceil(x))
            y0, y1 = int(floor(y)), int(ceil(y))
            xoff, yoff = x - x0, y - y0
            return (1.0 - xoff) * (1.0 - yoff) * self[x0, y0] + \
                   (1.0 - xoff) * (      yoff) * self[x0, y1] + \
                   (      xoff) * (1.0 - yoff) * self[x1, y0] + \
                   (      xoff) * (      yoff) * self[x1, y1]
        else:
            return Image.__getitem__(self, (x, y))


SZ = 20


def measure(num):
    img = Image(SZ, SZ, data=list(range(SZ * SZ)))
    for i in range(num):
        img = img.sobel(horizontal=True, vertical=True)
        img = img.fisheye(bilinear=True, fraction=3)
    return img


def __benchmark__(num=10000):
    return measure(num)


if __name__ == '__main__':
    import sys
    import time
    SZ = 5
    start = time.time()
    if len(sys.argv) >= 2:
        num = int(sys.argv[1])
        img = __benchmark__(num)
    else:
        img = __benchmark__(2)
    print(img.data)
    print("%s took %s s" % (__file__, time.time() - start))
