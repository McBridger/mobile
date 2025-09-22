import { useConnector } from "@/store/connection.store";
import { capitalizeFirstLetter } from "@/utils";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, useSegments } from "expo-router";
import { useCallback } from "react";
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
        return "lightblue";
      case "connecting":
      case "disconnecting":
        return "#00008B"; // Using a standard deep blue hex code
      default:
        return "lightgray";
    }
  }, [status]);

  const getTextColor = useCallback(() => {
    if (status === "connected") return "darkblue";
    return "white";
  }, [status]);

  const handleLeftButtonPress = useCallback(() => {
    if (currentRouteName === "connection")
      router.push({ pathname: "/devices" });
  }, [currentRouteName, router]);

  const handleRightButtonPress = useCallback(() => {
    if (currentRouteName === "devices" && status === "connected")
      router.push({ pathname: "/connection" });
  }, [currentRouteName, status, router]);

  const leftButton =
    currentRouteName === "connection" ? (
      <TouchableOpacity
        onPress={handleLeftButtonPress}
        style={styles.leftButton}
      >
        <Ionicons name="arrow-back" size={24} color={getTextColor()} />
      </TouchableOpacity>
    ) : null;

  const rightButton =
    currentRouteName === "devices" && status === "connected" ? (
      <TouchableOpacity
        onPress={handleRightButtonPress}
        style={styles.rightButton}
      >
        <Ionicons name="arrow-forward" size={24} color={getTextColor()} />
      </TouchableOpacity>
    ) : null;

  return (
    <SafeAreaView style={{ backgroundColor: getBackgroundColor() }}>
      <View style={styles.headerContainer}>
        {leftButton}
        <Text style={[styles.headerTitle, { color: getTextColor() }]}>
          {status === "connected" && name ? name : capitalizeFirstLetter(status)}
        </Text>
        {rightButton}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  headerContainer: {
    height: 50,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 15,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: "bold",
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
