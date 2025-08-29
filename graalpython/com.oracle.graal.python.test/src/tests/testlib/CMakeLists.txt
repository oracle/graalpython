cmake_minimum_required(VERSION 3.5)
project(testlib C)
add_library(answer SHARED answer.c)
set_target_properties(answer PROPERTIES PUBLIC_HEADER "answer.h")
