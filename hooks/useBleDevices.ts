import { useMemo } from "react";
import { useScanner } from "@/modules/scanner";
import { useShallow } from "zustand/react/shallow";

export const useBleDevices = () => {
  const { isScanning, devices, bridgers, start, stop, clear } = useScanner(
    useShallow((state) => ({
      isScanning: state.isScanning,
      devices: state.devices,
      bridgers: state.bridges,
      start: state.start,
      stop: state.stop,
      clear: state.clear,
    }))
  );

  const sections = useMemo(() => {
    const bridgersArray = Array.from(bridgers.values());
    const otherDevicesArray = Array.from(devices.values());
    const sectionsData = [];

    if (bridgersArray.length > 0) {
      sectionsData.push({ title: "BLE Devices", data: bridgersArray });
    }
    if (otherDevicesArray.length > 0) {
      sectionsData.push({ title: "Other Devices", data: otherDevicesArray });
    }
    return sectionsData;
  }, [bridgers, devices]);

  return {
    isScanning,
    sections,
    startScan: start,
    stopScan: stop,
    clearDevices: clear,
  };
};
