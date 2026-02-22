import { useThemeStore } from "@/hooks/useThemeStore";
import {
  BleState,
  EncryptionState,
  TcpState,
  useConnector,
} from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, useSegments } from "expo-router";
import React, { useCallback, useMemo } from "react";
import { StyleSheet, View } from "react-native";
import { Appbar, Text, useTheme } from "react-native-paper";
import { useShallow } from "zustand/shallow";

const Header = () => {
  const [ble, tcp, encryption, isReady] = useConnector(
    useShallow((curr) => [
      curr.state.ble.current,
      curr.state.tcp.current,
      curr.state.encryption.current,
      curr.isReady,
    ]),
  );
  const theme = useTheme() as AppTheme;
  const { toggleTheme } = useThemeStore();
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];
  const isConnectionRoute = currentRouteName === "connection";

  const getBackgroundColor = useCallback(() => {
    if (ble === BleState.ERROR || encryption === EncryptionState.ERROR) {
      return theme.colors.statusError;
    }
    if (tcp === TcpState.TRANSFERRING) return theme.colors.connected;
    if (ble === BleState.CONNECTED) return theme.colors.connected;
    return theme.colors.connecting;
  }, [ble, tcp, encryption, theme]);

  const getStatusText = useCallback(() => {
    if (encryption === EncryptionState.ERROR) return "Security Error";
    if (ble === BleState.ERROR) return "Link Error";
    if (tcp === TcpState.TRANSFERRING) return "Turbo Active";
    if (ble === BleState.CONNECTED) return "Connected";
    if (ble === BleState.CONNECTING) return "Connecting";
    if (ble === BleState.SCANNING) return "Searching...";
    if (encryption === EncryptionState.ENCRYPTING) return "Securing...";

    return "Idle";
  }, [encryption, ble, tcp]);

  // useEffect(() => {
  //   console.log("TLT ble", ble);
  // }, [ble]);

  const getTitle = useMemo(() => {
    switch (currentRouteName) {
      case "connection":
        return "Activity Feed";
      case "setup":
        return "Security";
      case "permissions":
        return "Permissions";
      default:
        return "App";
    }
  }, [currentRouteName]);

  const handleLeftButtonPress = useCallback(() => {
    if (isConnectionRoute) router.push("/setup");
  }, [isConnectionRoute, router]);

  const handleBackButtonPress = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace("/connection");
    }
  }, [router]);

  // console.log("TLT currentRouteName", currentRouteName);

  // Показываем кнопку назад только если мы в настройках и уже всё сконфигурировано
  const showBackButton = currentRouteName === "setup" && isReady;
  const isIdle = encryption === EncryptionState.IDLE && ble === BleState.IDLE;

  return (
    <Appbar.Header
      style={[styles.appbar, { backgroundColor: getBackgroundColor() }]}
      mode="center-aligned"
    >
      {!isIdle && showBackButton && (
        <Appbar.Action
          testID="arrow-back-outline"
          accessibilityLabel="Back"
          icon={({ size }) => (
            <Ionicons
              name="arrow-back-outline"
              size={size}
              color={theme.colors.onStatus}
            />
          )}
          onPress={handleBackButtonPress}
          color={theme.colors.onStatus}
          rippleColor={theme.colors.statusRipple}
        />
      )}

      {!isIdle && !showBackButton && isConnectionRoute && (
        <Appbar.Action
          testID="settings-outline"
          accessibilityLabel="Settings"
          icon={({ size }) => (
            <Ionicons
              name="settings-outline"
              size={size}
              color={theme.colors.onStatus}
            />
          )}
          onPress={handleLeftButtonPress}
          color={theme.colors.onStatus}
          rippleColor={theme.colors.statusRipple}
        />
      )}

      <Appbar.Content
        title={
          <View style={styles.titleContainer}>
            <View
              style={[
                styles.statusBadge,
                { backgroundColor: theme.colors.statusBadgeBackground },
              ]}
            >
              <View style={styles.badgeRow}>
                {tcp === TcpState.TRANSFERRING && (
                  <Ionicons
                    name="flash"
                    size={12}
                    color={theme.colors.onStatus}
                    style={{ marginRight: 4 }}
                  />
                )}
                <Text
                  variant="labelSmall"
                  style={[styles.statusText, { color: theme.colors.onStatus }]}
                >
                  {getStatusText()}
                </Text>
              </View>
            </View>
            <Text
              testID="title"
              variant="titleLarge"
              style={{ color: theme.colors.onStatus }}
            >
              {getTitle}
            </Text>
          </View>
        }
      />

      <Appbar.Action
        icon={({ size }) => (
          <Ionicons
            name={theme.dark ? "sunny-outline" : "moon-outline"}
            size={size}
            color={theme.colors.onStatus}
          />
        )}
        onPress={toggleTheme}
        color={theme.colors.onStatus}
        rippleColor={theme.colors.statusRipple}
      />
    </Appbar.Header>
  );
};

const styles = StyleSheet.create({
  appbar: {
    height: 80,
    justifyContent: "center",
    elevation: 0,
  },
  titleContainer: {
    alignItems: "center",
    justifyContent: "center",
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 2,
    borderRadius: 20,
    marginBottom: 4,
  },
  badgeRow: {
    flexDirection: "row",
    alignItems: "center",
  },
  statusText: {
    fontWeight: "bold",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  buttonWrapper: {
    width: 40,
    alignItems: "center",
  },
});

export default Header;
