cmake_minimum_required(VERSION 3.22.1)
project("terminal-native" C)

add_library(terminal-native SHARED terminal_jni.c)
target_link_libraries(terminal-native log)
