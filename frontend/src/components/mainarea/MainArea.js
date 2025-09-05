import React from 'react'
import { Typography, Button, Row, Col, Space, Spin, Alert, Tag } from 'antd'
import { LoginOutlined, LogoutOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
import Alerts from '../alerts/Alerts'
import MessagingServices from '../messagingservices/MessagingServices'
import Leagues from '../leagues/Leagues'
import MessageType from '../messagetype/MessageType'
import MessageHistory from '../messagehistory/MessageHistory'
import ManualAlerts from '../manualalerts/ManualAlerts'

const { Title } = Typography

class MainArea extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            authenticated: false,
            loading: true,
            authRequired: false,
            environment: 'development'
        }
    }

    componentDidMount() {
        fetch("/checkAuth")
            .then(res => res.json())
            .then((result) => {
                console.log('Authentication status:', result)
                this.setState({
                    authenticated: result.authenticated || false,
                    authRequired: result.authRequired || false,
                    environment: result.environment || 'development',
                    loading: false
                })
            })
            .catch((error) => {
                console.error("Authentication check failed:", error)
                this.setState({ loading: false })
            })
    }

    handleLogout = () => {
        fetch("/logout", { method: "GET" })
            .then(() => {
                this.setState({
                    authenticated: false,
                    loading: false
                })
                // Optionally reload the page to clear any cached data
                window.location.reload()
            })
            .catch((error) => {
                console.error("Logout failed:", error)
            })
    }

    render() {
        if (this.state.loading) {
            return (
                <div style={{ textAlign: 'center', padding: '50px' }}>
                    <Spin size="large" />
                </div>
            )
        }

        if (this.state.authenticated || (!this.state.authRequired && !this.state.loading)) {
            return (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    {/* Header with environment info and logout */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <Title level={2} style={{ margin: 0 }}>Dashboard</Title>
                            <Tag 
                                color={this.state.environment === 'production' ? 'green' : 'blue'}
                                icon={<SafetyCertificateOutlined />}
                            >
                                {this.state.environment}
                            </Tag>
                            {this.state.authRequired && (
                                <Tag color="gold">Secure Mode</Tag>
                            )}
                        </div>
                        
                        {this.state.authenticated && (
                            <Button 
                                type="default" 
                                icon={<LogoutOutlined />}
                                onClick={this.handleLogout}
                                size="small"
                            >
                                Logout
                            </Button>
                        )}
                    </div>

                    {/* Security notice for production */}
                    {this.state.environment === 'production' && this.state.authRequired && (
                        <Alert
                            message="Secure Production Environment"
                            description="This application is running in production mode with Yahoo OAuth authentication enabled for enhanced security."
                            type="info"
                            showIcon
                            style={{ margin: '0 16px' }}
                        />
                    )}

                    {/* Development notice */}
                    {this.state.environment === 'development' && !this.state.authRequired && (
                        <Alert
                            message="Development Mode"
                            description="Authentication is optional in development mode. In production, Yahoo OAuth will be required."
                            type="warning"
                            showIcon
                            style={{ margin: '0 16px' }}
                        />
                    )}

                    <Row gutter={[16, 16]}>
                        <Col xs={24}>
                            <Alerts />
                        </Col>
                        <Col xs={24} md={12}>
                            <Leagues />
                        </Col>
                        <Col xs={24} md={12}>
                            <MessagingServices />
                        </Col>
                        <Col xs={24}>
                            <MessageType />
                        </Col>
                        <Col xs={24}>
                            <ManualAlerts />
                        </Col>
                        <Col xs={24}>
                            <MessageHistory />
                        </Col>
                    </Row>
                </Space>
            )
        } else {
            return (
                <div style={{ textAlign: 'center', padding: '50px', maxWidth: '600px', margin: '0 auto' }}>
                    <Space direction="vertical" size="large" style={{ width: '100%' }}>
                        <div>
                            <SafetyCertificateOutlined style={{ fontSize: '48px', color: '#1890ff', marginBottom: '16px' }} />
                            <Title level={2}>Secure Access Required</Title>
                        </div>
                        
                        {this.state.environment === 'production' ? (
                            <Alert
                                message="Production Environment"
                                description="This Yahoo Fantasy Bot instance requires secure authentication. Please login with your Yahoo account to access the dashboard and manage your fantasy league."
                                type="info"
                                showIcon
                            />
                        ) : (
                            <Alert
                                message="Development Environment"
                                description="Authentication is required to access the bot configuration. This protects your fantasy league data and messaging services."
                                type="warning"
                                showIcon
                            />
                        )}
                        
                        <div style={{ margin: '32px 0' }}>
                            <Button 
                                type="primary" 
                                size="large" 
                                icon={<LoginOutlined />}
                                href="/authenticate"
                                style={{ minWidth: '200px' }}
                            >
                                Login with Yahoo
                            </Button>
                        </div>
                        
                        <div style={{ fontSize: '14px', color: '#666', maxWidth: '400px' }}>
                            <p>
                                🔒 Your authentication is secured by Yahoo OAuth. 
                                We only access your fantasy league data and never store your Yahoo password.
                            </p>
                            <p>
                                After authentication, you'll be able to:
                            </p>
                            <ul style={{ textAlign: 'left', display: 'inline-block' }}>
                                <li>Configure messaging services (Discord, Slack, GroupMe)</li>
                                <li>Manage fantasy league alerts and notifications</li>
                                <li>Test SchefterBot message generation</li>
                                <li>View message history and statistics</li>
                            </ul>
                        </div>
                    </Space>
                </div>
            )
        }
    }
}

export default MainArea