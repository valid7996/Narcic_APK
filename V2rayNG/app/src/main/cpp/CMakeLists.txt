cmake_minimum_required(VERSION 3.22.1)

project(narcicdesync C)

# Embedded, rootless DPI-desync engine (ciadpi/byedpi, MIT licensed),
# built as a local SOCKS proxy that Xray's outbound dials through.
set(CORE_DIR "desync_engine")

file(GLOB CORE_SOURCES ${CORE_DIR}/*.c)

add_library(narcicdesync SHARED ${CORE_SOURCES} native-lib.c utils.c)
target_include_directories(narcicdesync PRIVATE ${CORE_DIR})
target_compile_options(narcicdesync PRIVATE -std=c99 -O2 -D_XOPEN_SOURCE=500)
target_compile_definitions(narcicdesync PRIVATE ANDROID_APP)
target_link_libraries(narcicdesync PRIVATE android log)
