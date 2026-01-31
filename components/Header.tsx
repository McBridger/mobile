import { useThemeStore } from "@/hooks/useThemeStore";
import { STATUS, useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback, useMemo } from "react";
import { StyleSheet, View } from "react-native";
import { Appbar, Text, useTheme } from "react-native-paper";
import { TutorialTarget } from "./TutorialTarget";

import { useSettingsStore } from "@/hooks/useSettingsStore";
import { useTutorialStore } from "@/hooks/useTutorialStore";

const Header = () => {
  const status = useConnector((state) => state.status);
  const theme = useTheme() as AppTheme;
  const { toggleTheme } = useThemeStore();
  const { setHasSeenTutorial } = useSettingsStore();
  const { startTutorial } = useTutorialStore();

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
        return "Ready to sync";
      case STATUS.ERROR:
        return "Connection error";
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
        <View style={{ flexDirection: "row" }}>
          {showBackButton ? (
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
          ) : (
            <TutorialTarget name="settings">
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
            </TutorialTarget>
          )}
        </View>
      )}

      <Appbar.Content
        title={
          <View style={styles.titleContainer}>
            <TutorialTarget name="header">
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
            </TutorialTarget>
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
      {/* temp icon that starts tutorial on click */}
      <Appbar.Action
        icon={({ size }) => (
          <Ionicons
            name="school-outline"
            size={size}
            color={theme.colors.onStatus}
          />
        )}
        onPress={() => {
          setHasSeenTutorial(false);
          startTutorial();
        }}
        color={theme.colors.onStatus}
        rippleColor={theme.colors.statusRipple}
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
    marginTop: 4,
  },
  statusText: {
    fontWeight: "bold",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
});

export default Header;
