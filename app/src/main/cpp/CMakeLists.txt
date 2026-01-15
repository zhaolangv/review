# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC or SHARED, and provides
# the relative paths to its source code. You can define multiple libraries, and
# CMake builds them for you. Gradle automatically packages shared libraries with
# your APK.

# PaddleOCR 和 OpenCV 已被禁用，不再需要这些库
# set(PaddleLite_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../paddle_lite_libs_v2_10")
# include_directories(${PaddleLite_DIR}/cxx/include)

# set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../OpenCV/sdk/native/jni")
# message(STATUS "opencv dir: ${OpenCV_DIR}")
# find_package(OpenCV REQUIRED)
# message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
# include_directories(${OpenCV_INCLUDE_DIRS})
aux_source_directory(. SOURCES)
set(CMAKE_CXX_FLAGS
        "${CMAKE_CXX_FLAGS} -ffast-math -Ofast -Os"
        )
set(CMAKE_CXX_FLAGS
        "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden -fdata-sections -ffunction-sections"
        )
set(CMAKE_SHARED_LINKER_FLAGS
        "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-z,nocopyreloc -Wl,--allow-shlib-undefined -Wl,--no-fatal-warnings")

add_library(
        # Sets the name of the library.
        Native
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        ${SOURCES})

find_library(
        # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that you want CMake to locate.
        log)

# Paddle Lite 库已被移除
# add_library(
#         # Sets the name of the library.
#         paddle_light_api_shared
#         # Sets the library as a shared library.
#         SHARED
#         # Provides a relative path to your source file(s).
#         IMPORTED)
# 
# set_target_properties(
#         # Specifies the target library.
#         paddle_light_api_shared
#         # Specifies the parameter you want to define.
#         PROPERTIES
#         IMPORTED_LOCATION
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
#         # Provides the path to the library you want to import.
# )


# Specifies libraries CMake should link to your target library. You can link
# multiple libraries, such as libraries you define in this build script,
# prebuilt third-party libraries, or system libraries.

target_link_libraries(
        # Specifies the target library.
        Native
        # paddle_light_api_shared  # 已移除
        # ${OpenCV_LIBS}  # 已移除
        GLESv2
        EGL
        jnigraphics
        ${log-lib}
)

# Paddle Lite 库复制命令已移除（库文件已被删除）
# add_custom_command(
#         TARGET Native
#         POST_BUILD
#         COMMAND
#         ${CMAKE_COMMAND} -E copy
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libc++_shared.so
#         ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libc++_shared.so)
# 
# add_custom_command(
#         TARGET Native
#         POST_BUILD
#         COMMAND
#         ${CMAKE_COMMAND} -E copy
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libpaddle_light_api_shared.so
#         ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libpaddle_light_api_shared.so)
# 
# add_custom_command(
#         TARGET Native
#         POST_BUILD
#         COMMAND
#         ${CMAKE_COMMAND} -E copy
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libhiai.so
#         ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libhiai.so)
# 
# add_custom_command(
#         TARGET Native
#         POST_BUILD
#         COMMAND
#         ${CMAKE_COMMAND} -E copy
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libhiai_ir.so
#         ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libhiai_ir.so)
# 
# add_custom_command(
#         TARGET Native
#         POST_BUILD
#         COMMAND
#         ${CMAKE_COMMAND} -E copy
#         ${PaddleLite_DIR}/cxx/libs/${ANDROID_ABI}/libhiai_ir_build.so
#         ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libhiai_ir_build.so)
