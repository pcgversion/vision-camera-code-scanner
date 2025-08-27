#import <Foundation/Foundation.h>

@interface MyDecoder : NSObject
    - (NSString *)nativeDecode:(NSData *)imageData width:(int)width height:(int)height;
@end
