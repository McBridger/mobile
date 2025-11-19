import { useAppConfig } from "@/hooks/useConfig";
import { BleDevice } from "@/modules/scanner";
import { useRouter } from "expo-router";
import { useCallback, useMemo } from "react";
import { SectionList, StyleSheet, View } from "react-native";
import { useTheme, Button, Text, ActivityIndicator } from "react-native-paper";
import { PATHS } from "@/constants";
import DeviceCard from "@/components/DeviceCard";
import { useBleDevices } from "@/hooks/useBleDevices";

export default function Devices() {
  const { extra } = useAppConfig();
  const router = useRouter();
  const theme = useTheme();

  const styles = useMemo(() => getStyles(theme), [theme]);
  const { isScanning, sections, startScan, stopScan, clearDevices } =
    useBleDevices();

  const handleDevicePress = useCallback(
    (device: BleDevice) => {
      if (!device.isBridger) return;

      router.push({
        pathname: PATHS.CONNECTION,
        params: { address: device.address, name: device.name },
      });
      stopScan();
    },
    [router, stopScan]
  );

  const renderItem = ({ item }: { item: BleDevice }) => {
    return <DeviceCard device={item} onPress={() => handleDevicePress(item)} />;
  };

  const renderEmptyList = () => (
    <Text style={styles.emptyListText}>
      {"No devices found yet.\nStart scan to find available devices."}
    </Text>
  );

  return (
    <View style={styles.container}>
      <Text variant="titleLarge" style={styles.title}>
        BLE Scanner
      </Text>

      <View style={styles.buttonContainer}>
        <Button
          style={styles.button}
          mode="contained"
          onPress={() =>
            isScanning ? stopScan() : startScan(extra.ADVERTISE_UUID)
          }
        >
          {isScanning ? "Stop Scan" : "  Start Scan"}
        </Button>
        <Button style={styles.button} mode="contained" onPress={clearDevices}>
          Clear Devices
        </Button>
      </View>

      {isScanning && (
        <View style={styles.scanningInfo}>
          <ActivityIndicator animating={true} />
          <Text style={styles.scanningText}>Scanning for devices...</Text>
        </View>
      )}

      <SectionList
        style={styles.list}
        sections={sections}
        keyExtractor={(item) => item.address}
        renderItem={renderItem}
        renderSectionHeader={({ section: { title } }) => (
          <Text variant="bodyMedium" style={styles.sectionHeader}>
            {title}
          </Text>
        )}
        showsVerticalScrollIndicator={false}
        ListEmptyComponent={renderEmptyList}
      />
    </View>
  );
}

const getStyles = (theme: ReturnType<typeof useTheme>) =>
  StyleSheet.create({
    container: {
      flex: 1,
      alignItems: "center",
      paddingHorizontal: 20,
      paddingTop: 20,
      backgroundColor: theme.colors.background,
    },
    title: {
      marginBottom: 20,
      color: theme.colors.onBackground,
    },
    buttonContainer: {
      flexDirection: "row",
      justifyContent: "center",
      width: "100%",
      marginBottom: 20,
    },
    button: {
      marginLeft: 10,
      marginRight: 10,
    },
    list: {
      width: "100%",
    },
    sectionHeader: {
      paddingTop: 10,
      marginBottom: 10,
      color: theme.colors.onBackground,
      fontWeight: "bold",
    },
    scanningInfo: {
      flexDirection: "row",
      alignItems: "center",
      marginBottom: 20,
    },
    emptyListText: {
      textAlign: "center",
      color: theme.colors.onSurfaceVariant,
      marginTop: 20,
    },
    scanningText: {
      marginLeft: 8,
      color: theme.colors.onSurface,
    },
  });
