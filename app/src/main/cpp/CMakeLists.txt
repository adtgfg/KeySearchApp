cmake_minimum_required(VERSION 3.18.1)
project("keysearchapp")

add_library(native-lib SHARED native-lib.cpp)

# Android log
find_library(log-lib log)

# Import prebuilt OpenSSL libraries placed under app/src/main/jniLibs/<ABI>/
add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcrypto.so)

add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libssl.so)

# Link native-lib with Android log and the imported OpenSSL libs
target_link_libraries(
    native-lib
    ${log-lib}
    ssl
    crypto
)