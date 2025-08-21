import VisionCamera
import MLKitBarcodeScanning
import MLKitVision

import Vision
import UIKit
import CoreML
import AVFoundation
import Foundation
import CoreMedia
import CoreVideo
import CoreImage
import ImageIO
import CoreML

import MLKitObjectDetectionCustom
import MLKitObjectDetection
import MLKitVision
import MLKitCommon
import MLImage
import ImageIO
import MobileCoreServices
import ZXingCpp


@objc(VisionCameraCodeScanner)
public class VisionCameraCodeScanner: FrameProcessorPlugin {
    
    static var barcodeScanner: BarcodeScanner? = nil
    static var barcodeFormatOptionSet: BarcodeFormat = []
    private var isProcessingFallback: Bool? = false
    private var zxingBarcodeReader: ZXIBarcodeReader? = nil
    private var zxingBarcodeReaderOptions = ZXIReaderOptions()
    
    public override init(proxy: VisionCameraProxyHolder, options: [AnyHashable: Any]! = [:]) {
        super.init(proxy: proxy, options: options)
        //print("VisionCameraCodeScanner initialized with options: \(String(describing: options))")
        zxingBarcodeReaderOptions.tryRotate = true
        zxingBarcodeReaderOptions.tryHarder = true
        zxingBarcodeReaderOptions.tryInvert = true
        zxingBarcodeReaderOptions.tryDownscale = true
        self.zxingBarcodeReader = ZXIBarcodeReader(options: zxingBarcodeReaderOptions)
    }
    
    public override func callback(_ frame: Frame, withArguments args: [AnyHashable : Any]?) -> Any {
        
        // let image = VisionImage(buffer: frame.buffer)
        // image.orientation = .up
         guard let imageBuffer = CMSampleBufferGetImageBuffer(frame.buffer) else {
          print("Failed to get image buffer from sample buffer.")
             return [:]
        }

        var ciImage = CIImage(cvPixelBuffer: imageBuffer)
        let curDeviceOrientation = UIDevice.current.orientation
        let isLandscape = isDeviceInLandscapeWhenFaceUp()
        //print("current Device Orientation: \(curDeviceOrientation) \(isLandscape)")
        switch curDeviceOrientation {
            case UIDeviceOrientation.portraitUpsideDown:  // Device oriented vertically, Home button on the top
                ciImage = ciImage.oriented(forExifOrientation: 8)
            case UIDeviceOrientation.landscapeLeft:       // Device oriented horizontally, Home button on the right
                ciImage = ciImage.oriented(forExifOrientation: 1)
            case UIDeviceOrientation.landscapeRight:      // Device oriented horizontally, Home button on the left
                ciImage = ciImage.oriented(forExifOrientation: 3)
            case UIDeviceOrientation.portrait:            // Device oriented vertically, Home button on the bottom
                ciImage = ciImage.oriented(forExifOrientation: 6)
            case UIDeviceOrientation.faceUp:
            ciImage = ciImage.oriented(forExifOrientation: isLandscape ? isDeviceInLandscapeWhenFaceUpLeft() ? 3 : 1 : 6)
            case UIDeviceOrientation.faceDown:
                ciImage = ciImage.oriented(forExifOrientation: isLandscape ? 1 : 6)
            case UIDeviceOrientation.unknown:
                ciImage = ciImage.oriented(forExifOrientation: 1)
            default:
                ciImage = ciImage.oriented(forExifOrientation: 1)
        }
        guard let cgImage = CIContext().createCGImage(ciImage, from: ciImage.extent) else {
            print("Failed to create bitmap from image.")
            return [:]
        }
       
        let image = UIImage(cgImage: cgImage)
//         print("------VisionCameraCodeScanner--------")
//         detectBarcodes(in: image) { results in
//         for result in results {
//             print("Detected Barcode:")
//             print("Raw Value: \(result.rawValue)")
//             print("Format: \(result.format)")
//             print("Bounding Box: \(result.boundingBox)")
//             }
//         }
//         print("------END VisionCameraCodeScanner--------")
        let visionImage = VisionImage(image: image)
        visionImage.orientation = image.imageOrientation
       
        var barCodeAttributes: [Any] = []
        var barcodeType : [Any] = []
        barcodeType = [args?["types"] as Any]
       
        var modelName: String = ""
        var detectorMode: Int? = 1
        var shouldEnableClassification: Bool = false
        var shouldEnableMultipleObjects: Bool = true
        var enablePluMarkersDetection: Bool = false
        var modelImageSize: Int? = 1024
        var detectMarkerOnly: Bool = true
        //var threshold: Float? = 0.5
        
        
        guard let options = args?["options"] as? [String: Any] else { return [:]}
        modelName = options["modelName"] as? String ?? ""
        modelImageSize = options["modelImageSize"] as? Int ?? 1024
        detectorMode = options["detectorMode"] as? Int ?? 1
        shouldEnableClassification = options["shouldEnableClassification"] as? Bool ?? false
        enablePluMarkersDetection = options["enablePluMarkersDetection"] as? Bool ?? false
        shouldEnableMultipleObjects = options["shouldEnableMultipleObjects"] as? Bool ?? true
        var threshold: Double = {
            if let value = options["threshold"] as? Double {
                return value
            } else if let value = options["threshold"] as? NSNumber {
                return value.doubleValue
            } else if let value = options["threshold"] as? String, let doubleValue = Double(value) {
                return doubleValue
            }
            return 0.5
        }()
        detectMarkerOnly = options["detectMarkerOnly"] as? Bool ?? true
        // Get the document directory path
        var absoluteModelPath: String = "";
        if let documentDirectory = getDocumentsDirectory(){
            //NSLog("@document directory path...%@", documentDirectory.path);
            absoluteModelPath = documentDirectory.path + "/custom_models/" + modelName + ".tflite";
        }
        
        do {
            try Self.createScanner(barcodeType)
            //print("Barcode Scanner: \(String(describing: Self.barcodeScanner))")
            var barcodes: [Barcode] = []
            barcodes.append(contentsOf: try Self.barcodeScanner!.results(in: visionImage))
            
            if let options = args?["options"] as? [String: Any] {
                let checkInverted = options["checkInverted"] as? Bool ?? false
                if (checkInverted) {
                    guard let buffer = CMSampleBufferGetImageBuffer(frame.buffer) else {
                        return [:]
                    }
                    ciImage = CIImage(cvPixelBuffer: buffer)
                    guard let invertedImage = Self.invert(src: ciImage) else {
                        return [:]
                    }
                    barcodes.append(contentsOf: try Self.barcodeScanner!.results(in: VisionImage.init(image: invertedImage)))
                }
            }
            if (!barcodes.isEmpty){
                for barcode in barcodes {
                    barCodeAttributes.append(Self.convertBarcode(barcode: barcode))
                }
            }
            if(enablePluMarkersDetection){
            let BarcodeDetectorHelper = BarcodeDetectorHelper(modelPath: absoluteModelPath, modelName: modelName, scoreThreshold: Float(threshold) , maxResults: 3, mImageSize: modelImageSize!)
            guard let visionImage = uiImageToCVPixelBuffer(image: image) else {return []}
            // Preprocess the image and prepare input tensor
            do {
                guard let pluCodeMarkers = try BarcodeDetectorHelper?.detect(pixelBuffer: visionImage) as? [[String: Any]] else {
                    print("pluCodeMarkers is nil or in unexpected format")
                    return []
                }
                var outputObjectIndex = 1
                
                if !pluCodeMarkers.isEmpty && !isProcessingFallback! {
                    isProcessingFallback = true
                    var tempResults = Set<String>()
                    var outputObjectIndex = 1
                    let zxingReader = self.zxingBarcodeReader!
                    for detection in pluCodeMarkers {
                        guard
                            let x1 = (detection["x1"] as? NSNumber)?.floatValue,
                            let y1 = (detection["y1"] as? NSNumber)?.floatValue,
                            let x2 = (detection["x2"] as? NSNumber)?.floatValue,
                            let y2 = (detection["y2"] as? NSNumber)?.floatValue,
                            let x3 = (detection["x3"] as? NSNumber)?.floatValue,
                            let y3 = (detection["y3"] as? NSNumber)?.floatValue,
                            let x4 = (detection["x4"] as? NSNumber)?.floatValue,
                            let y4 = (detection["y4"] as? NSNumber)?.floatValue,
                            let confidence = (detection["confidence"] as? NSNumber)?.floatValue,
                            let angle = (detection["angle"] as? NSNumber)?.floatValue
                        else { continue }

                        let cornerPoints = [
                            CGPoint(x: CGFloat(x1), y: CGFloat(y1)),
                            CGPoint(x: CGFloat(x2), y: CGFloat(y2)),
                            CGPoint(x: CGFloat(x3), y: CGFloat(y3)),
                            CGPoint(x: CGFloat(x4), y: CGFloat(y4))
                        ]

                        let boundingBox = CGRect(
                            x: CGFloat(min(x1, x2, x3, x4)),
                            y: CGFloat(min(y1, y2, y3, y4)),
                            width: CGFloat(max(x1, x2, x3, x4) - min(x1, x2, x3, x4)),
                            height: CGFloat(max(y1, y2, y3, y4) - min(y1, y2, y3, y4))
                        )
                        

                        if !detectMarkerOnly {
                            for k in 0..<2 {
                                let rotationAngle: CGFloat = (k == 0) ? -CGFloat(angle) : (70 - CGFloat(angle))
                                let cropRect = boundingBox.integral

                                guard let croppedCGImage = image.cgImage?.cropping(to: cropRect) else {
                                    print("Failed to crop CGImage")
                                    continue
                                }

                                let croppedUIImage = UIImage(cgImage: croppedCGImage)
                                    let resizedCropped = croppedUIImage.resized(to: CGSize(width: 300, height: 300))
                                let rotatedCropped = rotate(bitmap: resizedCropped, byDegrees: rotationAngle, imgIndex: k)

                                guard let ciRotated = CIImage(image: rotatedCropped) else {
                                    print("Failed to convert rotated image to CIImage")
                                    continue
                                }

                                
//                                guard let rotatedCIImage = CIImage(image: rotate(bitmap: cropped, byDegrees: rotationAngle, imgIndex: k)) else {
//                                    continue
//                                }
                                // ...Plu Barcode reading using js function...
                                if let (grayscaleBytes, width, height) = ciImageToGrayscaleBytes(ciRotated) {
                                    let grayscaleData = Data(grayscaleBytes) // Your [UInt8] to Data conversion

                                    let resultCharPtr: UnsafePointer<CChar>? = grayscaleData.withUnsafeBytes { (rawBufferPointer: UnsafeRawBufferPointer) -> UnsafePointer<CChar>? in
                                        guard let baseAddress = rawBufferPointer.baseAddress else {
                                            print("Error: Could not get base address of grayscale data.")
                                            return nil
                                        }
                                        let rowStride = Int32(width) // Assuming tightly packed grayscale data
                                        
                                        // Call the new extern "C" function
                                        return callNativeDecode(baseAddress.assumingMemoryBound(to: UInt8.self),
                                                                Int32(width),
                                                                Int32(height),
                                                                rowStride)
                                    }

                                    if let validCharPtr = resultCharPtr {
                                        let resultJsonString = String(cString: validCharPtr)
                                        // Now you must free the memory allocated by strdup in C++
                                        free_decoded_string(validCharPtr)

                                        if !resultJsonString.isEmpty {
                                            print("ZXing JSON result: \(resultJsonString)")
                                            // Here, parse the resultJsonString if needed or directly use it
                                            // For example, convert JSON string to a Dictionary
                                            if let jsonData = resultJsonString.data(using: .utf8) {
                                                do {
                                                    if let jsonDict = try JSONSerialization.jsonObject(with: jsonData, options: []) as? [String: Any] {
                                                        // Create your resultMap for barCodeAttributes
                                                        // This part needs careful mapping from your C++ JSON to the Swift dictionary structure
                                                        var resultMap: [String: Any] = [:]
                                                        resultMap["rawValue"] = jsonDict["text"]
                                                        resultMap["displayValue"] = jsonDict["text"] // Or process as needed
                                                        
                                                        // Map format string to your integer representation if necessary
                                                        if let formatString = jsonDict["format"] as? String {
                                                            // Example: you'd need a mapping from "QRCode" -> your Int format
                                                            // For now, just using a placeholder or the string itself
                                                            resultMap["format"] = mapZxingFormatStringToInteger(formatString) // Implement this mapping
                                                        }
                                                        
                                                         resultMap["angle"] = Int(angle) // You still need the angle from your pluCodeMarkers detection
                                                        
                                                        if let pointsArray = jsonDict["points"] as? [[String: Double]] {
                                                            let cgPoints = pointsArray.map { CGPoint(x: $0["x"] ?? 0, y: $0["y"] ?? 0) }
                                                            let zxBoundingBox = calculateBoundingBox(from: cgPoints) // Your existing Swift func
                                                             resultMap["boundingBox"] = [
                                                                 "left": Int(zxBoundingBox.minX),
                                                                 "top": Int(zxBoundingBox.minY),
                                                                 "right": Int(zxBoundingBox.maxX),
                                                                 "bottom": Int(zxBoundingBox.maxY)
                                                             ]
                                                            resultMap["cornerPoints"] = pointsArray.map { ["x": Int($0["x"] ?? 0), "y": Int($0["y"] ?? 0)] }
                                                        }
                                                        
                                                        resultMap["content"] = ["type": 5, "data": jsonDict["text"]] // Adjust 'type' as needed
                                                        
                                                        barCodeAttributes.append(resultMap) // Add to your results
                                                        print("Processed resultMap: \(resultMap)")
                                                    }
                                                } catch {
                                                    print("Error deserializing JSON from nativeDecode: \(error)")
                                                }
                                            }
                                        } else {
                                            print("nativeDecode returned an empty string after freeing.")
                                        }
                                    } else {
                                        print("callNativeDecode returned null.")
                                    }
                                }
                                // ...End of Plu Barcode reading using js function...
                                
                                /*do {
                                    let results = try zxingReader.read(ciRotated)
                                    for result in results where !tempResults.contains(result.text) {
                                        tempResults.insert(result.text)
                                        let zxPoints = [result.position.topLeft, result.position.topRight, result.position.bottomRight, result.position.bottomLeft]
                                        let cgPoints = zxPoints.map { CGPoint(x: $0.x, y: $0.y) }
                                        let zxBoundingBox = calculateBoundingBox(from: cgPoints)

                                        let resultMap: [String: Any] = [
                                            "rawValue": result.text,
                                            "displayValue": result.text,
                                            "format": 2048,
                                            "angle": Int(angle),
                                            "boundingBox": [
                                                "left": Int(zxBoundingBox.minX),
                                                "top": Int(zxBoundingBox.minY),
                                                "right": Int(zxBoundingBox.maxX),
                                                "bottom": Int(zxBoundingBox.maxY)
                                            ],
                                            "cornerPoints": zxPoints.map { ["x": Int($0.x), "y": Int($0.y)] },
                                            "content": ["type": 5, "data": result.text]
                                        ]

                                        barCodeAttributes.append(resultMap)
                                    }
                                } catch {
                                    print("ZXing decoding failed: \(error)")
                                    continue
                                }*/
                            }
                        } else {
                            let fakeText = "123456_\(outputObjectIndex)"
                            if !tempResults.contains(fakeText) {
                                tempResults.insert(fakeText)
                                barCodeAttributes.append([
                                    "rawValue": fakeText,
                                    "displayValue": fakeText,
                                        "format": "RSS_14",
                                    "angle": Int(angle),
                                    "boundingBox": [
                                        "left": Int(boundingBox.minX),
                                        "top": Int(boundingBox.minY),
                                        "right": Int(boundingBox.maxX),
                                        "bottom": Int(boundingBox.maxY)
                                    ],
                                    "cornerPoints": cornerPoints.map { ["x": Int($0.x), "y": Int($0.y)] },
                                    "content": ["type": 5, "data": fakeText]
                                ])
                            }
                        }

                        outputObjectIndex += 1
                    }
                    isProcessingFallback = false
                }

                //return outputData
            } catch let error as NSError{
                let errorString = error.localizedDescription
                let pData: [String: Any] = ["error": "On-Device object detection failed with error: \(errorString)"]
                return pData;
                }
            }
            
        } catch _ {
            return [:]
        }
        return barCodeAttributes
    }
    
    // Helper function to map ZXing format string to your app's integer codes
    func mapZxingFormatStringToInteger(_ formatString: String) -> String {
        // This is an example, expand with all formats you expect from ZXing
        switch formatString {
        case "DataBar": return "RSS_14"
        // ... add other ZXing::BarcodeFormat string representations
        default: return formatString // Unknown or unmapped
        }
    }
    func ciImageToGrayscaleBytes(_ ciImage: CIImage) -> (bytes: [UInt8], width: Int, height: Int)? {
        let context = CIContext()
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { return nil }
        let width = cgImage.width
        let height = cgImage.height

        let colorSpace = CGColorSpaceCreateDeviceGray()
        var grayscaleBytes = [UInt8](repeating: 0, count: width * height)
        guard let bitmapContext = CGContext(
            data: &grayscaleBytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: 0
        ) else { return nil }

        bitmapContext.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        return (grayscaleBytes, width, height)
    }
    func calculateBoundingBox(from points: [CGPoint]) -> (minX: CGFloat, minY: CGFloat, maxX: CGFloat, maxY: CGFloat) {
        var minX = CGFloat.greatestFiniteMagnitude
        var minY = CGFloat.greatestFiniteMagnitude
        var maxX = CGFloat.leastNormalMagnitude
        var maxY = CGFloat.leastNormalMagnitude
        
        for pt in points {
            minX = min(minX, pt.x)
            minY = min(minY, pt.y)
            maxX = max(maxX, pt.x)
            maxY = max(maxY, pt.y)
        }
        
        return (minX, minY, maxX, maxY)
    }

    func saveImageToDocuments(_ image: UIImage, fileName: String = "rotated.jpg") -> URL? {
        guard let data = image.jpegData(compressionQuality: 0.9) else {
            print("Failed to convert image to JPEG")
            return nil
        }

        do {
            let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let fileURL = documentsURL.appendingPathComponent(fileName)

            try data.write(to: fileURL)
            print("Saved rotated image at: \(fileURL)")
            return fileURL
        } catch {
            print("Error saving image: \(error)")
            return nil
        }
    }
    func rotate(bitmap: UIImage, byDegrees degrees: CGFloat, imgIndex: Int) -> UIImage {
        let radians = degrees * .pi / 180
        var newSize = CGRect(origin: .zero, size: bitmap.size)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral.size
        
        // Ensure size is even to prevent CGImage errors
        if Int(newSize.width) % 2 != 0 { newSize.width += 1 }
        if Int(newSize.height) % 2 != 0 { newSize.height += 1 }

        UIGraphicsBeginImageContextWithOptions(newSize, false, bitmap.scale)
        guard let context = UIGraphicsGetCurrentContext() else {
            return bitmap
        }

        // Move origin to middle
        context.translateBy(x: newSize.width / 2, y: newSize.height / 2)
        // Rotate context
        context.rotate(by: radians)
        // Draw image at center
        bitmap.draw(in: CGRect(
            x: -bitmap.size.width / 2,
            y: -bitmap.size.height / 2,
            width: bitmap.size.width,
            height: bitmap.size.height)
        )

        let rotatedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

//        if let savedURL = saveImageToDocuments(rotatedImage!, fileName: "rotated-\(imgIndex).jpg") {
//            print("Image saved to: \(savedURL)")
//        }
        return rotatedImage ?? bitmap
    }

    static func createScanner(_ args: [Any]!) throws {
        guard let rawFormats = args[0] as? [Int] else {
            throw BarcodeError.noBarcodeFormatProvided
        }
        var formatOptionSet: BarcodeFormat = []
        rawFormats.forEach { rawFormat in
            if (rawFormat == 0) {
                // ALL is a special case, since the Android and iOS option raw values don't match
                formatOptionSet.insert(.all)
            } else {
                formatOptionSet.insert(BarcodeFormat(rawValue: rawFormat))
            }
        }
        if (barcodeScanner == nil || barcodeFormatOptionSet != formatOptionSet) {
            let barcodeOptions = BarcodeScannerOptions(formats: formatOptionSet)
            barcodeScanner = BarcodeScanner.barcodeScanner(options: barcodeOptions)
            barcodeFormatOptionSet = formatOptionSet
        }
    }
    
    static func convertContent(barcode: Barcode) -> Any {
        var map: [String: Any] = [:]
        
        map["type"] = barcode.valueType
        
        switch barcode.valueType {
        case .unknown, .ISBN, .text:
            map["data"] = barcode.rawValue
        case .contactInfo:
            map["data"] = BarcodeConverter.convertToMap(contactInfo: barcode.contactInfo)
        case .email:
            map["data"] = BarcodeConverter.convertToMap(email: barcode.email)
        case .phone:
            map["data"] = BarcodeConverter.convertToMap(phone: barcode.phone)
        case .SMS:
            map["data"] = BarcodeConverter.convertToMap(sms: barcode.sms)
        case .URL:
            map["data"] = BarcodeConverter.convertToMap(url: barcode.url)
        case .wiFi:
            map["data"] = BarcodeConverter.convertToMap(wifi: barcode.wifi)
        case .geographicCoordinates:
            map["data"] = BarcodeConverter.convertToMap(geoPoint: barcode.geoPoint)
        case .calendarEvent:
            map["data"] = BarcodeConverter.convertToMap(calendarEvent: barcode.calendarEvent)
        case .driversLicense:
            map["data"] = BarcodeConverter.convertToMap(driverLicense: barcode.driverLicense)
        default:
            map = [:]
        }
        
        return map
    }
    
    static func convertBarcode(barcode: Barcode) -> Any {
        var map: [String: Any] = [:]
        
        map["cornerPoints"] = BarcodeConverter.convertToArray(points: barcode.cornerPoints as? [CGPoint])
        map["displayValue"] = barcode.displayValue
        map["rawValue"] = barcode.rawValue
        map["content"] = self.convertContent(barcode: barcode)
        map["format"] = barcode.format.rawValue
        
        return map
    }
    
    // CIImage Inversion Filter https://stackoverflow.com/a/42987565
    static func invert(src: CIImage) -> UIImage? {
        guard let filter = CIFilter(name: "CIColorInvert") else { return nil }
        filter.setDefaults()
        filter.setValue(src, forKey: kCIInputImageKey)
        let context = CIContext(options: nil)
        guard let outputImage = filter.outputImage else { return nil }
        guard let outputImageCopy = context.createCGImage(outputImage, from: outputImage.extent) else { return nil }
        return UIImage(cgImage: outputImageCopy, scale: 1, orientation: .up)
    }
}


// Struct to simulate Google ML Kit barcode result
struct BarcodeResult {
    let rawValue: String
    let format: String
    let boundingBox: CGRect
}

func getDocumentsDirectory() -> URL? {
    let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
    return paths.first
}
    

// Function to detect barcodes using Vision framework
func detectBarcodes(in image: UIImage, completion: @escaping ([BarcodeResult]) -> Void) {
    guard let cgImage = image.cgImage else {
        completion([])
        return
    }
    //print("Detect barcodes function")
    // Step 1: Create a VNDetectBarcodesRequest
    let barcodeRequest = VNDetectBarcodesRequest { (request, error) in
        guard error == nil else {
            print("Barcode detection error: \(error?.localizedDescription ?? "Unknown error")")
            completion([])
            return
        }

        // Step 2: Process the barcode observations
        var results: [BarcodeResult] = []
        if let observations = request.results as? [VNBarcodeObservation] {
            for observation in observations {
                if let payload = observation.payloadStringValue {
                    let format = getBarcodeFormat(from: observation.symbology)
                    let result = BarcodeResult(rawValue: payload, format: format, boundingBox: observation.boundingBox)
                    results.append(result)
                }
            }
        }

        // Step 3: Pass results to completion handler
        completion(results)
    }

    // Step 4: Perform the request using a VNImageRequestHandler
    let requestHandler = VNImageRequestHandler(cgImage: cgImage, options: [:])
    do {
        try requestHandler.perform([barcodeRequest])
    } catch {
        print("Failed to perform barcode detection: \(error.localizedDescription)")
        completion([])
    }
}

// Helper function to map Vision symbology to a string format similar to Google ML Kit
func getBarcodeFormat(from symbology: VNBarcodeSymbology) -> String {
    switch symbology {
    case .ean8:
        return "EAN-8"
    case .ean13:
        return "EAN-13"
    case .upce:
        return "UPC-E"
    case .code39:
        return "Code 39"
    case .code39Checksum:
        return "Code 39 Mod 43"
    case .code93:
        return "Code 93"
    case .code128:
        return "Code 128"
    case .pdf417:
        return "PDF417"
    case .qr:
        return "QR Code"
    case .aztec:
        return "Aztec"
    case .itf14:
        return "ITF-14"
    case .i2of5Checksum:
        return "Interleaved 2 of 5"
    default:
        return "Unknown"
    }
}
func isDeviceInLandscapeWhenFaceUp() -> Bool {
    let orientation = UIDevice.current.orientation
    // If the device is face up, check the interface orientation
    if orientation == .faceUp {
        // Get the current interface orientation
        if #available(iOS 13.0, *), let interfaceOrientation = UIApplication.shared.windows.first?.windowScene?.interfaceOrientation as? UIInterfaceOrientation{
                return interfaceOrientation.isLandscape
        }
        // let interfaceOrientation = UIApplication.shared.windows.first?.windowScene?.interfaceOrientation
        // if let interfaceOrientation = interfaceOrientation {
        //     return interfaceOrientation.isLandscape
        // }
    }
    // Otherwise, check if the current device orientation is landscape
    return orientation == .landscapeLeft || orientation == .landscapeRight
}

func isDeviceInLandscapeWhenFaceUpLeft() -> Bool {
    let orientation = UIDevice.current.orientation
    
    // If the device is face up, check the interface orientation
    if orientation == .faceUp {
        // Get the current interface orientation
        let interfaceOrientation = UIApplication.shared.windows.first?.windowScene?.interfaceOrientation
        
        if let interfaceOrientation = interfaceOrientation {
            return interfaceOrientation == .landscapeLeft
        }
    }
    
    // Otherwise, check if the current device orientation is landscape
    return orientation == .landscapeLeft
}

func uiImageToCVPixelBuffer(image: UIImage) -> CVPixelBuffer? {
    guard let cgImage = image.cgImage else {
        print("Failed to get cgImage from UIImage")
        return nil
    }
    
    let frameSize = CGSize(width: cgImage.width, height: cgImage.height)
    
    var pixelBuffer: CVPixelBuffer?
    let options: [CFString: Any] = [
        kCVPixelBufferCGImageCompatibilityKey: kCFBooleanTrue!,
        kCVPixelBufferCGBitmapContextCompatibilityKey: kCFBooleanTrue!
    ]
    
    let status = CVPixelBufferCreate(kCFAllocatorDefault,
                                     Int(frameSize.width),
                                     Int(frameSize.height),
                                     kCVPixelFormatType_32ARGB,
                                     options as CFDictionary,
                                     &pixelBuffer)
    
    guard status == kCVReturnSuccess, let createdPixelBuffer = pixelBuffer else {
        print("Failed to create pixel buffer")
        return nil
    }
    
    CVPixelBufferLockBaseAddress(createdPixelBuffer, CVPixelBufferLockFlags(rawValue: 0))
    let pixelData = CVPixelBufferGetBaseAddress(createdPixelBuffer)
    
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(data: pixelData,
                            width: Int(frameSize.width),
                            height: Int(frameSize.height),
                            bitsPerComponent: 8,
                            bytesPerRow: CVPixelBufferGetBytesPerRow(createdPixelBuffer),
                            space: colorSpace,
                            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue)
    
    guard let context = context else {
        print("Failed to create CGContext")
        CVPixelBufferUnlockBaseAddress(createdPixelBuffer, CVPixelBufferLockFlags(rawValue: 0))
        return nil
    }
    
    context.draw(cgImage, in: CGRect(origin: .zero, size: frameSize))
    CVPixelBufferUnlockBaseAddress(createdPixelBuffer, CVPixelBufferLockFlags(rawValue: 0))
    
    return createdPixelBuffer
}

func pixelBufferToRGBData(pixelBuffer: CVPixelBuffer) -> Data? {
    // Lock the base address of the pixel buffer
    CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
    defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
    
    guard let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else {
        print("Unable to get base address from pixel buffer")
        return nil
    }
    
    let width = CVPixelBufferGetWidth(pixelBuffer)
    let height = CVPixelBufferGetHeight(pixelBuffer)
    let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
    let pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer)
    
    //        guard pixelFormat == kCVPixelFormatType_32BGRA else {
    //            print("Pixel format not supported")
    //            return nil
    //        }
    
    var rgbData = Data(count: width * height * 3)
    
    rgbData.withUnsafeMutableBytes { rgbPtr in
        guard let rgbBaseAddress = rgbPtr.baseAddress else { return }
        let buffer = baseAddress.assumingMemoryBound(to: UInt8.self)
        
        for y in 0..<height {
            for x in 0..<width {
                let pixelIndex = y * bytesPerRow + x * 4
                let rgbIndex = (y * width + x) * 3
                
                let blue = buffer[pixelIndex]
                let green = buffer[pixelIndex + 1]
                let red = buffer[pixelIndex + 2]
                
                rgbBaseAddress.storeBytes(of: red, toByteOffset: rgbIndex, as: UInt8.self)
                rgbBaseAddress.storeBytes(of: green, toByteOffset: rgbIndex + 1, as: UInt8.self)
                rgbBaseAddress.storeBytes(of: blue, toByteOffset: rgbIndex + 2, as: UInt8.self)
            }
        }
    }
    
    return rgbData
}
extension UIImage {
    func resized(to targetSize: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = self.scale
        format.opaque = false

        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { _ in
            self.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }
}
