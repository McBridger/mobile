// modules/scanner/index.ts
import { SubscriptionManager } from "@/utils";
import { create, StoreApi } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import ScannerModule from "./src/ScannerModule";
import {
  DeviceFoundEventPayload,
  ScanFailedEventPayload,
  ScannerModuleEvents,
} from "./src/ScannerModule.types";

export * from "./src/ScannerModule.types";

// --- Типы, Константы и Хелперы (без изменений) ---

const BATCH_UPDATE_MS = 500;
const log = console.log.bind(null, "[useScanner]");
const error = console.error.bind(null, "[useScanner]");

export interface BleDevice extends DeviceFoundEventPayload {
  isBridger?: boolean;
}

interface ScannerState {
  isScanning: boolean;
  devices: Map<string, BleDevice>;
  bridges: Map<string, BleDevice>;
  scanError: ScanFailedEventPayload | null;
  advertiseUuid: string | null;
}

interface ScannerActions {
  start: (advertiseUuid: string | null) => void;
  stop: () => void;
  clear: () => void;
  clearScanError: () => void;
}

type ScannerStore = ScannerState & ScannerActions;
type ZustandSet = StoreApi<ScannerStore>["setState"];

type InternalScannerStore = ScannerStore & {
  [SubscriptionManager.KEY]: SubscriptionManager<ScannerModuleEvents>;
};

const initialState: ScannerState = {
  isScanning: false,
  devices: new Map(),
  bridges: new Map(),
  scanError: null,
  advertiseUuid: null,
};

/**
 * Manages batch updates for discovered devices to avoid overwhelming the state.
 * It pools device updates and flushes them to the Zustand store periodically.
 */
class BatchUpdater {
  private devicePool = new Map<string, BleDevice>();
  private bridgePool = new Map<string, BleDevice>();
  private timeoutId?: number | void;

  constructor(private readonly set: ZustandSet) {}

  /**
   * Adds a device to the appropriate pool and schedules a flush.
   * @param device The device to add.
   */
  public add(device: BleDevice): void {
    if (device.isBridger) {
      this.bridgePool.set(device.address, device);
    } else {
      this.devicePool.set(device.address, device);
    }

    // Schedule a flush if one isn't already scheduled
    if (!this.timeoutId) {
      this.timeoutId = setTimeout(() => this.flush(), BATCH_UPDATE_MS);
    }
  }

  /**
   * Immediately applies all pooled devices to the store and clears the pools.
   * @param finalState Optional state changes to apply with the flush.
   */
  public flush(finalState: Partial<ScannerState> = {}): void {
    if (this.timeoutId) this.timeoutId = clearTimeout(this.timeoutId);

    // Exit early if there's nothing to update
    if (
      this.devicePool.size === 0 &&
      this.bridgePool.size === 0 &&
      Object.keys(finalState).length === 0
    ) {
      return;
    }

    this.set((prev) => {
      const newDevices = new Map([...prev.devices, ...this.devicePool]);
      const newBridges = new Map([...prev.bridges, ...this.bridgePool]);

      // Clear pools after preparing the update
      this.devicePool.clear();
      this.bridgePool.clear();

      return {
        ...prev,
        devices: newDevices,
        bridges: newBridges,
        ...finalState,
      };
    });
  }
}

export const useScanner = create<InternalScannerStore>()(
  subscribeWithSelector((set, get) => {
    const batcher = new BatchUpdater(set);

    const handlers: ScannerModuleEvents = {
      onDeviceFound: (device) => {
        const { advertiseUuid } = get();
        const isBridger =
          !!advertiseUuid && device.services?.includes(advertiseUuid);
        batcher.add(Object.assign(device, { isBridger }));
      },
      onScanFailed: (payload) => {
        error(`Ошибка сканирования: ${payload.message} (Код: ${payload.code})`);
        batcher.flush({ isScanning: false, scanError: payload });
      },
      onScanStopped: () => {
        log("Нативное событие: сканирование остановлено.");
        batcher.flush({ isScanning: false });
      },
    };

    return {
      ...initialState,

      // Инициализируем наши внутренние сервисы
      [SubscriptionManager.KEY]: new SubscriptionManager(
        ScannerModule,
        handlers
      ),

      start: (advertiseUuid: string | null) => {
        set({ ...initialState, advertiseUuid });
        get()[SubscriptionManager.KEY].setup();

        ScannerModule.startScan()
          .then(() => {
            set({ isScanning: true });
            log("Сканирование инициировано.");
          })
          .catch(handlers.onScanFailed);
      },

      stop: () => {
        ScannerModule.stopScan().catch((err) => {
          error("Promise stopScan() был отклонен.", err);
          set({ scanError: err, isScanning: false });
        });
      },

      clear: () => {
        set({ devices: new Map(), bridges: new Map(), scanError: null });
      },

      clearScanError: () => set({ scanError: null }),
    };
  })
);



// Подписка для управления побочными эффектами остается такой же чистой
useScanner.subscribe(
  (state) => state.isScanning,
  (isScanning, prevIsScanning) => {
    if (!prevIsScanning) return;
    if (isScanning) return;

    useScanner.getState()[SubscriptionManager.KEY].cleanup();
  }
);

export default ScannerModule;
