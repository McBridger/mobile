import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Platform } from "react-native";
import {
  AndroidPermission,
  openSettings,
  Permission,
  PERMISSIONS,
  PermissionStatus,
  requestMultiple,
  RESULTS,
} from "react-native-permissions";

type Config = Partial<{
  [key in Platform["OS"]]: Partial<{
    [key in AndroidPermission]: PermissionStatus | null;
  }>;
}>;

const config: Config = {
  android: {
    [PERMISSIONS.ANDROID.BLUETOOTH_SCAN]: null,
    [PERMISSIONS.ANDROID.BLUETOOTH_CONNECT]: null,
    [PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION]: null,
  },
};

export function useBluetoothPermissions() {
  const [status, setStatus] = useState<Config>(config);
  const [isLoading, setIsLoading] = useState(true);

  const target = useMemo(() => config[Platform.OS], []);
  const isGranted = useMemo(
    () =>
      Object.values(status[Platform.OS] || {}).every(
        (s) => s === RESULTS.GRANTED || s === RESULTS.UNAVAILABLE
      ),
    [status]
  );

  useEffect(() => {
    if (isGranted) return;
    if (!target) return;

    const permissions = Object.keys(target) as Permission[];
    setIsLoading(true);

    requestMultiple(permissions)
      .then((statuses) =>
        setStatus((prevState) => ({
          ...prevState,
          [Platform.OS]: { ...target, ...statuses },
        }))
      )
      .finally(() => setIsLoading(false));
  }, [isGranted, target]);

  const requestPermissions = useCallback(async () => {
    if (isGranted) return;
    if (!target) return;

    const permissions = Object.keys(target) as Permission[];

    setIsLoading(true);

    requestMultiple(permissions)
      .then((statuses) =>
        setStatus((prevState) => ({
          ...prevState,
          [Platform.OS]: { ...target, ...statuses },
        }))
      )
      .finally(() => setIsLoading(false));
  }, [isGranted, target]);

  const showPermissionRationale = useCallback(() => {
    Alert.alert(
      "Permissions Required",
      "For full functionality with Bluetooth devices, the application requires permissions for scanning, connecting, and location. Please grant them in the settings.",
      [
        { text: "Cancel", style: "cancel" },
        { text: "Open Settings", onPress: () => openSettings() },
      ]
    );
  }, []);

  return {
    status,
    isLoading,
    request: requestPermissions,
    allPermissionsGranted: isGranted,
    showPermissionRationale,
  };
}
