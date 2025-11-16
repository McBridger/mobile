import { useAppConfig } from "@/hooks/useConfig";
import { Item, useConnector } from "@/modules/connector";
import { Ionicons } from "@expo/vector-icons";
import * as Clipboard from "expo-clipboard";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import React, { useCallback, useEffect, useMemo } from "react";
import {
  useTheme,
  Appbar,
  Button,
  Card,
  Text,
  Snackbar,
} from "react-native-paper";

import {
  FlatList,
  StyleSheet,
  TouchableHighlight,
  TouchableOpacity,
  View,
} from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import Reanimated, {
  Extrapolation,
  SharedValue,
  interpolate,
  useAnimatedStyle,
} from "react-native-reanimated";
import Swipeable from "react-native-gesture-handler/ReanimatedSwipeable";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";

import { useShallow } from "zustand/react/shallow";
import { PATHS, Status } from "@/constants";

const SPACINGS = {
  BORDER_RADIUS: 20,
};

export default function Connection() {
  const router = useRouter();
  const { extra } = useAppConfig();
  const theme = useTheme();

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
    send,
    deleteItem,
    addItem,
    clearItems,
    // addRecorded
  ] = useConnector(
    useShallow((state) => [
      state.connect,
      state.disconnect,
      state.send,
      state.deleteItem,
      state.addItem,
      state.clearItems,
      // state.addRecorded
    ])
  );

  const styles = StyleSheet.create({
    container: {
      flex: 1,
      padding: 20,
      backgroundColor: theme.colors.background,
    },
    cardWrapper: {
      marginBottom: 10,
    },
    card: {
      backgroundColor: theme.colors.surfaceContainer,
    },
    timeStamp: {
      paddingTop: 8,
      textAlign: "right",
    },
    list: {
      flex: 1,
    },
    rightAction: {
      width: 50,
      height: "100%",
      verticalAlign: "middle",
      textAlign: "center",
      backgroundColor: "#f25d03",
    },
    bottomBar: {
      position: "absolute",
      left: 0,
      right: 0,
      bottom: 0,
      elevation: 4,
      height: 60,
      paddingHorizontal: 10,
      justifyContent: "center",
    },
    buttonContainer: {
      flexDirection: "row",
      justifyContent: "space-between",
      flex: 1,
    },
    button: {
      flex: 1,
      marginHorizontal: 5,
      backgroundColor: theme.colors.primaryFixed,
    },

    rightActionContainer: {
      backgroundColor: theme.colors.onError,
      flex: 1,
      justifyContent: "center",
      alignItems: "flex-end",
      paddingRight: 20,
      borderRadius: SPACINGS.BORDER_RADIUS,
      marginBottom: 10,
    },
    actionIconWrapper: {
      width: 30,
      height: "100%",
      justifyContent: "center",
      alignItems: "center",
    },
  });

  useFocusEffect(
    useCallback(() => {
      if (isConnected) return;
      if (!address) return;

      connect(address, name, extra);
      // bleRecorder.processEntries().then((entries) => addRecorded(entries));
    }, [address, connect, extra, isConnected, name])
  );

  useEffect(() => {
    const unsub = useConnector.subscribe(
      (state) => state.status,
      (status, prevStatus) => {
        if (prevStatus === Status.Disconnecting && status === Status.Disconnected)
          router.push(PATHS.DEVICES);
      }
    );

    return () => {
      unsub();
    };
  }, [router]);

  const [lastDeletedItem, setLastDeletedItem] = React.useState(null);
  const [showUndo, setShowUndo] = React.useState(false);

  const UndoSnackbar = ({ onUndo }) => (
    <Snackbar
      theme={{ colors: { primary: "green" } }}
      visible={showUndo}
      onDismiss={() => setShowUndo(false)}
      action={{
        label: "Undo",
        onPress: onUndo,
      }}
    >
      The entry has been deleted. Undo?
    </Snackbar>
  );

  const handleSendText = async (text: string) => {
    try {
      await Clipboard.setStringAsync(text);
      send(text);
    } catch (error) {
      console.error("Failed to copy to clipboard", error);
    }
  };

  const handleRemoveItem = useCallback(
    (item: Item) => {
      setLastDeletedItem(item);
      setShowUndo(true);
      deleteItem(item.id);

      const timer = setTimeout(() => {
        setShowUndo(false);
        setLastDeletedItem(null);
      }, 5000);

      return () => clearTimeout(timer);
    },
    [deleteItem]
  );

  const handleUndo = useCallback(() => {
    if (lastDeletedItem) {
      addItem(lastDeletedItem);
    }
    setShowUndo(false);
    setLastDeletedItem(null);
  }, [lastDeletedItem]);

  const handleSwipeOpen = useCallback(
    (direction: "left" | "right", item: Item) => {
      if (direction === "left") {
        handleRemoveItem(item);
      }
    },
    [handleRemoveItem]
  );

  const getItemMarginStyles = (isSent: boolean) => {
    return isSent
      ? { marginLeft: "10%", marginRight: 0 }
      : { marginRight: "10%", marginLeft: 0 };
  };

  const createRightAction =
    (isSent: boolean) =>
    (progress: SharedValue<number>, dragX: SharedValue<number>) => {
      const scale = useAnimatedStyle(() => {
        const currentOpacity = progress.value > 0.01 ? 1 : 0;

        // Анимация масштаба иконки
        const finalScale = interpolate(
          progress.value,
          [0, 1],
          [0.6, 1],
          Extrapolation.CLAMP
        );
        return {
          opacity: currentOpacity,
          transform: [{ scale: finalScale }],
        };
      });

      const actionContainerStyle = [
        styles.rightActionContainer,
        getItemMarginStyles(isSent),
      ];

      return (
        <View style={actionContainerStyle}>
          <Reanimated.View style={[styles.actionIconWrapper, scale]}>
            <Ionicons name="trash" size={32} color="white" />
          </Reanimated.View>
        </View>
      );
    };

  const renderItem = ({ item }: { item: Item; onRemove: void }) => {
    const isSent = item.type === "sent";

    const wrapperStyle = [styles.cardWrapper, getItemMarginStyles(isSent)];
    const rotationStyle = isSent
      ? { lineHeight: 16, transform: [{ rotate: "-45deg" }] }
      : { lineHeight: 16, transform: [{ rotate: "135deg" }] };
    return (
      <Swipeable
        renderRightActions={createRightAction(isSent)}
        friction={1.2}
        overshootRight={true}
        overshootFriction={0.5}
        overshootLeft={false}
        rightThreshold={50}
        enableTrackpadTwoFingerGesture
        onSwipeableOpen={(direction) => handleSwipeOpen(direction, item)}
      >
        <TouchableHighlight
          style={wrapperStyle}
          onPress={() => handleSendText(item.content)}
          onLongPress={() => handleSendText(item.content)}
          // underlayColor={isSent ? COLORS.SENT_BG : COLORS.RECEIVED_BG}
        >
          <Card style={styles.card}>
            <Card.Content>
              <Text
                variant="titleMedium"
                style={{ color: theme.colors.primary }}
              >
                <View style={rotationStyle}>
                  <Icon
                    name="send-outline"
                    size={16}
                    color={theme.colors.primary}
                    style={
                      !isSent && {
                        transform: [{ translateY: 1 }, { translateX: 6 }],
                      }
                    }
                  />
                </View>

                {isSent ? "Sent:" : "Received:"}
              </Text>

              <Text variant="bodyMedium">{item.content}</Text>
              <Text variant="bodySmall" style={styles.timeStamp}>
                {new Date(item.time).toLocaleTimeString()}
              </Text>
            </Card.Content>
          </Card>
        </TouchableHighlight>
      </Swipeable>
    );
  };
  return (
    <View style={styles.container}>
      <GestureHandlerRootView>
        <FlatList
          data={items}
          renderItem={renderItem}
          keyExtractor={(item) => item.id}
          style={styles.list}
        />
      </GestureHandlerRootView>
      <Appbar
        style={[styles.bottomBar, { backgroundColor: theme.colors.background }]}
      >
        <View style={styles.buttonContainer}>
          <Button
            style={styles.button}
            mode="contained-tonal"
            onPress={clearItems}
          >
            Remove all
          </Button>
          <Button
            mode="contained-tonal"
            style={styles.button}
            onPress={disconnect}
          >
            Disconnect
          </Button>
        </View>
      </Appbar>
      <UndoSnackbar onUndo={handleUndo} />
    </View>
  );
}
