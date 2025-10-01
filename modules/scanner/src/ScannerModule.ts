import { NativeModule, requireNativeModule } from 'expo';

import { ErrorConstants, ScannerModuleEvents } from './ScannerModule.types';

declare class ScannerModule extends NativeModule<ScannerModuleEvents> {
  ERRORS: ErrorConstants;
  startScan(): Promise<void>;
  stopScan(): Promise<void>;
}

export default requireNativeModule<ScannerModule>('ScannerModule');
