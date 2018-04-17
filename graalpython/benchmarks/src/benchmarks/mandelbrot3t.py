# The Computer Language Benchmarks Game
# http://shootout.alioth.debian.org/
#
# contributed by Tupteq
# 2to3 - fixed by Daniele Varrazzo

#from __future__ import print_function
import sys
import time

def main(num):
    cout = sys.stdout.write
    #size = float(sys.argv[1])
    size_int = num
    xr_size = range(size_int)
    xr_iter = range(50)
    bit = 128
    byte_acc = 0

    cout("P4\n%d %d\n" % (size_int, size_int))

    size = float(size_int)
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
                #cout(chr(byte_acc))
                bit = 128
                byte_acc = 0

        if bit != 128:
             #cout(chr(byte_acc))
             bit = 128
             byte_acc = 0

def measure():
    print("Start timing...")
    start = time.time()
    num = int(sys.argv[1])
    main(num)
    duration = "%.3f\n" % (time.time() - start)
    print()
    print("mandelbrot: " + duration)

# warm up
for run in range(12):
    main(300)
    print()

measure()