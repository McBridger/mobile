import { AppConfig } from "@/app.config";
import BleScanner, { BleDevice, ScanError } from "@/specs/NativeBleScanner";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { setError } from ".";

interface Scanner {
  isScanning: boolean;
  devices: Map<string, BleDevice>;
  bleDevices: Map<string, BleDevice>;

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
    bleDevices: new Map(),

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
      set({ devices: new Map(), bleDevices: new Map() });
      pool = { devices: new Map(), bleDevices: new Map() };
      if (timeout) clearTimeout(timeout);
      timeout = null;
    },
  }))
);

let pool = {
  devices: new Map<string, BleDevice>(),
  bleDevices: new Map<string, BleDevice>(),
};
let timeout: number | null = null;

const flushPool = (finalState: Partial<Scanner> = {}) => {
  if (timeout) clearTimeout(timeout);
  timeout = null;

  useScanner.setState((prev) => {
    if (!pool.devices.size && !pool.bleDevices.size) return prev;

    const devices = new Map(prev.devices);
    const bleDevices = new Map(prev.bleDevices);

    pool.devices.forEach((device, key) => devices.set(key, device));
    pool.bleDevices.forEach((device, key) => bleDevices.set(key, device));

    pool = { devices: new Map(), bleDevices: new Map() };

    return { ...prev, devices, bleDevices, ...finalState };
  });
};

export const handleDeviceFound = (
  extra: AppConfig["extra"],
  device: BleDevice
) => {
  const isBleDevice = device.services?.includes(extra.BRIDGER_SERVICE_UUID);

  if (isBleDevice) pool.bleDevices.set(device.address, device);
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
