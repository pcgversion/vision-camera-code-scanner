/*#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>

@interface VISION_EXPORT_SWIFT_FRAME_PROCESSOR(scanCodes, VisionCameraCodeScanner)
@end
*/
#import "NativeDecodeBridge.h"
#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>
#import "vision_camera_code_scanner-Swift.h"

#include "ReadBarcode.h"
#include "ImageView.h"
#include "TextUtfEncoding.h" // For ZXing::ToString
/*#include "zxing-cpp/Wrapper/ZXIResult.h"    // For ZXing::Result
#include "zxing-cpp/Core/BarcodeFormat.h" // For ZXing::BarcodeFormat
#include "zxing-cpp/Core/ImageFormat.h" // For ZXing::ImageFormat
*/

@interface VISION_EXPORT_SWIFT_FRAME_PROCESSOR(scanCodes, VisionCameraCodeScanner)
@end

// Define the C-style function that Swift will call
extern "C" const char* callNativeDecode(const unsigned char* bytes, int width, int height, int rowStride) {
    if (bytes == nullptr || width <= 0 || height <= 0) {
        return strdup(""); // Return empty, caller might need to free
    }

    // Note: ZXing::ImageView typically expects rowStride if format isn't Lum.
    // For Lum (grayscale), width is often the stride if tightly packed.
    // Ensure your `bytes` data matches this expectation.
    //ZXing::ImageView img(bytes, width, height, ZXing::ImageFormat::Lum);
                        // Potentially add rowStride if needed:
                         ZXing::ImageView img(bytes, width, height, ZXing::ImageFormat::Lum, rowStride);


    ZXing::Result result = ZXing::ReadBarcode(img);

    if (!result.isValid()) {
        return strdup(""); // Return empty, caller might need to free
    }

    // Convert result to a JSON C string
    // Using std::string for easier manipulation then converting to const char*
    std::string json_str = "{";
    json_str += "\"text\":\"" + result.text() + "\",";
    json_str += "\"format\":\"" + ZXing::ToString(result.format()) + "\",";
    
    json_str += "\"points\":[";
    auto points = result.position();
    for (size_t i = 0; i < points.size(); ++i) {
        const auto& pt = points[i];
        json_str += "{\"x\":" + std::to_string(pt.x) + ",\"y\":" + std::to_string(pt.y) + "}";
        if (i != points.size() - 1) {
            json_str += ",";
        }
    }
    json_str += "]}";

    // IMPORTANT: Swift needs to take ownership or copy this string.
    // strdup allocates memory that Swift (or C code) must free later.
    // Provide a corresponding free_decoded_string function.
    return strdup(json_str.c_str());
}

// Add a function for Swift to free the returned string
extern "C" void free_decoded_string(const char* str) {
    if (str) {
        free((void*)str);
    }
}



