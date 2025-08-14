cmake_minimum_required(VERSION 3.10)

# Add the ZXing core folder as a CMake subproject
add_subdirectory(zxing-cpp/core zxing-build)

# Your native lib
add_library(barcode-decoder SHARED
    BarcodeDecoder.cpp
)

# Include path to core/src so ReadBarcode.h can be found
target_include_directories(barcode-decoder PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/zxing-cpp/core/src
)

# Link the ZXing core lib (target is probably ZXing or zxing)
target_link_libraries(barcode-decoder
    ZXing     # This is the CMake target exported by core/CMakeLists.txt
    log
)
