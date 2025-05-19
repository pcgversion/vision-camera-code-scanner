require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "vision-camera-code-scanner"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "13.0" }
  s.source       = { :git => "https://github.com/pcgversion/vision-camera-code-scanner.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  # Explicitly define public headers.
  # This helps CocoaPods understand the module structure and ensures
  # that 'vision_camera_ocr.h' is findable within the module.
  s.public_header_files = "ios/vision_camera_code_scanner.h"
  
  s.dependency "React-Core"
  s.dependency "VisionCamera"
  s.dependency "GoogleMLKit/BarcodeScanning"
end
