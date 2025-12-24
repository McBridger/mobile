import { useAppConfig } from "@/hooks/useConfig";
import { Item, useConnector } from "@/modules/connector";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import React, { useCallback, useEffect, useMemo } from "react";
import {
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useShallow } from "zustand/react/shallow";

export default function Connection() {
  const router = useRouter();
  const { extra } = useAppConfig();

  const params = useLocalSearchParams<{ address: string; name: string }>();
  const address = useMemo(() => params.address, [params.address]);
  const name = useMemo(() => params.name, [params.name]);

  const status = useConnector((state) => state.status);
  const isConnected = useMemo(() => status === "connected", [status]);

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  const [
    connect,
    disconnect,
    // addRecorded
  ] = useConnector(
    useShallow((state) => [
      state.connect,
      state.disconnect,
      // state.addRecorded
    ])
  );

  // useFocusEffect(
  //   useCallback(() => {
  //     if (isConnected) return;
  //     if (!address) return;

  //     connect(address, name, extra);
  //     // bleRecorder.processEntries().then((entries) => addRecorded(entries));
  //   }, [address, connect, extra, isConnected, name])
  // );

  useEffect(() => {
    const unsub = useConnector.subscribe(
      (state) => state.status,
      (status, prevStatus) => {
        if (prevStatus === "disconnecting" && status === "disconnected")
          router.push("/devices");
      }
    );

    return () => {
      unsub();
    };
  }, [router]);

  const renderItem = ({ item }: { item: Item }) => (
    <View style={styles.clipboardItem}>
      <Text style={styles.itemType}>
        {item.type === "sent" ? "Sent:" : "Received:"}
      </Text>
      <Text style={styles.itemContent}>{item.content}</Text>
      <Text style={styles.itemTimestamp}>
        {new Date(item.time).toLocaleTimeString()}
      </Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Connection Details</Text>
      <FlatList
        data={items}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={styles.list}
      />
      <TouchableOpacity style={styles.disconnectButton} onPress={disconnect}>
        <Text style={styles.disconnectButtonText}>Disconnect</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: "#f0f0f0",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
    textAlign: "center",
  },
  clipboardItem: {
    backgroundColor: "#fff",
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
    elevation: 2,
  },
  itemType: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#555",
    marginBottom: 5,
  },
  itemContent: {
    fontSize: 16,
    marginBottom: 5,
  },
  itemTimestamp: {
    fontSize: 12,
    color: "#888",
    textAlign: "right",
  },
  list: {
    flex: 1,
  },
  disconnectButton: {
    position: "absolute",
    bottom: 30,
    right: 30,
    backgroundColor: "#FF3B30",
    padding: 15,
    borderRadius: 30,
    elevation: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
  },
  disconnectButtonText: {
    color: "white",
    fontWeight: "bold",
    fontSize: 16,
  },
});
