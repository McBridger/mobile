import { useBleConnector } from "@/hooks/useBleConnector";
import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import React, { useEffect, useMemo, useState } from "react";
import { Button, FlatList, StyleSheet, Text, View } from "react-native";

type Item = { id: string; type: string; content: string; timestamp: string };

export default function Connection() {
  const router = useRouter();
  const params = useLocalSearchParams<{ address: string }>();
  const [items, setItems] = useState<Item[]>([]);

  const { send, isConnected, connect, disconnect } = useBleConnector({
    onReceived: (data) =>
      setItems((prev) =>
        prev.concat({
          id: `${prev.length}`,
          type: "received",
          content: data,
          timestamp: new Date().toLocaleString(),
        })
      ),
  });

  const address = useMemo(() => params.address, [params.address]);

  useEffect(() => {
    if (!isConnected && address) connect(address);
  }, [address, connect, isConnected]);

  const handleDisconnect = () => {
    disconnect()
      .then(() => {
        console.log("Disconnected successfully");
        router.push('/devices');
      })
      .catch((error) => {
        console.error("Failed to disconnect:", error);
        // Optionally, show an alert to the user
      });
  };

  const renderItem = ({
    item,
  }: {
    item: { id: string; type: string; content: string; timestamp: string };
  }) => (
    <View style={styles.clipboardItem}>
      <Text style={styles.itemType}>
        {item.type === "sent" ? "Sent:" : "Received:"}
      </Text>
      <Text style={styles.itemContent}>{item.content}</Text>
      <Text style={styles.itemTimestamp}>{item.timestamp}</Text>
    </View>
  );

  return (
    <View style={styles.container}>
      <Stack.Screen
        options={{
          title: `${isConnected ? "Connected" : "Connecting"}`,
          headerRight: () => (
            <Button
              onPress={handleDisconnect}
              title="Disconnect"
              color="#FF3B30"
            />
          ),
        }}
      />
      <Text style={styles.title}>Connection Details</Text>
      <FlatList
        data={items.reverse()}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        style={styles.list}
      />
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
});
