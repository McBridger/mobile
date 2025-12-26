import { useConnector } from "@/modules/connector";
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback } from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const Header = () => {
  const brokerStatus = useConnector((state) => state.brokerStatus);
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];

  const getBackgroundColor = useCallback(() => {
    switch (brokerStatus) {
      case "connected":
        return "#008564"; // Green
      case "connecting":
      case "discovering":
        return "#769cdf"; // Blue
      case "encrypting":
      case "keys_ready":
      case "transport_initializing":
        return "#ff9b7e"; // Orange
      case "error":
        return "#e6000f"; // Red
      default:
        return "#B2B2B2"; // Gray
    }
  }, [brokerStatus]);

  const getStatusText = useCallback(() => {
    switch (brokerStatus) {
      case "connected": return "Connected";
      case "connecting": return "Connecting...";
      case "discovering": return "Searching for Mac...";
      case "encrypting": return "Encrypting...";
      case "ready": return "Ready to Sync";
      case "error": return "Connection Error";
      default: return capitalize(brokerStatus);
    }
  }, [brokerStatus]);

  const handleLeftButtonPress = useCallback(() => {
    if (currentRouteName === "connection")
      router.push("/setup");
  }, [currentRouteName, router]);

  const leftButton =
    currentRouteName === "connection" ? (
      <TouchableOpacity
        onPress={handleLeftButtonPress}
        style={styles.leftButton}
      >
        <Ionicons name="settings-outline" size={24} color="white" />
      </TouchableOpacity>
    ) : null;

  return (
    <SafeAreaView style={{ backgroundColor: getBackgroundColor() }} edges={['top']}>
      <View style={styles.headerContainer}>
        <View style={styles.buttonWrapper}>{leftButton}</View>
        <Text style={styles.headerTitle}>
          {getStatusText()}
        </Text>
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
    alignItems: 'center',
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
