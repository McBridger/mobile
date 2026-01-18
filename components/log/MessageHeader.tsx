import { Message } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import React from "react";
import { StyleSheet, View } from "react-native";
import { Text } from "react-native-paper";

export const MessageHeader = ({ item, theme }: { item: Message; theme: AppTheme }) => (
    <View style={styles.headerRow}>
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
                style={[styles.labelText, { color: theme.colors.onSurfaceVariant }]}
            >
                {!item.address ? "SENT" : "RECEIVED"}
            </Text>
        </View>
        <Text
            variant="labelMedium"
            style={[styles.timeText, { color: theme.colors.onSurfaceVariant }]}
        >
            {new Date(item.timestamp * (item.timestamp < 1e12 ? 1000 : 1)).toLocaleTimeString()}
        </Text>
    </View>
);

const styles = StyleSheet.create({
    headerRow: {
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 4,
    },
    labelBadge: {
        paddingHorizontal: 8,
        paddingVertical: 2,
        borderRadius: 6,
    },
    labelText: {
        letterSpacing: 0.5,
    },
    timeText: {
        fontSize: 12,
    },
});
