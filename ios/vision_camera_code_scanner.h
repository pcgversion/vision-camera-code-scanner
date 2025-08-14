#ifndef vision_camera_code_scanner_h
#define vision_camera_code_scanner_h

// Declare the C functions that Swift can call
const char* callNativeDecode(const unsigned char* bytes, int width, int height, int rowStride);
void free_decoded_string(const char* str);

#endif /* YourProjectName_Bridging_Header_h */
