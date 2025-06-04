import * as React from 'react';

import { StyleSheet } from 'react-native';
import {
  useCameraDevice,
  useFrameProcessor,
} from 'react-native-vision-camera';
import { Camera } from 'react-native-vision-camera';
import type { Frame,ReadonlyFrameProcessor } from 'react-native-vision-camera';
import {Worklets} from 'react-native-worklets-core';
import { hookScanBarcodes, BarcodeFormat, type Barcode } from 'vision-camera-code-scanner';

export default function App() {
  const [hasPermission, setHasPermission] = React.useState(false);
  const [barcodes, setBarcodes] = React.useState<Barcode[]>([]);
  const device = useCameraDevice('back', {
    physicalDevices: ['wide-angle-camera'],
  });
  const barcodePlugin = hookScanBarcodes([BarcodeFormat.ALL_FORMATS], {
      checkInverted: true,
  });
  const frameProcessor: ReadonlyFrameProcessor =  useFrameProcessor((frame: Frame) => {
    'worklet';
    const detectedBarcodes =  barcodePlugin.scanBarcodes(frame, [BarcodeFormat.ALL_FORMATS], {
       checkInverted: true,
    }); 
    Worklets.createRunOnJS(() => {
      setBarcodes(detectedBarcodes);
    })();
  }, []);

  React.useEffect(() => {
    (async () => {
      const status = await Camera.requestCameraPermission();
      setHasPermission(status === 'granted');
    })();
  }, []);

  
  return (
    device != null &&
    hasPermission && (
      <>
        <Camera
          style={StyleSheet.absoluteFill}
          device={device}
          isActive={true}
          frameProcessor={frameProcessor}
          zoom={device?.neutralZoom ?? 1}
        />
        {/* {barcodes.map((barcode, idx) => (
          <Text key={idx} style={styles.barcodeTextURL}>
            {barcode.displayValue}
          </Text>
        ))} */}
      </>
    )
  );
}

