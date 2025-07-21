import CoreImage
import TensorFlowLite
import UIKit
import Accelerate
import MLImage

class BarcodeDetectorHelper: NSObject {

    private var interpreter: Interpreter?
    private let threshold: Float
    private let modelImageSize: Int
    private let labels = ["barcode", "unknown"]

    private let inputChannels = 3
    private let batchSize = 1

    init?(modelPath: String, modelName: String, threadCount: Int = 2, scoreThreshold: Float, maxResults: Int = 3, mImageSize: Int = 1024) {
        self.threshold = scoreThreshold
        self.modelImageSize = mImageSize

        super.init()

        do {
            var options = Interpreter.Options()
            options.threadCount = threadCount

            // Use GPU (Metal) delegate
            //let delegate: MetalDelegate? = MetalDelegate()
            //if let delegate = delegate {
            //    interpreter = try Interpreter(modelPath: modelPath, options: options, delegates: [delegate])
            //} else {
            interpreter = try Interpreter(modelPath: modelPath, options: options)
            //}
            try interpreter?.allocateTensors()
        } catch {
            print("Interpreter setup failed: \(error)")
            return nil
        }
    }

    func detect(pixelBuffer: CVPixelBuffer) -> [[String: Any]]? {
        let imageWidth = CVPixelBufferGetWidth(pixelBuffer)
        let imageHeight = CVPixelBufferGetHeight(pixelBuffer)
        let imageSize = CGSize(width: imageWidth, height: imageHeight)

        let scaledBuffer: CVPixelBuffer
        let targetSize = CGSize(width: modelImageSize, height: modelImageSize)

        if imageWidth != modelImageSize || imageHeight != modelImageSize {
            guard let resized = pixelBuffer.resized(to: targetSize) else { return nil }
            scaledBuffer = resized
        } else {
            scaledBuffer = pixelBuffer
        }

        do {
            let inputTensor = try interpreter?.input(at: 0)

            guard let rgbData = rgbDataFromBuffer(
                scaledBuffer,
                byteCount: batchSize * modelImageSize * modelImageSize * inputChannels,
                isModelQuantized: inputTensor?.dataType == .uInt8
            ) else {
                return nil
            }

            try interpreter?.copy(rgbData, toInputAt: 0)
            try interpreter?.invoke()

            guard let outputTensor = try interpreter?.output(at: 0) else {
                return nil
            }

            let output = outputTensor.data.toArray(type: Float32.self)
            let shape = outputTensor.shape.dimensions
            return parseDetections(output: output, shape: shape, originalSize: imageSize)

        } catch {
            print("Detection failed: \(error)")
            return nil
        }
    }

    private func parseDetections(output: [Float], shape: [Int], originalSize: CGSize) -> [[String: Any]] {
        let count = shape[1]
        var results: [[String: Any]] = []

        for i in 0..<count {
            let base = i * 7
            let conf = output[base + 4]
            let classId = Int(output[base + 5])
            let angle = output[base + 6]
            if conf >= self.threshold {
                let cx = output[base + 0] * Float(modelImageSize)
                let cy = output[base + 1] * Float(modelImageSize)
                let w = output[base + 2] * Float(modelImageSize)
                let h = output[base + 3] * Float(modelImageSize)

                let corners = rotatedBoxCorners(cx: cx, cy: cy, w: w, h: h, angleRad: angle)
                let mapped = corners.map { mapToOriginal($0, originalSize: originalSize) }
                
                results.append([
                    "x1": mapped[0].x, "y1": mapped[0].y,
                    "x2": mapped[1].x, "y2": mapped[1].y,
                    "x3": mapped[2].x, "y3": mapped[2].y,
                    "x4": mapped[3].x, "y4": mapped[3].y,
                    "confidence": conf,
                    "classId": classId < labels.count ? labels[classId] : "unknown",
                    "angle": angle * 180 / .pi
                ])
            }
        }
        return results
    }

    private func rotatedBoxCorners(cx: Float, cy: Float, w: Float, h: Float, angleRad: Float) -> [CGPoint] {
        let cosA = cos(angleRad)
        let sinA = sin(angleRad)
        let hw = w / 2
        let hh = h / 2

        let relCorners = [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]

        return relCorners.map { dx, dy in
            let x = dx * cosA - dy * sinA + cx
            let y = dx * sinA + dy * cosA + cy
            return CGPoint(x: CGFloat(x), y: CGFloat(y))
        }
    }

    private func mapToOriginal(_ pt: CGPoint, originalSize: CGSize) -> CGPoint {
        let scaleX = originalSize.width / CGFloat(modelImageSize)
        let scaleY = originalSize.height / CGFloat(modelImageSize)
        return CGPoint(x: pt.x * scaleX, y: pt.y * scaleY)
    }

    private func rgbDataFromBuffer(_ buffer: CVPixelBuffer, byteCount: Int, isModelQuantized: Bool) -> Data? {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }

        guard let sourceData = CVPixelBufferGetBaseAddress(buffer) else { return nil }

        let width = CVPixelBufferGetWidth(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        let rowBytes = CVPixelBufferGetBytesPerRow(buffer)

        var srcBuffer = vImage_Buffer(data: sourceData, height: vImagePixelCount(height),
                                      width: vImagePixelCount(width), rowBytes: rowBytes)

        let rgbRowBytes = width * 3
        guard let rgbBytes = malloc(height * rgbRowBytes) else { return nil }
        defer { free(rgbBytes) }

        var dstBuffer = vImage_Buffer(data: rgbBytes, height: vImagePixelCount(height),
                                      width: vImagePixelCount(width), rowBytes: rgbRowBytes)

        let format = CVPixelBufferGetPixelFormatType(buffer)
        if format == kCVPixelFormatType_32BGRA {
            vImageConvert_BGRA8888toRGB888(&srcBuffer, &dstBuffer, 0)
        } else if format == kCVPixelFormatType_32ARGB {
            vImageConvert_ARGB8888toRGB888(&srcBuffer, &dstBuffer, 0)
        }

        let byteData = Data(bytes: rgbBytes, count: height * rgbRowBytes)

        if isModelQuantized {
            return byteData
        }

        let bytes = [UInt8](unsafeData: byteData) ?? []
        var floats = [Float](repeating: 0, count: bytes.count)
        vDSP_vfltu8(bytes, 1, &floats, 1, vDSP_Length(bytes.count))
        var scale: Float = 1.0 / 255.0
        vDSP_vsmul(floats, 1, &scale, &floats, 1, vDSP_Length(floats.count))

        return Data(copyingBufferOf: floats)
    }
}




extension CVPixelBuffer {
    /// Returns thumbnail by cropping pixel buffer to biggest square and scaling the cropped image
    /// to model dimensions.
    func resized(to size: CGSize ) -> CVPixelBuffer? {
        
        let imageWidth = CVPixelBufferGetWidth(self)
        let imageHeight = CVPixelBufferGetHeight(self)
        
        let pixelBufferType = CVPixelBufferGetPixelFormatType(self)
        //print("CVPixelBuffer Function.......:", pixelBufferType, kCVPixelFormatType_32BGRA)
        //assert(pixelBufferType == kCVPixelFormatType_32BGRA)
        
        let inputImageRowBytes = CVPixelBufferGetBytesPerRow(self)
        let imageChannels = 4
        
        CVPixelBufferLockBaseAddress(self, CVPixelBufferLockFlags(rawValue: 0))
        
        // Finds the biggest square in the pixel buffer and advances rows based on it.
        guard let inputBaseAddress = CVPixelBufferGetBaseAddress(self) else {
            return nil
        }
        
        // Gets vImage Buffer from input image
        var inputVImageBuffer = vImage_Buffer(data: inputBaseAddress, height: UInt(imageHeight), width: UInt(imageWidth), rowBytes: inputImageRowBytes)
        
        let scaledImageRowBytes = Int(size.width) * imageChannels
        guard  let scaledImageBytes = malloc(Int(size.height) * scaledImageRowBytes) else {
            return nil
        }
        
        // Allocates a vImage buffer for scaled image.
        var scaledVImageBuffer = vImage_Buffer(data: scaledImageBytes, height: UInt(size.height), width: UInt(size.width), rowBytes: scaledImageRowBytes)
        
        // Performs the scale operation on input image buffer and stores it in scaled image buffer.
        let scaleError = vImageScale_ARGB8888(&inputVImageBuffer, &scaledVImageBuffer, nil, vImage_Flags(0))
        
        CVPixelBufferUnlockBaseAddress(self, CVPixelBufferLockFlags(rawValue: 0))
        
        guard scaleError == kvImageNoError else {
            return nil
        }
        
        let releaseCallBack: CVPixelBufferReleaseBytesCallback = {mutablePointer, pointer in
            
            if let pointer = pointer {
                free(UnsafeMutableRawPointer(mutating: pointer))
            }
        }
        
        var scaledPixelBuffer: CVPixelBuffer?
        
        // Converts the scaled vImage buffer to CVPixelBuffer
        let conversionStatus = CVPixelBufferCreateWithBytes(nil, Int(size.width), Int(size.height), pixelBufferType, scaledImageBytes, scaledImageRowBytes, releaseCallBack, nil, nil, &scaledPixelBuffer)
        
        guard conversionStatus == kCVReturnSuccess else {
            
            free(scaledImageBytes)
            return nil
        }
        
        return scaledPixelBuffer
    }
    
}


// MARK: - Extensions
extension Data {
    init<T>(copyingBufferOf array: [T]) {
        self = array.withUnsafeBufferPointer(Data.init)
    }

    func toArray<T>(type: T.Type) -> [T] {
        let count = self.count / MemoryLayout<T>.stride
        return self.withUnsafeBytes {
            Array(UnsafeBufferPointer<T>(start: $0.baseAddress!.assumingMemoryBound(to: T.self), count: count))
        }
    }
}

extension Array {
    init?(unsafeData: Data) {
        guard unsafeData.count % MemoryLayout<Element>.stride == 0 else { return nil }
        self = unsafeData.withUnsafeBytes {
            .init($0.bindMemory(to: Element.self))
        }
    }
}
