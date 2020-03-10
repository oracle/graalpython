# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

JAVA_CODE = """
import java.util.ArrayList;
import java.util.List;

public class Image {
    private final int width;
    private final int height;
    private final List<Integer> data;

    public Image(int width, int height, List<Integer> data) {
        this.width = width;
        this.height = height;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new ArrayList<Integer>(width * height);
            for (int i = 0; i < width * height; i++) {
                this.data.add(0);
            }
        }
    }

    int _idx(int x, int y) {
        if (0 <= x && x < width && 0 <= y && y < height) {
            return y * width + x;
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    int __getitem__(int x, int y) {
        return data.get(_idx(x, y));
    }

    void __setitem__(int x, int y, int val) {
        data.set(_idx(x, y), val);
    }

    public Image sobel(boolean horizontal, boolean vertical) {
        Image out = new Image(width, height, null);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double dx;
                if (horizontal) {
                    dx = -1.0 * __getitem__(x - 1, y - 1) + 1.0 * __getitem__(x + 1, y - 1) +
                        -2.0 * __getitem__(x - 1, y)     + 2.0 * __getitem__(x + 1, y) +
                        -1.0 * __getitem__(x - 1, y + 1) + 1.0 * __getitem__(x + 1, y + 1);
                } else {
                    dx = __getitem__(x, y);
                }
                double dy;
                if (vertical) {
                    dy = -1.0 * __getitem__(x - 1, y - 1) - 2.0 * __getitem__(x, y - 1) - 1.0 * __getitem__(x + 1, y - 1) +
                        1.0 * __getitem__(x - 1, y + 1) + 2.0 * __getitem__(x, y + 1) + 1.0 * __getitem__(x + 1, y + 1);
                } else {
                    dy = __getitem__(x, y);
                }
                out.__setitem__(x, y, (int) Math.min(Math.round(Math.sqrt(dx * dx + dy * dy) / 4.0), 255));
            }
        }
        return out;
    }
}
"""


def __setup__(*args):
    import os
    __dir__ = os.path.dirname(__file__)
    javafile = os.path.join(__dir__, "Image.java")
    with open(javafile, "w") as f:
        f.write(JAVA_CODE)
    os.system("javac " + javafile)

    import java
    java.add_to_classpath(__dir__)
    global Image
    Image = java.type("Image")


SZ = 20


def measure(num):
    img = Image(SZ, SZ, list(range(SZ * SZ)))
    for i in range(num):
        img = img.sobel(True, True)
    return img


def __benchmark__(num=10000):
    return measure(num)


if __name__ == '__main__':
    import sys
    import time
    start = time.time()
    if len(sys.argv) >= 2:
        num = int(sys.argv[1])
        img = __benchmark__(num)
    else:
        img = __benchmark__(2)
    print(img.data)
    print("%s took %s s" % (__file__, time.time() - start))
