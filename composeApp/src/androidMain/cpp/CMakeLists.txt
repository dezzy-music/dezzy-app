# Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
# Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.

cmake_minimum_required(VERSION 3.22.1)

project("dezzy_dsp" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(dezzy_dsp SHARED
    dezzy_dsp.cpp
)

target_compile_options(dezzy_dsp PRIVATE
    -O3
    -fno-math-errno
    -ffp-contract=fast
    -fno-exceptions
    -fno-rtti
)

find_library(log-lib log)
find_library(m-lib m)

target_link_libraries(dezzy_dsp
    ${log-lib}
    ${m-lib}
)
