cmake_minimum_required(VERSION 3.10.2)

project("keysearchapp")

# إضافة المكتبة الرئيسية (native-lib.cpp)
add_library(
        native-lib
        SHARED
        native-lib.cpp)

# مكتبة log (مدمجة مع أندرويد)
find_library(
        log-lib
        log)

# ربط المكتبة مع log + OpenSSL (إن وجد)
target_link_libraries(
        native-lib
        ${log-lib}
        crypto
        ssl)
