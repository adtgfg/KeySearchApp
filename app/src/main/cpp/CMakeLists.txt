cmake_minimum_required(VERSION 3.10.2)

project(KeySearchApp)

# اجمع كل ملفات C/C++ داخل مجلد cpp تلقائياً
file(GLOB_RECURSE NATIVE_SRC
     *.c
     *.cc
     *.cpp)

add_library(native-lib SHARED ${NATIVE_SRC})

# معيار C++
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# مسار هيدرز OpenSSL التي وضعتها داخل cpp/openssl/include
include_directories(${CMAKE_SOURCE_DIR}/src/main/cpp/openssl/include)

# عرّف مكتبات OpenSSL الجاهزة (.so) من jniLibs حسب الـ ABI
add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libcrypto.so)

add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libssl.so)

# مكتبات أندرويد القياسية
find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)  # OpenSSL قد يعتمد على zlib

# اربط المكتبات مع مكتبتك الأصلية
target_link_libraries(
    native-lib
    ssl
    crypto
    ${z-lib}
    ${log-lib}
    ${android-lib}
)
