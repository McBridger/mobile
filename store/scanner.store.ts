import { AppConfig } from "@/app.config";
import BleScanner, { BleDevice as NativeBleDevice, ScanError } from "@/specs/NativeBleScanner";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { setError } from ".";

export interface BleDevice extends NativeBleDevice {
  isBridger?: boolean;
}

interface Scanner {
  isScanning: boolean;
  devices: Map<string, BleDevice>;
  bridgerDevices: Map<string, BleDevice>;

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
    bridgerDevices: new Map(),

    start: () => {
      BleScanner.startScan()
        .then(() => {
          set({ isScanning: true });
          log("Scan initiated.");
        })
        .catch((err) => {
          warn(
            "startScan() promise rejected. This is expected for initial validation errors.",
            err
          );
        });
    },

    stop: () => {
      BleScanner.stopScan().catch((err) => {
        error("stopScan() promise rejected.", err); // It's very rare for stopScan to fail, but we should log it.
      });
    },

    clear: () => {
      set({ devices: new Map(), bridgerDevices: new Map() });
      pool = { devices: new Map(), bridgerDevices: new Map() };
      if (timeout) clearTimeout(timeout);
      timeout = null;
    },
  }))
);

let pool = {
  devices: new Map<string, BleDevice>(),
  bridgerDevices: new Map<string, BleDevice>(),
};
let timeout: number | null = null;

const flushPool = (finalState: Partial<Scanner> = {}) => {
  if (timeout) clearTimeout(timeout);
  timeout = null;

  useScanner.setState((prev) => {
    if (!pool.devices.size && !pool.bridgerDevices.size) return prev;

    const devices = new Map(prev.devices);
    const bridgerDevices = new Map(prev.bridgerDevices);

    pool.devices.forEach((device, key) => devices.set(key, device));
    pool.bridgerDevices.forEach((device, key) => bridgerDevices.set(key, device));

    pool = { devices: new Map(), bridgerDevices: new Map() };

    return { ...prev, devices, bridgerDevices, ...finalState };
  });
};

export const handleDeviceFound = (
  extra: AppConfig["extra"],
  device: BleDevice
) => {
  const isBridger = device.services?.includes(extra.ADVERTISE_UUID);

  if (isBridger) pool.bridgerDevices.set(device.address, Object.assign(device, { isBridger: true }));
  else pool.devices.set(device.address, device);

  if (timeout) return;
  timeout = setTimeout(flushPool, 500);
};

export const handleScanFailed = (error: ScanError) => {
  setError(new Error(`Scan Failed: ${error.message} (Code: ${error.code})`));
  flushPool({ isScanning: false });
};

export const handleScanStopped = () => {
  log("Scan Stopped.");
  flushPool({ isScanning: false });
};
