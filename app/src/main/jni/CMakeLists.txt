cmake_minimum_required(VERSION 3.22.1)
project("androidterminal" C)

add_library(androidterminal SHARED
    terminal_native.c
)

target_link_libraries(androidterminal
    log
)

set_target_properties(androidterminal PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
)
