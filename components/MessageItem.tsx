import { Item } from "@/modules/connector";
import { Ionicons } from "@expo/vector-icons";
import React from "react";
import { StyleSheet, TouchableHighlight, View } from "react-native";
import Swipeable from "react-native-gesture-handler/ReanimatedSwipeable";
import { Card, Text, useTheme } from "react-native-paper";
import Reanimated, {
    Extrapolation,
    SharedValue,
    interpolate,
    useAnimatedStyle,
} from "react-native-reanimated";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";

type MessageItemProps = {
  item: Item;
  onSend: (text: string) => void;
  onSwipeToDelete: (item: Item) => void;
};

const getItemMarginStyles = (isSent: boolean) => ({
  marginLeft: isSent ? "10%" : 0,
  marginRight: isSent ? 0 : "10%",
});

const createRightAction =
  (theme: ReturnType<typeof useTheme>, isSent: boolean) =>
  (progress: SharedValue<number>) => {
    const scale = useAnimatedStyle(() => ({
      opacity: progress.value > 0.01 ? 1 : 0,
      transform: [
        {
          scale: interpolate(
            progress.value,
            [0, 1],
            [0.6, 1],
            Extrapolation.CLAMP
          ),
        },
      ],
    }));

    const actionContainerStyle = [
      styles.rightActionContainer,
      { backgroundColor: theme.colors.error },
      getItemMarginStyles(isSent),
    ];

    return (
      <View style={actionContainerStyle}>
        <Reanimated.View style={[styles.actionIconWrapper, scale]}>
          <Ionicons name="trash" size={32} color={theme.colors.onError} />
        </Reanimated.View>
      </View>
    );
  };

export const MessageItem: React.FC<MessageItemProps> = React.memo(
  ({ item, onSend, onSwipeToDelete }) => {
    const theme = useTheme();
    const isSent = item.type === "sent";

    const wrapperStyle = [styles.cardWrapper, getItemMarginStyles(isSent)];
    const rotationStyle = isSent
      ? { transform: [{ rotate: "-45deg" }] }
      : { transform: [{ rotate: "135deg" }] };

    const handleSwipeOpen = (direction: "left" | "right") => {
      // Предполагаем, что свайп вправо (открытие левого экшена) или влево (открытие правого)
      // В нашем случае рендерится только rightAction
      onSwipeToDelete(item);
    };

    return (
      <Swipeable
        renderRightActions={createRightAction(theme, isSent)}
        friction={1.2}
        overshootRight={true}
        onSwipeableOpen={handleSwipeOpen}
      >
        <TouchableHighlight
          style={wrapperStyle}
          onPress={() => onSend(item.content)}
          underlayColor={theme.colors.surfaceVariant}
        >
          <Card style={{ backgroundColor: theme.colors.surfaceContainer }}>
            <Card.Content>
              <Text
                variant="titleMedium"
                style={{
                  color: theme.colors.primary,
                  display: "flex",
                  alignItems: "center",
                }}
              >
                <Icon
                  name="send-outline"
                  size={16}
                  color={theme.colors.primary}
                  style={rotationStyle}
                />
                {isSent ? " Sent:" : " Received:"}
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
  }
);

const styles = StyleSheet.create({
  cardWrapper: {
    marginBottom: 10,
  },
  timeStamp: {
    paddingTop: 8,
    textAlign: "right",
  },
  rightActionContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "flex-end",
    paddingRight: 20,
    borderRadius: 20,
    marginBottom: 10,
  },
  actionIconWrapper: {
    width: 30,
    height: "100%",
    justifyContent: "center",
    alignItems: "center",
  },
});
