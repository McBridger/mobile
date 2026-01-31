import { TutorialTarget } from "@/components/TutorialTarget";
import { useSettingsStore } from "@/hooks/useSettingsStore";
import { useTutorialStore } from "@/hooks/useTutorialStore";
import { Item, STATUS, useConnector } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import { connectionTutorialItems } from "@/utils/tutorialConstants";
import { Redirect } from "expo-router";
import React, { useEffect, useMemo } from "react";
import { FlatList, StyleSheet, View } from "react-native";
import { Card, Text, useTheme } from "react-native-paper";

export default function Connection() {
  const status = useConnector((state) => state.status);
  const isReady = useConnector((state) => state.isReady);
  const isConnected = status === STATUS.CONNECTED;
  const theme = useTheme() as AppTheme;

  const { currentStep, isTutorialVisible, startTutorial } = useTutorialStore();
  const { hasSeenTutorial } = useSettingsStore();

  const isActivityTutorial = isTutorialVisible && currentStep === 1;
  const isQuickSendTutorial = isTutorialVisible && currentStep === 2;
  const isAnyListTutorial = isActivityTutorial || isQuickSendTutorial;

  useEffect(() => {
    if (!hasSeenTutorial && isReady) {
      const timer = setTimeout(() => startTutorial(), 1000);
      return () => clearTimeout(timer);
    }
  }, [hasSeenTutorial, isReady, startTutorial]);

  const _items = useConnector((state) => state.items);
  const items = useMemo(
    () => Array.from(_items.values()).sort((a, b) => b.time - a.time) as Item[],
    [_items]
  );

  if (!isReady) return <Redirect href="/setup" />;

  const renderItem = ({ item }: { item: Item }) => (
    <Card
      style={[styles.logCard, { borderColor: theme.colors.cardBorder }]}
      mode="elevated"
      elevation={1}
    >
      <Card.Content style={styles.cardContent}>
        <View style={styles.logInfo}>
          <View
            style={[
              styles.labelBadge,
              {
                backgroundColor: theme.dark
                  ? theme.colors.statusRipple
                  : theme.colors.statusRippleLight,
              },
            ]}
          >
            <Text
              variant="labelSmall"
              style={[
                styles.labelText,
                { color: theme.colors.onSurfaceVariant },
              ]}
            >
              {item.type === "sent" ? "SENT" : "RECEIVED"}
            </Text>
          </View>
          <Text variant="titleMedium" style={{ color: theme.colors.onSurface }}>
            {item.content}
          </Text>
        </View>
        <View style={styles.logMeta}>
          <Text
            variant="labelMedium"
            style={[styles.timeText, { color: theme.colors.onSurfaceVariant }]}
          >
            {new Date(item.time).toLocaleTimeString()}
          </Text>
        </View>
      </Card.Content>
    </Card>
  );

  const TutorialHeader = () => {
    if (!isAnyListTutorial) return null;

    return (
      <View style={{ paddingBottom: 12 }}>
        {isActivityTutorial && (
          <TutorialTarget name="list">
            {connectionTutorialItems.map((item) => (
              <React.Fragment key={item.id}>
                {renderItem({ item })}
              </React.Fragment>
            ))}
          </TutorialTarget>
        )}

        {isQuickSendTutorial && (
          <>
            {renderItem({ item: connectionTutorialItems[0] })}
            <TutorialTarget name="list_send">
              {renderItem({ item: connectionTutorialItems[1] })}
            </TutorialTarget>
          </>
        )}
      </View>
    );
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <FlatList
        data={isAnyListTutorial ? [] : items}
        renderItem={renderItem}
        ListHeaderComponent={TutorialHeader}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          !isAnyListTutorial ? (
            <View style={styles.emptyContainer}>
              <Text
                variant="titleMedium"
                style={[
                  styles.emptyText,
                  { color: theme.colors.onSurfaceVariant },
                ]}
              >
                {isConnected
                  ? "No data synced yet. Try copying something on your Mac"
                  : "Waiting for connection to start syncing..."}
              </Text>
            </View>
          ) : null
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  listContent: {
    padding: 20,
  },
  logCard: {
    borderRadius: 20,
    marginBottom: 12,
    borderWidth: 1,
  },
  cardContent: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  logInfo: {
    flex: 1,
    marginRight: 10,
  },
  labelBadge: {
    alignSelf: "flex-start",
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
    marginBottom: 8,
  },
  labelText: {
    letterSpacing: 0.5,
  },
  logMeta: {
    alignItems: "flex-end",
  },
  timeText: {
    marginTop: 4,
  },
  emptyContainer: {
    padding: 40,
    alignItems: "center",
  },
  emptyText: {
    textAlign: "center",
  },
});
