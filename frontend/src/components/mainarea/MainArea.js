import React from 'react'
import { Typography, Button, Row, Col, Space, Spin } from 'antd'
import { LoginOutlined } from '@ant-design/icons'
import Alerts from '../alerts/Alerts'
import MessagingServices from '../messagingservices/MessagingServices'
import Leagues from '../leagues/Leagues'
import MessageType from '../messagetype/MessageType'

const { Title } = Typography

class MainArea extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            authenticated: false,
            loading: true
        }
    }

    componentDidMount() {
        fetch("/checkAuth")
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    authenticated: result.authenticated,
                    loading: false
                })
            },
                (error) => {
                    console.log("Error mate!")
                    this.setState({ loading: false })
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

        if (this.state.authenticated) {
            return (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Title level={2} style={{ textAlign: 'center' }}>Dashboard</Title>
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
                    </Row>
                </Space>
            )
        } else {
            return (
                <div style={{ textAlign: 'center', padding: '50px' }}>
                    <Space direction="vertical" size="large">
                        <Title level={3}>Authentication Required</Title>
                        <Button 
                            type="primary" 
                            size="large" 
                            icon={<LoginOutlined />}
                            href="/authenticate"
                        >
                            Authenticate with Yahoo
                        </Button>
                    </Space>
                </div>
            )
        }
    }
}

export default MainArea