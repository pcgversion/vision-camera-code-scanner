import { useFrameProcessor } from 'react-native-vision-camera';
import type { Frame, ReadonlyFrameProcessor } from 'react-native-vision-camera';
import { useState } from 'react';
import { Worklets } from 'react-native-worklets-core';

import type {
  Barcode,
  BarcodeFormat,
  CodeScannerOptions,
  
} from './common';
import { scanBarcodes } from './common';
export function useScanBarcodes(
  types: BarcodeFormat[],
  options?: CodeScannerOptions
): [(frame: Frame) => void, Barcode[], number, number] {
  const [barcodes, setBarcodes] = useState<Barcode[]>([]);
  const [frameWidth, setFrameWidth] = useState<number>(1);
  const [frameHeight, setFrameHeight] = useState<number>(1);

  const frameProcessor = useFrameProcessor((frame: Frame) => {
    'worklet';
    const detectedBarcodes = scanBarcodes(frame, types, options);
    Worklets.createRunOnJS(() => {
      setBarcodes(detectedBarcodes);
      setFrameWidth(frame.width);
      setFrameHeight(frame.height);
    })
  }, []);

  return [frameProcessor, barcodes, frameWidth, frameHeight];
}