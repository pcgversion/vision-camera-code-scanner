/*#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>

@interface VISION_EXPORT_SWIFT_FRAME_PROCESSOR(scanCodes, VisionCameraCodeScanner)
@end
*/
#import <Foundation/Foundation.h>
#import <VisionCamera/FrameProcessorPlugin.h>
#import <VisionCamera/FrameProcessorPluginRegistry.h>
#import <VisionCamera/Frame.h>
#import "vision_camera_code_scanner-Swift.h"
// @interface VisionCameraCodeScanner : FrameProcessorPlugin
// @end
// VISION_EXPORT_SWIFT_FRAME_PROCESSOR(VisionCameraCodeScanner, scanCodes)

//#import "vision_camera_ocr-Swift.h"

@interface VisionCameraCodeScanner (FrameProcessorPluginLoader)
@end

@implementation VisionCameraCodeScanner (FrameProcessorPluginLoader)

+ (void)load
{
    [FrameProcessorPluginRegistry addFrameProcessorPlugin:@"scanBarcodes"
                                        withInitializer:^FrameProcessorPlugin* (VisionCameraProxyHolder* proxy, NSDictionary* options) {
        return [[VisionCameraCodeScanner alloc] initWithProxy:proxy withOptions:options];
    }];
}

@end
