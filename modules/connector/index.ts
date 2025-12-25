import { AppConfig } from "@/app.config";
import { SubscriptionManager } from "@/utils";
import uuid from "react-native-uuid";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import {
  ConnectorModuleEvents,
} from "./src/Connector.types";
import ConnectorModule from "./src/ConnectorModule";

export * from "./src/Connector.types";

const log = console.log.bind(null, "[useConnector]");
const error = console.error.bind(null, "[useConnector]");

// --- Types, Constants, and Helpers ---

type ConnectionStatus =
  | "disconnected"
  | "connecting"
  | "connected"
  | "disconnecting";

export type Item = {
  id: string;
  type: "received" | "sent";
  content: string;
  time: number;
};

// --- State, Actions, and Store Definition ---

interface ConnectorState {
  status: ConnectionStatus;
  address: string | null;
  name: string | null;
  items: Map<string, Item>;
}

interface ConnectorActions {
  connect: (address: string, name: string, extra: AppConfig["extra"]) => void;
  disconnect: () => void;
  send: (data: string) => void;
  clearConnectionError: () => void;
  deleteItem: (data: string) => void;
  addItem: (data: Item) => void;
  clearItems: () => void;

  subscribe: () => void;
  unsubscribe: () => void;
}

type ConnectorStore = ConnectorState & ConnectorActions;

type InternalConnectorStore = ConnectorStore & {
  [SubscriptionManager.KEY]: SubscriptionManager<ConnectorModuleEvents>;
};

const initialState: ConnectorState = {
  status: "disconnected",
  address: null,
  name: null,
  items: new Map(),
  // connectionError: null,
};

export const useConnector = create<InternalConnectorStore>()(
  subscribeWithSelector((set, get) => {
    const handlers: ConnectorModuleEvents = {
      onConnected: () => {
        log("Native event: connected.");
        set({ status: "connected" });
      },
      onDisconnected: () => {
        log("Native event: disconnected.");
        set(initialState);
      },
      onReceived: (payload) => {
        log(`Native event: data received (ID: ${payload.id}).`);
        set((state) => {
          const newItems = new Map(state.items);
          newItems.set(payload.id, {
            ...payload,
            type: "received",
            content: payload.value,
            time: Date.now(),
          });
          return { items: newItems };
        });
      },
    };

    return {
      ...initialState,

      [SubscriptionManager.KEY]: new SubscriptionManager(
        ConnectorModule,
        handlers
      ),

      connect: async (
        address: string,
        name: string,
        extra: AppConfig["extra"]
      ) => {
        if (get().status !== "disconnected") {
          log("Connection in progress, ignoring connect request.");
          return;
        }

        set({ status: "connecting", address, name, items: new Map() });

        try {
          const isConnected = await ConnectorModule.isConnected();
          if (isConnected) return set({ status: "connected", address, name });

          await ConnectorModule.connect(address);
        } catch (err: any) {
          error(`Connection failed: ${err.message}`, err);
        }
      },

      disconnect: () => {
        if (get().status !== "connected") return;
        set({ status: "disconnecting" });

        ConnectorModule.disconnect().catch((err) => {
          error("Promise disconnect() was rejected.", err);
        })
      },

      send: (content: string) => {
        const item: Item = {
          id: uuid.v4(),
          content,
          type: "sent",
          time: Date.now(),
        };
        ConnectorModule.send(content)
          .then(() => {
            set((prev) => {
              const newItems = new Map(prev.items);
              newItems.set(item.id, item);

              return {
                ...prev,
                items: newItems,
              };
            });
          })
          .catch((err) => {
            error("Promise send() was rejected.", err);
          });
      },
       deleteItem: (itemId: string) => {
        set((prev) => {
          const newItems = new Map(prev.items);
          newItems.delete(itemId);
          return { items: newItems };
        });
      },
      
      clearItems: () => {
        set(() => {
          return { items: new Map() };
        });
      },

      addItem: (item: Item) => {
        set((prev) => {
          const newItems = new Map(prev.items);
          newItems.set(item.id, item);

          return {
            ...prev,
            items: newItems,
          };
        });
      },

      deleteAllItems: () => {},

      subscribe: () => {
        get()[SubscriptionManager.KEY].setup();
      },

      unsubscribe: () => {
        get()[SubscriptionManager.KEY].cleanup();
      }
    };
  })
);

useConnector.subscribe((state, prevState) => {
  console.debug("[BleStore] State changed:", JSON.stringify(state, null, 2));
});

export default ConnectorModule;

export const BridgerService = {
  getHistory: async (): Promise<string[]> => {
    try {
      const history = await ConnectorModule.getHistory();
      log(`Retrieved ${history.length} items from history.`);
      return history;
    } catch (err: any) {
      error("Failed to get history:", err.message);
      throw err;
    }
  },

  clearHistory: async (): Promise<void> => {
    try {
      await ConnectorModule.clearHistory();
      log("History cleared.");
    } catch (err: any) {
      error("Failed to clear history:", err.message);
      throw err;
    }
  },
};
