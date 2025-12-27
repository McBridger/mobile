import { STATUS, useConnector } from "@/modules/connector";
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback } from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const Header = () => {
  const status = useConnector((state) => state.status);
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];

  const getBackgroundColor = useCallback(() => {
    switch (status) {
      case STATUS.CONNECTED:
        return "#34C759"; // Green
      case STATUS.CONNECTING:
      case STATUS.DISCOVERING:
        return "#007AFF"; // Blue
      case STATUS.ENCRYPTING:
      case STATUS.KEYS_READY:
      case STATUS.TRANSPORT_INITIALIZING:
        return "#FF9500"; // Orange
      case STATUS.ERROR:
        return "#FF3B30"; // Red
      default:
        return "#B2B2B2"; // Gray
    }
  }, [status]);

  const getStatusText = useCallback(() => {
    switch (status) {
      case STATUS.CONNECTED: return "Connected";
      case STATUS.CONNECTING: return "Connecting...";
      case STATUS.DISCOVERING: return "Searching for Mac...";
      case STATUS.ENCRYPTING: return "Encrypting...";
      case STATUS.READY: return "Ready to Sync";
      case STATUS.ERROR: return "Connection Error";
      default: return capitalize(status.toLowerCase());
    }
  }, [status]);

  const handleLeftButtonPress = useCallback(() => {
    if (currentRouteName === "connection") router.push("/setup");
  }, [currentRouteName, router]);

  const handleBackButtonPress = useCallback(() => {
     if (currentRouteName === "setup") router.push("/connection");
  }, [currentRouteName, router]);

  const leftButton =
    currentRouteName === "connection" ? (
      <TouchableOpacity
        onPress={handleLeftButtonPress}
        style={styles.leftButton}
      >
        <Ionicons name="settings-outline" size={24} color="white" />
      </TouchableOpacity>
    ) : (
      <TouchableOpacity onPress={handleBackButtonPress}>
        <Ionicons name="arrow-back-outline" size={24} color="white" />
      </TouchableOpacity>
    );

  return (
    <SafeAreaView
      style={{ backgroundColor: getBackgroundColor() }}
      edges={["top"]}
    >
      <View style={styles.headerContainer}>
        <View style={styles.buttonWrapper}>{leftButton}</View>
        <Text style={styles.headerTitle}>{getStatusText()}</Text>
        <View style={styles.buttonWrapper} />
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  headerContainer: {
    height: 56,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 15,
  },
  buttonWrapper: {
    width: 40,
    alignItems: "center",
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: "white",
    flex: 1,
    textAlign: "center",
  },
  leftButton: {
    padding: 5,
  },
  rightButton: {
    padding: 5,
  },
});

export default Header;
