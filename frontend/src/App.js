import React from 'react'
import { Layout, ConfigProvider } from 'antd'
import 'antd/dist/reset.css'
import './App.scss'
import ReleaseNotification from './components/releasenotification/ReleaseNotification'
import Header from './components/header/Header'
import MainArea from './components/mainarea/MainArea'

const { Header: AntHeader, Content } = Layout

function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          // Customize theme tokens here if needed
          colorPrimary: '#aa3b33',
          fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif",
        },
      }}
    >
      <Layout className="App" style={{ minHeight: '100vh' }}>
        <AntHeader style={{ background: '#fff', padding: '0 20px' }}>
          <Header />
        </AntHeader>
        <Content style={{ padding: '0 20px', marginTop: 16 }}>
          <ReleaseNotification />
          <MainArea />
        </Content>
      </Layout>
    </ConfigProvider>
  );
}

export default App
