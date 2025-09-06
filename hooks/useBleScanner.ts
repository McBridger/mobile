import BleScanner, { BleDevice, ScanError } from '@/specs/NativeBleScanner';
import { useCallback, useEffect, useMemo, useState } from 'react';

export function useBleScanner() {
  const [isScanning, setIsScanning] = useState(false);
  const [devicesMap, setDevicesMap] = useState<Map<string, BleDevice>>(new Map());
  const [scanError, setScanError] = useState<ScanError | null>(null);

  // --- Event Handlers ---
  // These callbacks are the single source of truth for state changes from native events.

  const handleDeviceFound = useCallback((device: BleDevice) => {
    setDevicesMap(prevMap => new Map(prevMap).set(device.address, device));
  }, []);

  const handleScanFailed = useCallback((error: ScanError) => {
    console.error('[BLE Hook] Scan Failed:', error);
    setScanError(error);
    setIsScanning(false);
  }, []);

  const handleScanStopped = useCallback(() => {
    // This can be triggered by stopScan() or the automatic timeout.
    console.log('[BLE Hook] Scan Stopped.');
    setIsScanning(false);
  }, []);

  // --- Effect for Subscriptions ---
  // Sets up and tears down the native event listeners.

  useEffect(() => {
    const subscriptions = [
      BleScanner.onDeviceFound(handleDeviceFound),
      BleScanner.onScanFailed(handleScanFailed),
      BleScanner.onScanStopped(handleScanStopped),
    ];

    return () => {
      subscriptions.forEach(sub => sub.remove());
    };
  }, [handleDeviceFound, handleScanFailed, handleScanStopped]);

  // --- Public API Methods ---
  // These are the functions returned by the hook for the component to call.

  const startScan = useCallback(() => {
    // 1. Reset state for a new scan session.
    setDevicesMap(new Map());
    setScanError(null);

    // 2. Request the scan. We don't need a try-catch for UI logic.
    // The onScanFailed listener is our centralized error handler.
    BleScanner.startScan()
      .then(() => {
        // The *request* to scan was accepted by the native module.
        setIsScanning(true);
        console.log('[BLE Hook] Scan initiated.');
      })
      .catch(error => {
        // This is now just a debug log. The onScanFailed event, which was
        // also fired, will handle the actual state update.
        console.warn('[BLE Hook] startScan() promise rejected. This is expected for initial validation errors.', error);
      });
  }, []);

  const stopScan = useCallback(() => {
    BleScanner.stopScan().catch(error => {
      // It's very rare for stopScan to fail, but we should log it.
      console.error('[BLE Hook] stopScan() promise rejected.', error);
    });
  }, []);

  const clearDevices = useCallback(() => {
    setDevicesMap(new Map());
  }, []);

  // Memoize the conversion from Map to Array so the component
  // doesn't re-render unnecessarily if the device order/data hasn't changed.
  const foundDevices = useMemo(() => Array.from(devicesMap.values()), [devicesMap]);

  return {
    isScanning,
    foundDevices,
    scanError,
    startScan,
    stopScan,
    clearDevices,
  };
}