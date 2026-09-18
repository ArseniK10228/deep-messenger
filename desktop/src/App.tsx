import { useState } from 'react';
import { getToken } from './api/client';
import { CallProvider } from './call/CallContext';
import { CallOverlay } from './components/CallOverlay';
import { LoginView } from './components/LoginView';
import { MainApp } from './components/MainApp';
import { UpdateBanner } from './components/UpdateBanner';

export default function App() {
  const [authed, setAuthed] = useState(() => Boolean(getToken()));

  const shell = (
    <>
      <UpdateBanner />
      {authed ? (
        <CallProvider>
          <MainApp />
          <CallOverlay />
        </CallProvider>
      ) : (
        <LoginView onLoggedIn={() => setAuthed(true)} />
      )}
    </>
  );

  return shell;
}
