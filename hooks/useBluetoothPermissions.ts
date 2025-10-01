import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Platform } from "react-native";
import {
  AndroidPermission,
  openSettings,
  Permission,
  PERMISSIONS,
  PermissionStatus,
  requestMultiple,
  requestNotifications,
  RESULTS,
} from "react-native-permissions";

type ExtendedAndroidPermission = AndroidPermission | "NOTIFICATIONS";

type Config = Partial<{
  [key in Platform["OS"]]: Partial<{
    [key in ExtendedAndroidPermission]: PermissionStatus | null;
  }>;
}>;

const config: Config = {
  android: {
    [PERMISSIONS.ANDROID.BLUETOOTH_SCAN]: null,
    [PERMISSIONS.ANDROID.BLUETOOTH_CONNECT]: null,
    [PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION]: null,
    ["NOTIFICATIONS"]: null,
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

  const requestPermissions = useCallback(async () => {
    if (isGranted) return;
    if (!target) return;

    const withNotifications = "NOTIFICATIONS" in target;
    const permissions = Object.keys(target) as Permission[];

    if (withNotifications) {
      const notificationStatus = await requestNotifications();
      setStatus((prevState) => ({
        ...prevState,
        [Platform.OS]: { ...target, ["NOTIFICATIONS"]: notificationStatus },
      }));
    }

    const statuses = await requestMultiple(permissions);
    setStatus((prevState) => ({
      ...prevState,
      [Platform.OS]: { ...prevState[Platform.OS], ...statuses },
    }));
  }, [isGranted, target]);

  useEffect(() => {
    if (isGranted) return;
    if (!target) return;

    setIsLoading(true);

    requestPermissions()
    .finally(() => setIsLoading(false));
  }, [isGranted, requestPermissions, target]);

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
