import ConnectorModule, {
  Item,
  useConnector
} from "@/modules/connector";
import { useFocusEffect, useRouter } from "expo-router";
import React, { useCallback, useMemo } from "react";
import {
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import { useShallow } from "zustand/react/shallow";

export default function Connection() {
  const router = useRouter();

  const status = useConnector((state) => state.status);
  const isConnected = status === "connected";

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  const [disconnect] = useConnector(useShallow((state) => [state.disconnect]));

  // Ensure Magic Sync is active when we focus this screen
  useFocusEffect(
    useCallback(() => {
      console.log("[Connection] Screen focused, ensuring discovery is active.");
      ConnectorModule.startDiscovery();
    }, [])
  );

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
      <FlatList
        data={items}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={styles.list}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>
              {isConnected
                ? "No data synced yet. Try copying something on your Mac!"
                : "Waiting for connection to start syncing..."}
            </Text>
          </View>
        }
      />

      <TouchableOpacity
        style={[
          styles.actionButton,
          isConnected ? styles.disconnectButton : styles.setupButton,
        ]}
        onPress={() => (isConnected ? disconnect() : router.push("/setup"))}
      >
        <Text style={styles.actionButtonText}>
          {isConnected ? "Disconnect" : "Setup Mnemonic"}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f8f9fa",
  },
  list: {
    flex: 1,
  },
  listContent: {
    padding: 20,
    paddingBottom: 100,
  },
  clipboardItem: {
    backgroundColor: "#fff",
    padding: 15,
    borderRadius: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: "#eee",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  itemType: {
    fontSize: 12,
    fontWeight: "bold",
    color: "#007AFF",
    marginBottom: 4,
    textTransform: "uppercase",
  },
  itemContent: {
    fontSize: 16,
    color: "#1a1a1a",
    marginBottom: 8,
  },
  itemTimestamp: {
    fontSize: 11,
    color: "#bbb",
    textAlign: "right",
  },
  emptyContainer: {
    padding: 40,
    alignItems: "center",
  },
  emptyText: {
    textAlign: "center",
    color: "#999",
    fontSize: 14,
    lineHeight: 20,
  },
  actionButton: {
    position: "absolute",
    bottom: 30,
    alignSelf: "center",
    paddingHorizontal: 30,
    paddingVertical: 15,
    borderRadius: 30,
    elevation: 5,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  disconnectButton: {
    backgroundColor: "#FF3B30",
  },
  setupButton: {
    backgroundColor: "#007AFF",
  },
  actionButtonText: {
    color: "white",
    fontWeight: "bold",
    fontSize: 16,
  },
});
