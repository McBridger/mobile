import { useAppConfig } from "@/hooks/useConfig";
import { Item, useConnector } from "@/store/connection.store";
import {
  Stack,
  useFocusEffect,
  useLocalSearchParams,
  useRouter,
} from "expo-router";
import React, { useCallback, useEffect, useMemo } from "react";
import { Button, FlatList, StyleSheet, Text, View } from "react-native";
import { useShallow } from "zustand/react/shallow";

export default function Connection() {
  const router = useRouter();
  const { extra } = useAppConfig();
  const params = useLocalSearchParams<{ address: string }>();
  const [connect, disconnect] = useConnector(
    useShallow((state) => [
      state.connect,
      state.disconnect,
    ])
  );
  const status = useConnector((state) => state.status);
  const isConnected = useMemo(() => status === "connected", [status]);

  const _items = useConnector((state) => state.items);
  const items = useMemo(() => Array.from(_items.values()) as Item[], [_items]);

  const address = useMemo(() => params.address, [params.address]);

  useFocusEffect(
    useCallback(() => {
      if (isConnected) return;
      if (!address) return;
        
        connect(address, extra);
    }, [address, connect, extra, isConnected])
  );

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
              onPress={disconnect}
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
