import { useSwipeToDeleteAnimation } from "@/hooks/useSwipeToDeleteAnimation";
import { Item } from "@/modules/connector";
import { Ionicons } from "@expo/vector-icons";
import React, { useCallback } from "react";
import { StyleSheet, TouchableHighlight, View } from "react-native";
import Swipeable from "react-native-gesture-handler/ReanimatedSwipeable";
import { Card, Text, useTheme } from "react-native-paper";
import Reanimated, {
  Extrapolation,
  interpolate,
  SharedValue,
  useAnimatedStyle,
  withTiming,
} from "react-native-reanimated";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";

type MessageItemProps = {
  item: Item;
  onSend: (text: string) => void;
  onSwipeToDelete: (item: Item) => void;
};

type SwipeableRightActionProps = {
  progress: SharedValue<number>;
  isSent: boolean;
  isMarkedForRemoval: SharedValue<boolean>;
};

const getItemMarginStyles = (isSent: boolean) => ({
  marginLeft: isSent ? "10%" : 0,
  marginRight: isSent ? 0 : "10%",
});

const SwipeableRightAction: React.FC<SwipeableRightActionProps> = ({
  progress,
  isSent,
  isMarkedForRemoval,
}) => {
  const theme = useTheme();

  const iconStyle = useAnimatedStyle(() => {
    const opacity = interpolate(
      progress.value,
      [0, 0.5],
      [0, 1],
      Extrapolation.CLAMP
    );
    const scale = interpolate(
      progress.value,
      [0, 1],
      [0.6, 1],
      Extrapolation.CLAMP
    );
    return { opacity, transform: [{ scale }] };
  });

  const containerStyle = useAnimatedStyle(() => {
    const initialOpacity = interpolate(
      progress.value,
      [0, 0.3],
      [0, 1],
      Extrapolation.CLAMP
    );
    return {
      opacity: withTiming(isMarkedForRemoval.value ? 0 : initialOpacity, {
        duration: 200,
      }),
    };
  });

  const actionContainerStyle = [
    styles.rightActionContainer,
    { backgroundColor: theme.colors.error },
    getItemMarginStyles(isSent),
  ];

  return (
    <Reanimated.View style={[actionContainerStyle, containerStyle]}>
      <Reanimated.View style={[styles.actionIconWrapper, iconStyle]}>
        <Ionicons name="trash" size={32} color={theme.colors.onError} />
      </Reanimated.View>
    </Reanimated.View>
  );
};

export const MessageItem: React.FC<MessageItemProps> = ({
  item,
  onSend,
  onSwipeToDelete,
}) => {
  const theme = useTheme();
  const isSent = item.type === "sent";

  const handleAnimationEnd = useCallback(() => {
    onSwipeToDelete(item);
  }, [item, onSwipeToDelete]);

  const {
    containerStyle,
    contentStyle,
    isMarkedForRemoval,
    triggerDelete,
    onLayoutHandler,
  } = useSwipeToDeleteAnimation({ onAnimationEnd: handleAnimationEnd });
  const rotationStyle = isSent
    ? { lineHeight: 16, transform: [{ rotate: "-45deg" }] }
    : { lineHeight: 16, transform: [{ rotate: "135deg" }] };

  return (
    <Reanimated.View style={containerStyle}>
      <View onLayout={onLayoutHandler}>
        <Swipeable
          onSwipeableOpen={triggerDelete}
          renderRightActions={(progress) => (
            <SwipeableRightAction
              progress={progress}
              isSent={isSent}
              isMarkedForRemoval={isMarkedForRemoval}
            />
          )}
          friction={1.2}
          overshootRight={true}
        >
          <Reanimated.View style={contentStyle}>
            <TouchableHighlight
              style={[styles.cardWrapper, getItemMarginStyles(isSent)]}
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
                    {isSent ? " Sent:" : " Received:"}
                  </Text>
                  <Text variant="bodyMedium">{item.content}</Text>
                  <Text variant="bodySmall" style={styles.timeStamp}>
                    {new Date(item.time).toLocaleTimeString()}
                  </Text>
                </Card.Content>
              </Card>
            </TouchableHighlight>
          </Reanimated.View>
        </Swipeable>
      </View>
    </Reanimated.View>
  );
};

const styles = StyleSheet.create({
  cardWrapper: {
    marginBottom: 10,
    borderRadius: 20,
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
