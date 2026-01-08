import { SubscriptionManager } from "@/utils";
import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import {
  ConnectorModuleEvents,
  MessagePayload,
  STATUS,
} from "./src/Connector.types";
import ConnectorModule from "./src/ConnectorModule";

export * from "./src/Connector.types";

const log = console.log.bind(null, "[useConnector]");
const error = console.error.bind(null, "[useConnector]");

// --- Types, Constants, and Helpers ---

export class Item {
  id: string;
  type: "received" | "sent";
  content: string;
  time: number;

  static transform(payload: MessagePayload): Item  {
    return {
      id: payload.id,
      type: payload.address ? "received" : "sent",
      content: payload.value,
      time: payload.timestamp || Date.now(),
    }
  }
};

// --- State, Actions, and Store Definition ---

interface ConnectorState {
  status: `${STATUS}`;
  isReady: boolean;
  items: Map<string, Item>;
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

export const WORKING_STATUSES = new Set<`${STATUS}`>([
  STATUS.READY,
  STATUS.DISCOVERING,
  STATUS.CONNECTING,
  STATUS.CONNECTED,
  STATUS.DISCONNECTED,
]);

const getInitialStatus = () => ConnectorModule.getStatus() || STATUS.IDLE;
const getIsReady = (status: `${STATUS}`) => WORKING_STATUSES.has(status);

const initialState: ConnectorState = {
  status: getInitialStatus(),
  isReady: getIsReady(getInitialStatus()),
  items: new Map(),
};

export const useConnector = create<InternalConnectorStore>()(
  subscribeWithSelector((set, get) => {
    const handlers: ConnectorModuleEvents = {
      onReceived: (payload) => {
        log(`Native event: data received (ID: ${payload.id}).`);
        set((state) => {
          const newItems = new Map(state.items);
          newItems.set(payload.id, Item.transform(payload));
          return { items: newItems };
        });
      },
      onStateChanged: (payload) => {
        log(`Native event: state changed from ${get().status} to ${payload.status}.`);
        set({ 
          status: payload.status,
          isReady: getIsReady(payload.status)
        });
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

      loadHistory: async () => {
        try {
          const history = await ConnectorModule.getHistory();
          log(`Loaded ${history.length} items from history.`);

          set(() => {
            return {
              items: new Map(history.map((p) => [p.id, Item.transform(p)])),
            };
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
          set({ items: new Map() });
        } catch (err: any) {
          error("Reset failed:", err.message);
        }
      },

      subscribe: () => {
        get()[SubscriptionManager.KEY].setup();
        // Sync initial status and history from native
        const currentStatus = ConnectorModule.getStatus();
        log(`Initial native status: ${currentStatus}`);

        set({
          status: currentStatus || STATUS.IDLE,
          isReady: getIsReady(currentStatus || STATUS.IDLE),
        });

        get().loadHistory();
      },

      unsubscribe: () => {
        get()[SubscriptionManager.KEY].cleanup();
      },
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