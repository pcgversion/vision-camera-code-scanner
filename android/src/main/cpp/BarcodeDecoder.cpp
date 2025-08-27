#include <jni.h>
#include <string>
#include <sstream>
#include "ReadBarcode.h"
#include "ImageView.h"
#include "TextUtfEncoding.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_visioncameracodescanner_BarcodeDecoder_nativeDecode(JNIEnv *env, jobject, jbyteArray byteArray, jint width, jint height) {
    jbyte *data = env->GetByteArrayElements(byteArray, nullptr);

    ZXing::ImageView img(
        reinterpret_cast<const uint8_t*>(data),
        width,
        height,
        ZXing::ImageFormat::Lum
    );

    auto result = ZXing::ReadBarcode(img);
    env->ReleaseByteArrayElements(byteArray, data, JNI_ABORT);

    if (!result.isValid())
        return env->NewStringUTF("");

    std::ostringstream json;
    json << "{";
    json << "\"text\":\"" << result.text() << "\",";
    json << "\"format\":\"" << ZXing::ToString(result.format()) << "\",";

    json << "\"points\":[";
    auto points = result.position();
    for (size_t i = 0; i < points.size(); ++i) {
        const auto& pt = points[i];
        json << "{\"x\":" << pt.x << ",\"y\":" << pt.y << "}";
        if (i != points.size() - 1) {
            json << ",";
        }
    }
    json << "]}";

    std::string jsonStr = json.str();
    return env->NewStringUTF(jsonStr.c_str());
}
