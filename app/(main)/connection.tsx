import { Item, STATUS, useConnector } from "@/modules/connector";
import { Redirect } from "expo-router";
import React, { useMemo } from "react";
import {
  FlatList,
  StyleSheet,
  Text,
  View
} from "react-native";

export default function Connection() {
  const status = useConnector((state) => state.status);
  const isReady = useConnector((state) => state.isReady);
  const isConnected = status === STATUS.CONNECTED;

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  // Guard: If not ready, go to setup
  if (!isReady) return <Redirect href="/setup" />;

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
    paddingBottom: 40,
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
});