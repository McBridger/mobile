import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, AppState, AppStateStatus, Platform } from "react-native";
import {
  checkMultiple,
  checkNotifications,
  Permission,
  PERMISSIONS,
  PermissionStatus,
  requestMultiple,
  requestNotifications,
  RESULTS
} from "react-native-permissions";

type Config = {
  android: Record<string, PermissionStatus>;
  ios: Record<string, PermissionStatus>;
};

const initialConfig: Config = {
  android: {
    [PERMISSIONS.ANDROID.BLUETOOTH_SCAN]: RESULTS.DENIED,
    [PERMISSIONS.ANDROID.BLUETOOTH_CONNECT]: RESULTS.DENIED,
    [PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION]: RESULTS.DENIED,
    ["NOTIFICATIONS"]: RESULTS.DENIED,
  },
  ios: {
    [PERMISSIONS.IOS.BLUETOOTH]: RESULTS.DENIED,
    ["NOTIFICATIONS"]: RESULTS.DENIED,
  },
};

export function useBluetoothPermissions() {
  const [status, setStatus] = useState<Config>(initialConfig);
  const [isLoading, setIsLoading] = useState(true);

  const isMandatoryGranted = useMemo(() => {
    const os = Platform.OS as keyof Config;
    const current = status[os];
    if (os === "android") {
      return (
        current[PERMISSIONS.ANDROID.BLUETOOTH_SCAN] === RESULTS.GRANTED &&
        current[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT] === RESULTS.GRANTED &&
        current[PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION] === RESULTS.GRANTED
      );
    }
    return current[PERMISSIONS.IOS.BLUETOOTH] === RESULTS.GRANTED;
  }, [status]);

  const updateStatuses = useCallback(async () => {
    const os = Platform.OS as keyof Config;
    const permissionsToCheck = Object.keys(initialConfig[os]).filter(
      (p) => p !== "NOTIFICATIONS"
    ) as Permission[];

    const statuses = await checkMultiple(permissionsToCheck);
    const { status: notificationStatus } = await checkNotifications();

    setStatus((prev) => ({
      ...prev,
      [os]: {
        ...statuses,
        NOTIFICATIONS: notificationStatus,
      },
    }));
  }, []);

  useEffect(() => {
    updateStatuses().finally(() => setIsLoading(false));

    const subscription = AppState.addEventListener("change", (nextAppState: AppStateStatus) => {
      if (nextAppState === "active") {
        updateStatuses();
      }
    });

    return () => subscription.remove();
  }, [updateStatuses]);

  const requestPermissions = useCallback(async () => {
    const os = Platform.OS as keyof Config;
    const permissionsToRequest = Object.keys(initialConfig[os]).filter(
      (p) => p !== "NOTIFICATIONS"
    ) as Permission[];

    const statuses = await requestMultiple(permissionsToRequest);
    const { status: notificationStatus } = await requestNotifications();

    setStatus((prev) => ({
      ...prev,
      [os]: {
        ...prev[os],
        ...statuses,
        NOTIFICATIONS: notificationStatus,
      },
    }));
  }, []);

  const showPermissionRationale = useCallback(() => {
    Alert.alert(
      "Permissions Required",
      "McBridge needs these permissions to find and sync with your Mac.",
      [
        { text: "Close", style: "default" },      ]
    );
  }, []);

  return {
    status,
    isLoading,
    request: requestPermissions,
    allPermissionsGranted: isMandatoryGranted,
    showPermissionRationale,
  };
}
