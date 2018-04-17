#! /usr/bin/env python

"""
Semi-micro benchmark for floating point performance.

This is a Python implementation of a floating point benchmark originally on the
Factor language blog:
http://factor-language.blogspot.com/2009/08/performance-comparison-between-factor.html

Local changes:
- Reduced the number of points from 5000000 to 20000. This reduces individual
  iteration times, but we compensate by increasing the number of iterations.
"""

__author__ = "alex.gaynor@gmail.com (Alex Gaynor)"

import time, sys
from math import sin, cos, sqrt


class Point(object):
    def __init__(self, i):
        self.x = x = sin(i)
        self.y = cos(i) * 3
        self.z = (x * x) / 2

    def normalize(self):
        norm = sqrt(self.x ** 2 + self.y ** 2 + self.z ** 2)
        self.x = self.x / norm
        self.y = self.y / norm
        self.z = self.z / norm

    def maximize(self, other):
        self.x = self.x if self.x > other.x else other.x
        self.y = self.y if self.y > other.y else other.y
        self.z = self.z if self.z > other.z else other.z


def maximize(points):
    points = iter(points)
    cur = next(points)
    for p in points:
        cur.maximize(p)
    return cur


def benchmark():
    points = []
    for i in range(20000):
        points.append(Point(i))
    for p in points:
        p.normalize()
    maximize(points)

def measure():
    print("Start timing...")
    start = time.time()
    iteration = int(sys.argv[1])

    for i in range(iteration):
        benchmark()

    duration = "%.3f\n" % (time.time() - start)
    print("bm-float: " + duration)

for i in range(100):
    benchmark()

measure()