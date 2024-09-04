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

@objc(VisionCameraCodeScanner)
class VisionCameraCodeScanner: NSObject, FrameProcessorPluginBase {
    
    static var barcodeScanner: BarcodeScanner?
    static var barcodeFormatOptionSet: BarcodeFormat = []
    
    @objc
    public static func callback(_ frame: Frame!, withArgs args: [Any]!) -> Any! {
        // let image = VisionImage(buffer: frame.buffer)
        // image.orientation = .up
         guard let imageBuffer = CMSampleBufferGetImageBuffer(frame.buffer) else {
          print("Failed to get image buffer from sample buffer.")
          return nil
        }

        var ciImage = CIImage(cvPixelBuffer: imageBuffer)
        var curDeviceOrientation = UIDevice.current.orientation
        let isLandscape = isDeviceInLandscapeWhenFaceUp()
        //print("current Device Orientation: \(curDeviceOrientation) \(isLandscape)")
        switch curDeviceOrientation {
            case UIDeviceOrientation.portraitUpsideDown:  // Device oriented vertically, Home button on the top
                ciImage = ciImage.oriented(forExifOrientation: 3)
            case UIDeviceOrientation.landscapeLeft:       // Device oriented horizontally, Home button on the right
                ciImage = ciImage.oriented(forExifOrientation: 3)
            case UIDeviceOrientation.landscapeRight:      // Device oriented horizontally, Home button on the left
                ciImage = ciImage.oriented(forExifOrientation: 3)
            case UIDeviceOrientation.portrait:            // Device oriented vertically, Home button on the bottom
                ciImage = ciImage.oriented(forExifOrientation: 1)
            case UIDeviceOrientation.faceUp:
            ciImage = ciImage.oriented(forExifOrientation: isLandscape ? 3 : 1)
            case UIDeviceOrientation.faceDown:
                ciImage = ciImage.oriented(forExifOrientation: isLandscape ? 3 : 1)
            case UIDeviceOrientation.unknown:
                ciImage = ciImage.oriented(forExifOrientation: 1)
            default:
                ciImage = ciImage.oriented(forExifOrientation: 1)
        }
        guard let cgImage = CIContext().createCGImage(ciImage, from: ciImage.extent) else {
            print("Failed to create bitmap from image.")
            return nil
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
        
        do {
            try self.createScanner(args)
            var barcodes: [Barcode] = []
            barcodes.append(contentsOf: try barcodeScanner!.results(in: visionImage))
            
            if let options = args[1] as? [String: Any] {
                let checkInverted = options["checkInverted"] as? Bool ?? false
                if (checkInverted) {
                    guard let buffer = CMSampleBufferGetImageBuffer(frame.buffer) else {
                        return nil
                    }
                    ciImage = CIImage(cvPixelBuffer: buffer)
                    guard let invertedImage = invert(src: ciImage) else {
                        return nil
                    }
                    barcodes.append(contentsOf: try barcodeScanner!.results(in: VisionImage.init(image: invertedImage)))
                }
            }
            
            if (!barcodes.isEmpty){
                for barcode in barcodes {
                    barCodeAttributes.append(self.convertBarcode(barcode: barcode))
                }
            }
            
        } catch _ {
            return nil
        }
        
        return barCodeAttributes
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
