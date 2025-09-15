import { create } from "zustand";


export const useError = create<Error | null>(() => (null));

export const setError = (err: Error) => {
    useError.setState(err);
    console.error(err);
    setTimeout(() => useError.setState(null), 5000);
}