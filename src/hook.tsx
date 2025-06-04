import { useFrameProcessor } from 'react-native-vision-camera';
import type { Frame, ReadonlyFrameProcessor } from 'react-native-vision-camera';
import { useState, useMemo } from 'react';
import { Worklets } from 'react-native-worklets-core';

import type {
  Barcode,
  BarcodeFormat,
  CodeScannerOptions,
  
} from './common';
import { hookScanBarcodes } from './common';

export function useScanBarcodes(
  types: BarcodeFormat[],
  options?: CodeScannerOptions
): [ReadonlyFrameProcessor, Barcode[], number, number] {
  const [barcodes, setBarcodes] = useState<Barcode[]>([]);
  const [frameWidth, setFrameWidth] = useState<number>(1);
  const [frameHeight, setFrameHeight] = useState<number>(1);
  const barcodePlugin = useMemo(() => hookScanBarcodes(types, options), [types, options]);

  const frameProcessor = useFrameProcessor((frame: Frame) => {
    'worklet';
    const detectedBarcodes = barcodePlugin.scanBarcodes(frame, types, options);
    Worklets.createRunOnJS(() => {
      setBarcodes(detectedBarcodes);
      setFrameWidth(frame.width);
      setFrameHeight(frame.height);
    })();
  }, [types, options]);

  return [frameProcessor, barcodes, frameWidth, frameHeight];
}