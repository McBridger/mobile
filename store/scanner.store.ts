import BleScanner, { BleDevice, ScanError } from "@/specs/NativeBleScanner";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { setError } from ".";

interface Scanner {
  isScanning: boolean;
  devices: Map<string, BleDevice>;

  start: () => void;
  stop: () => void;
  clear: () => void;
}

const log = console.log.bind(null, "[useScanner]");
const warn = console.warn.bind(null, "[useScanner]");
const error = console.error.bind(null, "[useScanner]");

export const useScanner = create<Scanner>()(
  subscribeWithSelector((set) => ({
    isScanning: false,
    devices: new Map(),

    start: () => {
      BleScanner.startScan()
        .then(() => {
          set({ isScanning: true });
          log("Scan initiated.");
        })
        .catch((error) => {
          warn(
            "startScan() promise rejected. This is expected for initial validation errors.",
            error
          );
        });
    },

    stop: () => {
      BleScanner.stopScan().catch((err) => {
        // It's very rare for stopScan to fail, but we should log it.
        error("stopScan() promise rejected.", err);
      });
    },

    clear: () => {
      set({ devices: new Map() });
    },
  }))
);

export const handleDeviceFound = (device: BleDevice) => {
  useScanner.setState((prev) => ({
    ...prev,
    devices: new Map(prev.devices).set(device.address, device),
  }));
};

export const handleScanFailed = (error: ScanError) => {
  setError(new Error(`Scan Failed: ${error.message} (Code: ${error.code})`));
};

export const handleScanStopped = () => {
  log("Scan Stopped.");
  useScanner.setState({ isScanning: false });
};
