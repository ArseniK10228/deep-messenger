import { useState } from 'react';
import { getToken } from './api/client';
import { CallProvider } from './call/CallContext';
import { CallOverlay } from './components/CallOverlay';
import { LoginView } from './components/LoginView';
import { MainApp } from './components/MainApp';

export default function App() {
  const [authed, setAuthed] = useState(() => Boolean(getToken()));

  if (!authed) {
    return <LoginView onLoggedIn={() => setAuthed(true)} />;
  }

  return (
    <CallProvider>
      <MainApp />
      <CallOverlay />
    </CallProvider>
  );
}
