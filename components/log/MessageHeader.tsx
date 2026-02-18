import { Porter, PorterStatus } from "@/modules/connector";
import { AppTheme } from "@/theme/CustomTheme";
import React from "react";
import { StyleSheet, View } from "react-native";
import { Text } from "react-native-paper";

export const MessageHeader = ({ item, theme }: { item: Porter; theme: AppTheme }) => (
    <View style={styles.headerRow}>
        <View style={styles.badgeContainer}>
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
                    {item.isOutgoing ? "SENT" : "RECEIVED"}
                </Text>
            </View>
            
            {item.status !== PorterStatus.COMPLETED && (
                <View
                    style={[
                        styles.statusBadge,
                        {
                            backgroundColor: item.status === PorterStatus.ERROR 
                                ? theme.colors.errorContainer 
                                : theme.colors.secondaryContainer,
                        },
                    ]}
                >
                    <Text
                        variant="labelSmall"
                        style={{ 
                            color: item.status === PorterStatus.ERROR 
                                ? theme.colors.error 
                                : theme.colors.onSecondaryContainer 
                        }}
                    >
                        {item.status}
                    </Text>
                </View>
            )}
        </View>

        <Text
            variant="labelMedium"
            style={[styles.timeText, { color: theme.colors.onSurfaceVariant }]}
        >
            {new Date(item.timestamp * (item.timestamp < 1e12 ? 1000 : 1)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </Text>
    </View>
);

const styles = StyleSheet.create({
    headerRow: {
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 8,
    },
    badgeContainer: {
        flexDirection: "row",
        gap: 8,
    },
    labelBadge: {
        paddingHorizontal: 8,
        paddingVertical: 2,
        borderRadius: 6,
    },
    statusBadge: {
        paddingHorizontal: 8,
        paddingVertical: 2,
        borderRadius: 6,
    },
    labelText: {
        letterSpacing: 0.5,
        fontWeight: "bold",
    },
    timeText: {
        fontSize: 12,
    },
});
