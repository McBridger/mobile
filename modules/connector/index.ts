import { AppConfig } from "@/app.config";
import { SubscriptionManager } from "@/utils";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import {
  ConnectorModuleEvents,
  BrokerStatus,
  MessagePayload
} from "./src/Connector.types";
import ConnectorModule from "./src/ConnectorModule";

export * from "./src/Connector.types";

const log = console.log.bind(null, "[useConnector]");
const error = console.error.bind(null, "[useConnector]");

// --- Types, Constants, and Helpers ---

export type Item = {
  id: string;
  type: "received" | "sent";
  content: string;
  time: number;
};

// --- State, Actions, and Store Definition ---

interface ConnectorState {
  brokerStatus: BrokerStatus;
  items: Map<string, Item>;
}

interface ConnectorActions {
  setup: (mnemonic: string, salt: string) => Promise<void>;
  disconnect: () => void;
  send: (data: string) => void;

  subscribe: () => void;
  unsubscribe: () => void;
}

type ConnectorStore = ConnectorState & ConnectorActions;

type InternalConnectorStore = ConnectorStore & {
  [SubscriptionManager.KEY]: SubscriptionManager<ConnectorModuleEvents>;
};

const initialState: ConnectorState = {
  brokerStatus: "idle",
  items: new Map(),
};

export const useConnector = create<InternalConnectorStore>()(
  subscribeWithSelector((set, get) => {
    const handlers: ConnectorModuleEvents = {
      onConnected: () => {
        log("Native event: connected.");
      },
      onDisconnected: () => {
        log("Native event: disconnected.");
      },
      onReceived: (payload) => {
        log(`Native event: data received (ID: ${payload.id}).`);
        set((state) => {
          const newItems = new Map(state.items);
          newItems.set(payload.id, {
            id: payload.id,
            type: "received",
            content: payload.value,
            time: payload.time || Date.now(),
          });
          return { items: newItems };
        });
      },
      onStateChanged: (payload) => {
        log(`Native event: state changed to ${payload.status}.`);
        set({ brokerStatus: payload.status });
      }
    };

    return {
      ...initialState,

      [SubscriptionManager.KEY]: new SubscriptionManager(
        ConnectorModule,
        handlers
      ),

      setup: async (mnemonic: string, salt: string) => {
        log("Initiating setup via Broker.");
        try {
          await ConnectorModule.setup(mnemonic, salt);
        } catch (err: any) {
          error("Setup failed:", err.message);
        }
      },

      disconnect: () => {
        ConnectorModule.disconnect().catch((err) => {
          error("Promise disconnect() was rejected.", err);
        })
      },

      send: (data: string) => {
        ConnectorModule.send(data).catch((err) => {
          error("Promise send() was rejected.", err);
        });
      },

      subscribe: () => {
        get()[SubscriptionManager.KEY].setup();
        // Sync initial status from native
        const currentStatus = ConnectorModule.getStatus();
        log(`Initial native status: ${currentStatus}`);
        set({ brokerStatus: currentStatus });
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
  getHistory: async (): Promise<MessagePayload[]> => {
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