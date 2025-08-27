# Install script for directory: /Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/Users/qamartahir/Library/Android/sdk/ndk/21.4.7075529/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-objdump")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/libZXing.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include/ZXing" TYPE FILE FILES
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Barcode.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/BarcodeFormat.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/BitHacks.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ByteArray.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/CharacterSet.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Content.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Error.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Flags.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/GTIN.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ImageView.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Point.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Quadrilateral.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Range.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ReadBarcode.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ReaderOptions.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/StructuredAppend.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/TextUtfEncoding.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ZXingCpp.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ZXAlgorithms.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/ZXVersion.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/DecodeHints.h"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/src/main/cpp/zxing-cpp/core/src/Result.h"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include/ZXing" TYPE FILE FILES "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/Version.h")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing/ZXingTargets.cmake")
    file(DIFFERENT EXPORT_FILE_CHANGED FILES
         "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing/ZXingTargets.cmake"
         "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/CMakeFiles/Export/lib/cmake/ZXing/ZXingTargets.cmake")
    if(EXPORT_FILE_CHANGED)
      file(GLOB OLD_CONFIG_FILES "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing/ZXingTargets-*.cmake")
      if(OLD_CONFIG_FILES)
        message(STATUS "Old export file \"$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing/ZXingTargets.cmake\" will be replaced.  Removing files [${OLD_CONFIG_FILES}].")
        file(REMOVE ${OLD_CONFIG_FILES})
      endif()
    endif()
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing" TYPE FILE FILES "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/CMakeFiles/Export/lib/cmake/ZXing/ZXingTargets.cmake")
  if("${CMAKE_INSTALL_CONFIG_NAME}" MATCHES "^([Dd][Ee][Bb][Uu][Gg])$")
    file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing" TYPE FILE FILES "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/CMakeFiles/Export/lib/cmake/ZXing/ZXingTargets-debug.cmake")
  endif()
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/pkgconfig" TYPE FILE FILES "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/zxing.pc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/ZXing" TYPE FILE FILES
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/ZXingConfig.cmake"
    "/Users/qamartahir/mobile/Reposi41xHighlighter/node_modules/vision-camera-code-scanner/android/.cxx/Debug/6t5x3w18/x86_64/zxing-build/ZXingConfigVersion.cmake"
    )
endif()

