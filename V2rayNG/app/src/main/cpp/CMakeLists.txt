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

# ---------------------------------------------------------------------------
# AetherST tunnel engine (ported from github.com/immaghzbad/AetherST,
# attribution license — credit PowerSigma Team). cloak obfuscation scanner +
# hev-socks5-tunnel JNI. The hev third-party tree (yaml/lwip/hev-task-system/
# hev-socks5-tunnel) comes from AetherST's bundled copy and is linked into a
# differently-named shared lib (hev-tun2socks-jni), so it does not clash with
# the standalone libhev-socks5-tunnel.so that TProxyService loads.
# ---------------------------------------------------------------------------
set(AETHER_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# --- cloak (shared lib, JNI class io/github/immaghzbad/.../CloakNative) ---
file(GLOB CLOAK_SOURCES cloak/*.c)
add_library(cloak SHARED ${CLOAK_SOURCES})
target_compile_options(cloak PRIVATE -O2 -Wall -D__ANDROID__ -Wno-unused-parameter -Wno-deprecated-declarations)
target_link_libraries(cloak PRIVATE log)
target_link_options(cloak PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)

# --- hev third-parties (static) ---
set(HEV_DIR ${AETHER_DIR}/third_party/hev-socks5-tunnel)

file(GLOB_RECURSE YAML_SOURCES ${HEV_DIR}/third-part/yaml/src/*.c)
add_library(yaml STATIC ${YAML_SOURCES})
target_include_directories(yaml PUBLIC ${HEV_DIR}/third-part/yaml/include)
# Version macros come from third-part/yaml/configs.mk in the ndk-build tree —
# CMake must define them (api.c reads YAML_VERSION_STRING/MAJOR/MINOR/PATCH
# and no header in the yaml tree declares them).
target_compile_definitions(yaml PRIVATE
    YAML_VERSION_MAJOR=0
    YAML_VERSION_MINOR=2
    YAML_VERSION_PATCH=5
    YAML_VERSION_STRING="0.2.5")

file(GLOB_RECURSE LWIP_SOURCES
    ${HEV_DIR}/third-part/lwip/src/api/*.c
    ${HEV_DIR}/third-part/lwip/src/core/*.c
    ${HEV_DIR}/third-part/lwip/src/netif/*.c
    ${HEV_DIR}/third-part/lwip/src/ports/unix/lib/*.c
    ${HEV_DIR}/third-part/lwip/src/ports/unix/netif/*.c)
add_library(lwip STATIC ${LWIP_SOURCES})
target_include_directories(lwip PUBLIC
    ${HEV_DIR}/third-part/lwip/src/include
    ${HEV_DIR}/third-part/lwip/src/ports/unix/include
    ${HEV_DIR}/third-part/lwip/src/ports/include)
# Mirrors third-part/lwip/Android.mk LOCAL_CFLAGS.
target_compile_definitions(lwip PRIVATE FD_SET_DEFINED SOCKLEN_T_DEFINED)

file(GLOB_RECURSE HTS_SOURCES ${HEV_DIR}/third-part/hev-task-system/*.c)
add_library(hev-task-system STATIC ${HTS_SOURCES})
target_include_directories(hev-task-system PUBLIC ${HEV_DIR}/third-part/hev-task-system/include)
# Mirrors third-part/hev-task-system/configs.mk CONFIG_CFLAGS (defaults).
target_compile_definitions(hev-task-system PRIVATE
    ENABLE_STACK_OVERFLOW_DETECTION
    ENABLE_MEMALLOC_SLICE
    ENABLE_IO_SPLICE_SYSCALL
    CONFIG_STACK_BACKEND=STACK_MMAP
    CONFIG_STACK_OVERFLOW_DETECTION=1
    CONFIG_MEMALLOC_SLICE_ALIGN=16
    CONFIG_MEMALLOC_SLICE_MAX_SIZE=4096
    CONFIG_MEMALLOC_SLICE_MAX_COUNT=1000
    CONFIG_SCHED_CLOCK=CLOCK_NONE)

file(GLOB_RECURSE HEV_TUNNEL_SOURCES
    ${HEV_DIR}/src/*.c
    ${HEV_DIR}/src/misc/*.c
    ${HEV_DIR}/src/core/*.c)
list(FILTER HEV_TUNNEL_SOURCES EXCLUDE REGEX "hev-jni\\.c$")
add_library(hev-socks5-tunnel-static STATIC ${HEV_TUNNEL_SOURCES})
target_include_directories(hev-socks5-tunnel-static PRIVATE
    ${HEV_DIR}/src
    ${HEV_DIR}/src/misc
    ${HEV_DIR}/src/core/include
    ${HEV_DIR}/third-part/yaml/include
    ${HEV_DIR}/third-part/lwip/src/include
    ${HEV_DIR}/third-part/lwip/src/ports/include
    ${HEV_DIR}/third-part/lwip/src/ports/unix/include
    ${HEV_DIR}/third-part/hev-task-system/include)
target_compile_definitions(hev-socks5-tunnel-static PRIVATE
    FD_SET_DEFINED SOCKLEN_T_DEFINED ENABLE_LIBRARY COMMIT_ID="aether")
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(hev-socks5-tunnel-static PRIVATE -mfpu=neon)
endif()

# --- hev-tun2socks-jni (shared, JNI class path from jni_package_config.h) ---
add_library(hev-tun2socks-jni SHARED hev_tun2socks_jni.c)
target_include_directories(hev-tun2socks-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${HEV_DIR}/src
    ${HEV_DIR}/src/misc
    ${HEV_DIR}/src/core/include)
target_compile_definitions(hev-tun2socks-jni PRIVATE
    FD_SET_DEFINED SOCKLEN_T_DEFINED ENABLE_LIBRARY COMMIT_ID="9a06bc6")
target_link_libraries(hev-tun2socks-jni PRIVATE
    hev-socks5-tunnel-static yaml lwip hev-task-system log)
target_link_options(hev-tun2socks-jni PRIVATE
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
