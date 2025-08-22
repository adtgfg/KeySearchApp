cmake_minimum_required(VERSION 3.10.2)

# تعريف اسم المشروع
project("keysearchapp")

# إضافة مكتبة "native-lib" المشتركة من ملف native-lib.cpp
add_library(
        native-lib
        SHARED
        native-lib.cpp)

# البحث عن مكتبة سجلات أندرويد (log)
find_library(log-lib log)

# ربط المكتبة الأصلية مع مكتبات النظام الخارجية المطلوبة
target_link_libraries(
        native-lib
        ${log-lib}
        ssl
        crypto)

