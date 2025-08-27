// NativeDecodeBridge.h
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

// Returns malloc'ed C string (UTF-8). Caller must free via free_decoded_string.
const char* callNativeDecode(const unsigned char* bytes,
                             int width,
                             int height,
                             int rowStride);

void free_decoded_string(const char* str);

#ifdef __cplusplus
}
#endif
