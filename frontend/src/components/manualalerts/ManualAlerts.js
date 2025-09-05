import React from 'react'
import { Card, Button, Space, message, Divider, Alert } from 'antd'
import { 
    SendOutlined, 
    TrophyOutlined, 
    BarChartOutlined, 
    DashboardOutlined,
    FireOutlined 
} from '@ant-design/icons'

class ManualAlerts extends React.Component {
    constructor(props) {
        super(props)
        this.state = {
            loading: {
                matchup: false,
                standings: false,
                score: false,
                closescore: false
            }
        }
    }

    handleAlertTrigger = async (alertType) => {
        this.setState(prevState => ({
            loading: { ...prevState.loading, [alertType]: true }
        }))

        try {
            const response = await fetch(`/manual/${alertType}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            })

            const data = await response.json()

            if (response.ok) {
                message.success(`${this.getAlertTypeName(alertType)} alert sent successfully!`)
                
                // Show results in console for debugging
                console.log(`${alertType} results:`, data)
                
                // Show messaging service results
                if (data.results) {
                    const failedServices = Object.entries(data.results)
                        .filter(([_, result]) => !result.includes('Success'))
                    
                    if (failedServices.length > 0) {
                        message.warning(`Some services failed: ${failedServices.map(([service]) => service).join(', ')}`)
                    }
                }
            } else {
                // Show more detailed error message
                const errorMsg = data.error || `Failed to send ${this.getAlertTypeName(alertType)} alert`
                message.error(errorMsg, 10) // Show for 10 seconds
                
                // Log full error for debugging
                console.error(`${alertType} alert error:`, data)
            }
        } catch (error) {
            console.error(`Error triggering ${alertType} alert:`, error)
            message.error(`Error: ${error.message}`)
        } finally {
            this.setState(prevState => ({
                loading: { ...prevState.loading, [alertType]: false }
            }))
        }
    }

    getAlertTypeName = (type) => {
        const names = {
            matchup: 'Matchup',
            standings: 'Standings',
            score: 'Score',
            closescore: 'Close Score'
        }
        return names[type] || type
    }

    render() {
        return (
            <Card 
                title={
                    <Space>
                        <SendOutlined />
                        Manual Alert Testing
                    </Space>
                }
                extra={
                    <Alert 
                        message="Debug Tool" 
                        type="warning" 
                        showIcon 
                        style={{ padding: '4px 12px' }}
                    />
                }
            >
                <Alert
                    message="Manual Alert Triggers"
                    description="Use these buttons to manually send alerts to your configured messaging services. This is useful for testing your alert setup and debugging issues."
                    type="info"
                    showIcon
                    style={{ marginBottom: 16 }}
                />

                <Space direction="vertical" style={{ width: '100%' }} size="large">
                    <div>
                        <h4 style={{ marginBottom: 12 }}>Matchup Alerts</h4>
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<TrophyOutlined />}
                                onClick={() => this.handleAlertTrigger('matchup')}
                                loading={this.state.loading.matchup}
                                size="large"
                            >
                                Send Matchup Alert
                            </Button>
                            <span style={{ color: '#666', fontSize: '12px' }}>
                                Shows current week matchup with projected points
                            </span>
                        </Space>
                    </div>

                    <Divider />

                    <div>
                        <h4 style={{ marginBottom: 12 }}>Score Alerts</h4>
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<DashboardOutlined />}
                                onClick={() => this.handleAlertTrigger('score')}
                                loading={this.state.loading.score}
                                size="large"
                            >
                                Send Score Alert
                            </Button>
                            <Button
                                type="primary"
                                danger
                                icon={<FireOutlined />}
                                onClick={() => this.handleAlertTrigger('closescore')}
                                loading={this.state.loading.closescore}
                                size="large"
                            >
                                Send Close Score Alert
                            </Button>
                            <span style={{ color: '#666', fontSize: '12px' }}>
                                Shows current scores (close score only if within 40% win probability)
                            </span>
                        </Space>
                    </div>

                    <Divider />

                    <div>
                        <h4 style={{ marginBottom: 12 }}>Standings Alert</h4>
                        <Space wrap>
                            <Button
                                type="primary"
                                icon={<BarChartOutlined />}
                                onClick={() => this.handleAlertTrigger('standings')}
                                loading={this.state.loading.standings}
                                size="large"
                            >
                                Send Standings Alert
                            </Button>
                            <span style={{ color: '#666', fontSize: '12px' }}>
                                Shows current league standings with records
                            </span>
                        </Space>
                    </div>
                </Space>

                <Alert
                    message="Note"
                    description="These manual triggers bypass the scheduled alert system and send messages immediately. Check your messaging services (Discord, Slack, GroupMe) to verify the messages are received."
                    type="warning"
                    showIcon
                    style={{ marginTop: 24 }}
                />
            </Card>
        )
    }
}

export default ManualAlerts
