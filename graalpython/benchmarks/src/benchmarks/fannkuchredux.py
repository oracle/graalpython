import sys
from array import array

def fannkuch(n):
    p = array('i', range(n))
    q = array('i', range(n))
    s = array('i', range(n))
    sign = 1
    maxflips = 0
    summ = 0
    m = n - 1
    while True:
        q0 = p[0]
        if q0:
            i = 1
            while i < n:
                q[i] = p[i]
                i += 1
            flips = 1
            while True:
                qq = q[q0]
                if not qq:
                    summ += sign*flips
                    if flips > maxflips:
                        maxflips = flips
                    break
                q[q0] = q0;
                if q0 >= 3:
                    i = 1
                    j = q0 - 1
                    while True:
                        q[i], q[j] = q[j], q[i]
                        i += 1
                        j -= 1
                        if i >= j:
                            break
                q0 = qq
                flips += 1
        if sign == 1:
            p[1], p[0] = p[0], p[1]
            sign = -1
        else:
            p[1], p[2] = p[2], p[1]
            sign = 1
            i = 2
            while i < n:
                sx = s[i]
                if sx != 0:
                    s[i] = sx - 1
                    break
                if i == m:
                    return summ, maxflips
                s[i] = i
                t = p[0]
                j = 0
                while j <= i:
                    p[j] = p[j + 1]
                    j += 1
                p[i + 1] = t
                i += 1

sum, maxflips = fannkuch(int(sys.argv[1]))
print(sum)
print("Pfannkuchen(%d) = %d" % (int(sys.argv[1]), maxflips))
