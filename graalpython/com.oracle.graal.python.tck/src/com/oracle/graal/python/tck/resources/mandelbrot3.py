# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
#
# contributed by Tupteq
# 2to3 - fixed by Daniele Varrazzo

def main():
    size = 10
    xr_size = range(size)
    xr_iter = range(50)
    bit = 128
    byte_acc = 0

    size = float(size)
    for y in xr_size:
        fy = 2j * y / size - 1j
        for x in xr_size:
            z = 0j
            c = 2. * x / size - 1.5 + fy

            for i in xr_iter:
                z = z * z + c
                if abs(z) >= 2.0:
                    break
            else:
                byte_acc += bit

            if bit > 1:
                bit >>= 1
            else:
                bit = 128
                byte_acc = 0

        if bit != 128:
            bit = 128
            byte_acc = 0

lambda: main()
