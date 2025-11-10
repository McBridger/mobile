import { useConnector } from "@/modules/connector";
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback, useMemo } from "react";
import { useTheme, Appbar, Divider } from "react-native-paper";
import { PATH_NAMES, PATHS, Status } from "@/constants";

const Header = () => {
  const theme = useTheme();
  const status = useConnector((state) => state.status);
  const name = useConnector((state) => state.name);
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];

  const { isCoonectionRouteName, isDevicesRouteName } = useMemo(() => {
    return {
      isCoonectionRouteName: currentRouteName === PATH_NAMES.CONNECTION,
      isDevicesRouteName: currentRouteName === PATH_NAMES.DEVICES,
    };
  }, [currentRouteName]);

  const getBackgroundColor = useCallback(() => {
    const defaultColor = theme.colors.background;
    switch (status) {
      case Status.Connected:
        return defaultColor;
      case Status.Connecting:
      case Status.Disconnecting:
        return theme.colors.outlineVariant;
      default:
        return defaultColor;
    }
  }, [status]);

  const handleLeftButtonPress = useCallback(() => {
    if (isCoonectionRouteName) router.push({ pathname: PATHS.DEVICES });
  }, [currentRouteName, router]);

  const handleRightButtonPress = useCallback(() => {
    if (isDevicesRouteName && status === Status.Connected)
      router.push({ pathname: PATHS.CONNECTION });
  }, [currentRouteName, status, router]);

  const leftButton = isCoonectionRouteName ? (
    <Appbar.BackAction onPress={handleLeftButtonPress} />
  ) : null;

  const rightButton =
    isDevicesRouteName && status === Status.Connected ? (
      <Appbar.Action icon="arrow-right" onPress={handleRightButtonPress} />
    ) : null;

  return (
    <>
      <Appbar.Header style={{ backgroundColor: getBackgroundColor() }}>
        {leftButton}
        <Appbar.Content
          title={
            status === Status.Connected && name ? name : capitalize(status)
          }
        />
        {rightButton}
      </Appbar.Header>
      <Divider />
    </>
  );
};

export default Header;
