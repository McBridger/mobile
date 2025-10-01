import { useAppConfig } from "@/hooks/useConfig";
import { useScanner } from "@/modules/scanner";
import { useEffect } from "react";
import { FlatList, Text, View } from "react-native";
import { useShallow } from "zustand/shallow";

export default function Index() {
  const {
    extra: { ADVERTISE_UUID },
  } = useAppConfig();
  const isScanning = useScanner((state) => state.isScanning);
  const { devices, bridgers } = useScanner(
    useShallow((state) => ({
      devices: state.devices,
      bridgers: state.bridges,
    }))
  );
  const [start, stop, clear] = useScanner(
    useShallow((state) => [state.start, state.stop, state.clear])
  );

  useEffect(() => {
    start(ADVERTISE_UUID); // Начинаем сканирование при монтировании компонента

    return () => {
      stop();
      clear();
    };
  }, [start, stop, clear, ADVERTISE_UUID]);

  const allDevices = Array.from(devices.values()).concat(
    Array.from(bridgers.values())
  );

  return (
    <View
      style={{
        flex: 1,
        justifyContent: "center",
        alignItems: "center",
        padding: 20,
      }}
    >
      <Text style={{ fontSize: 24, marginBottom: 20 }}>Bluetooth Scanner</Text>
      {isScanning && allDevices.length === 0 ? (
        <Text>Scanning for devices...</Text>
      ) : (
        <FlatList
          data={allDevices}
          keyExtractor={(item) => item.address}
          renderItem={({ item }) => (
            <View
              style={{
                padding: 10,
                borderBottomWidth: 1,
                borderBottomColor: "#ccc",
              }}
            >
              <Text style={{ fontWeight: "bold" }}>
                Name: {item.name || "N/A"} {item.isBridger ? "(Bridger)" : ""}
              </Text>
              <Text>Address: {item.address}</Text>
              <Text>RSSI: {item.rssi}</Text>
              <Text>Services: {item.services?.join(", ") || "N/A"}</Text>
            </View>
          )}
        />
      )}
      <View style={{ flexDirection: "row", marginTop: 20 }}>
        <Text
          onPress={() => (isScanning ? stop() : start(ADVERTISE_UUID))}
          style={{
            backgroundColor: isScanning ? "orange" : "green",
            color: "white",
            padding: 10,
            marginRight: 10,
            borderRadius: 5,
          }}
        >
          {isScanning ? "Stop Scan" : "Start Scan"}
        </Text>
        <Text
          onPress={clear}
          style={{
            backgroundColor: "red",
            color: "white",
            padding: 10,
            borderRadius: 5,
          }}
        >
          Clear Devices
        </Text>
      </View>
    </View>
  );
}
