import { useThemeStore } from "@/hooks/useThemeStore";
import { STATUS, useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback, useMemo } from "react";
import { StyleSheet, View } from "react-native";
import { Appbar, Text, useTheme } from "react-native-paper";

const Header = () => {
  const status = useConnector((state) => state.status);
  const theme = useTheme() as AppTheme;
  const { toggleTheme } = useThemeStore();
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];

  const getBackgroundColor = useCallback(() => {
    switch (status) {
      case STATUS.CONNECTED:
        return theme.colors.connected;
      case STATUS.CONNECTING:
      case STATUS.DISCOVERING:
        return theme.colors.connecting;
      case STATUS.ENCRYPTING:
      case STATUS.KEYS_READY:
      case STATUS.TRANSPORT_INITIALIZING:
        return theme.colors.connecting;
      case STATUS.ERROR:
        return theme.colors.statusError;
      default:
        return theme.colors.connecting;
    }
  }, [status, theme]);

  const getStatusText = useCallback(() => {
    switch (status) {
      case STATUS.CONNECTED:
        return "Connected";
      case STATUS.CONNECTING:
        return "Connecting";
      case STATUS.DISCOVERING:
        return "Searching for Mac";
      case STATUS.ENCRYPTING:
        return "Encrypting";
      case STATUS.READY:
        return "Ready to Sync";
      case STATUS.ERROR:
        return "Connection Error";
      default:
        return capitalize(status.toLowerCase());
    }
  }, [status]);

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
    if (currentRouteName === "connection") router.push("/setup");
  }, [currentRouteName, router]);

  const handleBackButtonPress = useCallback(() => {
    if (currentRouteName === "setup") router.back();
  }, [currentRouteName, router]);

  const showBackButton = currentRouteName !== "connection";
  const isStatusIdle = status === STATUS.IDLE;

  return (
    <Appbar.Header
      style={[styles.appbar, { backgroundColor: getBackgroundColor() }]}
      mode="center-aligned"
    >
      {!isStatusIdle && (
        <Appbar.Action
          icon={({ size }) => (
            <Ionicons
              name={showBackButton ? "arrow-back-outline" : "settings-outline"}
              size={size}
              color={theme.colors.onStatus}
            />
          )}
          onPress={
            showBackButton ? handleBackButtonPress : handleLeftButtonPress
          }
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
              <Text
                variant="labelSmall"
                style={[styles.statusText, { color: theme.colors.onStatus }]}
              >
                {getStatusText()}
              </Text>
            </View>
            <Text
              variant="titleLarge"
              style={[styles.headerTitle, { color: theme.colors.onStatus }]}
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
    height: 90,
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
  statusText: {
    fontWeight: "800",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  headerTitle: {
    fontWeight: "800",
  },
});

export default Header;
