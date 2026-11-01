import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueueProvider } from './context/QueueContext';
import { AppShell } from './components/AppShell';
import { RecepcaoPage } from './pages/RecepcaoPage';
import { AdminPage } from './pages/AdminPage';

export function App() {
  return (
    <QueueProvider>
      <BrowserRouter>
        <AppShell>
          <Routes>
            <Route path="/" element={<Navigate to="/recepcao" replace />} />
            <Route path="/recepcao" element={<RecepcaoPage />} />
            <Route path="/recepcao/:counterCode" element={<RecepcaoPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="*" element={<Navigate to="/recepcao" replace />} />
          </Routes>
        </AppShell>
      </BrowserRouter>
    </QueueProvider>
  );
}

export default App;
