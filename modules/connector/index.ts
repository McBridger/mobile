import { SubscriptionManager } from "@/utils";
import Value from "typebox/value";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import {
  BrokerState,
  ConnectorModuleEvents,
  EncryptionState,
  Porter,
} from "./src/Connector.types";
import ConnectorModule from "./src/ConnectorModule";

export * from "./src/Connector.types";

const log = console.log.bind(null, "[useConnector]");
const error = console.error.bind(null, "[useConnector]");

// --- State, Actions, and Store Definition ---

interface ConnectorState {
  state: BrokerState;
  isReady: boolean;
  items: Map<string, Porter>;
}

interface ConnectorActions {
  setup: (mnemonic: string, salt: string) => Promise<void>;
  send: (data: string) => void;
  reset: () => Promise<void>;

  loadHistory: () => Promise<void>;
  subscribe: () => void;
  unsubscribe: () => void;
}

type ConnectorStore = ConnectorState & ConnectorActions;

type InternalConnectorStore = ConnectorStore & {
  [SubscriptionManager.KEY]: SubscriptionManager<ConnectorModuleEvents>;
};

const initialState: ConnectorState = {
  state: Value.Create(BrokerState),
  isReady: false,
  items: new Map(),
};

export const useConnector = create<InternalConnectorStore>()(
  subscribeWithSelector((set, get) => {
    // Event Handlers
    
    // Total history refresh (triggered on add/remove/status change)
    const onHistoryChanged: ConnectorModuleEvents["onHistoryChanged"] = ({ items }) => {
      log(`Native event: history changed. Total items: ${items.length}`);
      set(() => {
        const newItems = new Map<string, Porter>();
        items.forEach(p => {
          if (Value.Check(Porter, p)) newItems.set(p.id, p);
        });
        return { items: newItems };
      });
    };

    // Hot delta update (progress/size/speed)
    const onPorterUpdated: ConnectorModuleEvents["onPorterUpdated"] = (payload) => {
      set((state) => {
        const porter = state.items.get(payload.id);
        if (!porter) return state; // Ignore updates for items not in current list

        const newItems = new Map(state.items);
        newItems.set(payload.id, {
          ...porter,
          progress: payload.progress,
          currentSize: payload.currentSize,
        });

        return { items: newItems };
      });
    };

    const onStateChanged: ConnectorModuleEvents["onStateChanged"] = (
      payload,
    ) => {
      log(
        `Native event: broker state updated. ${Object.entries(payload)
          .map(([k, v]) => `${k}: ${v.error ? `ERROR: ${v.error}` : v.current}`)
          .join(", ")}`,
      );
      set({
        state: payload,
        isReady: payload.encryption.current === EncryptionState.KEYS_READY,
      });
    };

    return {
      ...initialState,

      [SubscriptionManager.KEY]: new SubscriptionManager(ConnectorModule, {
        onHistoryChanged,
        onPorterUpdated,
        onStateChanged,
      }),

      setup: async (mnemonic: string, salt: string) => {
        log("Initiating setup via Broker.");
        try {
          await ConnectorModule.setup(mnemonic, salt);
        } catch (err: any) {
          error("Setup failed:", err.message);
        }
      },

      loadHistory: async () => {
        try {
          const history = await ConnectorModule.getHistory();
          log(`Loaded ${history.length} items from history.`);

          set(() => {
            const newItems = new Map<string, Porter>();
            history.forEach(p => {
              if (Value.Check(Porter, p)) newItems.set(p.id, p);
            });
            return { items: newItems };
          });
        } catch (err: any) {
          error("Failed to load history:", err.message);
        }
      },

      send: (data: string) => {
        ConnectorModule.send(data).catch((err) => {
          error("Promise send() was rejected.", err);
        });
      },

      reset: async () => {
        try {
          await ConnectorModule.reset();
          set({ ...initialState });
        } catch (err: any) {
          error("Reset failed:", err.message);
        }
      },

      subscribe: () => {
        get()[SubscriptionManager.KEY].setup();

        // Sync initial state from native
        try {
          const nativeState = ConnectorModule.getBrokerState();
          set({ 
            state: nativeState,
            isReady: nativeState.encryption.current === EncryptionState.KEYS_READY
          });
          log("Initial native BrokerState synced.");
        } catch {
          error("Failed to sync initial BrokerState");
        }

        get().loadHistory();
      },

      unsubscribe: () => {
        get()[SubscriptionManager.KEY].cleanup();
      },
    };
  }),
);

export default ConnectorModule;

export const BridgerService = {
  getHistory: async (): Promise<Porter[]> => {
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
