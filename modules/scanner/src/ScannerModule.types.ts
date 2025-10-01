export type DeviceFoundEventPayload = {
  name: string | null;
  address: string;
  rssi: number;
  services: string[];
};

export type ScanFailedEventPayload = {
  code: number;
  message: string;
};

export type ErrorConstants = {
  SCAN_IN_PROGRESS: number;
  BLUETOOTH_UNAVAILABLE: number;
  BLUETOOTH_DISABLED: number;
  START_SCAN_FAILED: number;
};

export type ScannerModuleEvents = {
  onDeviceFound: (payload: DeviceFoundEventPayload) => void;
  onScanStopped: () => void;
  onScanFailed: (payload: ScanFailedEventPayload) => void;
};
