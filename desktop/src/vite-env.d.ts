/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_APP_VERSION: string;
  readonly VITE_APP_VERSION_CODE: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

interface DeepDesktopApi {
  platform: string;
  openChatFile?: (url: string, fileName: string, authToken?: string | null) => Promise<{ path: string }>;
  checkForUpdates?: () => Promise<unknown>;
  installUpdate?: () => Promise<unknown>;
  onUpdateStatus?: (listener: (payload: unknown) => void) => () => void;
}

interface Window {
  deepDesktop?: DeepDesktopApi;
}
