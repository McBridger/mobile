import { useConnector } from "@/modules/connector";
import { Ionicons } from '@expo/vector-icons';
import { useRouter, useSegments } from "expo-router";
import { capitalize } from "lodash";
import React, { useCallback } from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const Header = () => {
  const status = useConnector((state) => state.status);
  const name = useConnector((state) => state.name);
  const router = useRouter();
  const segments = useSegments();
  const currentRouteName = segments[segments.length - 1];

  const getBackgroundColor = useCallback(() => {
    switch (status) {
      case "connected":
        return "#34C759"; // iOS Success Green
      case "connecting":
        return "#007AFF"; // iOS Blue
      case "disconnecting":
        return "#5856D6"; // iOS Purple
      default:
        return "#8E8E93"; // iOS Gray
    }
  }, [status]);

  const getStatusText = useCallback(() => {
    if (status === "connected") return name || "Connected";
    if (status === "connecting") return "Connecting...";
    if (status === "disconnected") return "Magic Sync Active";
    return capitalize(status);
  }, [status, name]);

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
