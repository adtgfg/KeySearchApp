cmake_minimum_required(VERSION 3.10.2)

project(KeySearchApp)

# جمع كل ملفات ++C
file(GLOB_RECURSE NATIVE_SRC
     *.c
     *.cc
     *.cpp)

add_library(native-lib SHARED ${NATIVE_SRC})

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# الهيدرز
include_directories(${CMAKE_SOURCE_DIR}/openssl/include)

# المكتبات الجاهزة
add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/lib/libcrypto.so)

add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/lib/libssl.so)

# مكتبات أندرويد
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)

target_link_libraries(
    native-lib
    ssl
    crypto
    ${z-lib}
    ${log-lib}
    ${android-lib}
)
