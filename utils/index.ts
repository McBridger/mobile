export class SubscriptionManager<
  H extends Record<string, (...args: any[]) => void>
> {
  static readonly KEY: unique symbol = Symbol("SubscriptionManager");

  #subscriptions: Subscription[] = [];

  constructor(
    private readonly target: {
      addListener: (key: keyof H, handler: H[keyof H]) => Subscription;
    },
    private readonly handlers: H
  ) {}

  setup() {
    this.cleanup();

    const eventKeys = Object.keys(this.handlers) as (keyof H)[];
    this.#subscriptions = eventKeys.map((key) =>
      this.target.addListener(key, this.handlers[key])
    );
  }

  cleanup() {
    if (this.#subscriptions.length === 0) return;
    this.#subscriptions.forEach((sub) => sub.remove());
    this.#subscriptions = [];
  }
}

type Subscription = {
  remove: () => void;
};
