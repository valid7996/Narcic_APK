cmake_minimum_required(VERSION 3.22.1)

project(narcic C CXX)

# Embedded, rootless DPI-desync engine (ciadpi/byedpi, MIT licensed),
# built as a local SOCKS proxy that Xray's outbound dials through.
set(CORE_DIR "desync_engine")

file(GLOB CORE_SOURCES ${CORE_DIR}/*.c)

add_library(narcicdesync SHARED ${CORE_SOURCES} native-lib.c utils.c)
target_include_directories(narcicdesync PRIVATE ${CORE_DIR})
target_compile_options(narcicdesync PRIVATE -std=c99 -O2 -D_XOPEN_SOURCE=500)
target_compile_definitions(narcicdesync PRIVATE ANDROID_APP)
target_link_libraries(narcicdesync PRIVATE android log)

# ---------------------------------------------------------------------------
# W on N (ported from MSN-GUARD): badvpn tun2socks + lwIP — whole-device
# TUN -> SOCKS routing used by the Psiphon and Tor paths. Produces
# libtun2socks.so, whose natives register against ca/psiphon/Tun2SocksJniLoader
# (kept in the ca.psiphon package on purpose — the native side FindClass()es it).
add_subdirectory(badvpn)

# The Rust core (libaether.so — MASQUE / WireGuard / WARP-on-WARP transports) is
# cross-compiled from core/aether by the Gradle task (core/build-android.sh) and
# dropped in src/main/jniLibs/<abi>/. aether_jni.cpp is only its thin JNI shim,
# so it is built here only when the Rust artifact is actually present; a build
# without the Rust toolchain still succeeds and just skips the W on N native
# core (the Psiphon and Tor paths do not need it).
set(AETHER_SO "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libaether.so")
if(EXISTS "${AETHER_SO}")
    add_library(aether SHARED IMPORTED)
    set_target_properties(aether PROPERTIES
        IMPORTED_LOCATION "${AETHER_SO}"
        IMPORTED_NO_SONAME TRUE)

    add_library(aether_jni SHARED aether_jni.cpp)
    target_link_options(aether_jni PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
    find_library(log-lib log)
    target_link_libraries(aether_jni aether ${log-lib})
    message(STATUS "W on N: building aether_jni against ${AETHER_SO}")
else()
    message(STATUS "W on N: libaether.so not present for ${ANDROID_ABI} — skipping aether_jni (Rust core not built)")
endif()
