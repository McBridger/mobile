import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { EventEmitter } from 'react-native/Libraries/Types/CodegenTypes';

export interface BleDevice {
  name: string | null;
  address: string;
  rssi: number;
}

export interface ScanError {
  code: number;
  message: string;
}

interface ModuleConstants {
  readonly ERRORS: {
    readonly SCAN_IN_PROGRESS: number;
    readonly BLUETOOTH_UNAVAILABLE: number;
    readonly BLUETOOTH_DISABLED: number;
    readonly START_SCAN_FAILED: number;
  };
}

export interface Spec extends TurboModule {
  getConstants(): ModuleConstants;

  // Methods
  startScan(): Promise<void>;
  stopScan(): Promise<void>;

  // Events
  readonly onDeviceFound: EventEmitter<BleDevice>;
  readonly onScanFailed: EventEmitter<ScanError>;
  readonly onScanStopped: EventEmitter<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'BleScanner',
);