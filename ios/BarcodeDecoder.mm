#import <Foundation/Foundation.h>
#import "BarcodeDecoder.h"
#include "ReadBarcode.h"
#include "ImageView.h"
#include "TextUtfEncoding.h"


NSString* nativeDecode(NSData* imageData, int width, int height) {
    ZXing::ImageView img(
        reinterpret_cast<const uint8_t*>(imageData.bytes),
        width,
        height,
        ZXing::ImageFormat::Lum
    );

    auto result = ZXing::ReadBarcode(img);

    if (!result.isValid())
        return @"";

    NSMutableString *json = [NSMutableString stringWithString:@"{"];
    [json appendFormat:@"\"text\":\"%s\",", result.text().c_str()];
    [json appendFormat:@"\"format\":\"%s\",", ZXing::ToString(result.format()).c_str()];

    [json appendString:@"\"points\":["];
    auto points = result.position();
    for (size_t i = 0; i < points.size(); ++i) {
        const auto& pt = points[i];
        [json appendFormat:@"{\"x\":%f,\"y\":%f}", pt.x, pt.y];
        if (i != points.size() - 1) {
            [json appendString:@","];
        }
    }
    [json appendString:@"]}"];

    return json;
}
