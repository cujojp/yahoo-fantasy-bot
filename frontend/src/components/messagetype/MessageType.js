import React from 'react'
import { Card, Select, Button, Space, message, Typography, Tag, Alert } from 'antd'
import { SaveOutlined, MessageOutlined } from '@ant-design/icons'

const { Option } = Select
const { Title, Text } = Typography

class MessageType extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            typeDisplay: -1,
            type: null,
            loading: true
        }
    }

    componentDidMount() {
        this.fetchMessageType()
    }

    fetchMessageType = () => {
        this.setState({ loading: true })
        fetch("/messageType")
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    typeDisplay: result.type,
                    loading: false
                })
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to load message type')
                    this.setState({ loading: false })
                })
    }

    mapToMessageName(messageType) {
        switch (messageType) {
            case 0: return "Individual"
            case 1: return "Batch"
            default: return "N/A"
        }
    }

    handleMessageType = (value) => {
        this.setState({
            type: value
        })
    }

    setMessageType = () => {
        if (this.state.type === null) {
            message.warning('Please select a message type')
            return
        }

        fetch("/messageType", {
            method: "PUT",
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                type: this.state.type
            })
        })
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    typeDisplay: result.type,
                    type: null
                })
                message.success('Message type updated successfully')
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to update message type')
                })
    }

    getMessageTypeTag = (type) => {
        const name = this.mapToMessageName(type)
        if (name === "Individual") {
            return <Tag color="blue" icon={<MessageOutlined />}>{name}</Tag>
        } else if (name === "Batch") {
            return <Tag color="green" icon={<MessageOutlined />}>{name}</Tag>
        }
        return <Tag>{name}</Tag>
    }

    render() {
        return (
            <Card 
                title="Message Type"
                loading={this.state.loading}
            >
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Alert
                        message="Current Message Type"
                        description={
                            <Space align="center">
                                <Text>Messages are currently sent as:</Text>
                                <Title level={4} style={{ margin: 0 }}>
                                    {this.getMessageTypeTag(this.state.typeDisplay)}
                                </Title>
                            </Space>
                        }
                        type="info"
                        showIcon
                    />

                    <Card type="inner" title="Change Message Type">
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <Select
                                placeholder="Select message type"
                                onChange={this.handleMessageType}
                                value={this.state.type}
                                style={{ width: '100%' }}
                            >
                                {Array.from(Array(2).keys()).map(num => (
                                    <Option key={num} value={num}>
                                        {this.mapToMessageName(num)}
                                    </Option>
                                ))}
                            </Select>
                            
                            <Button 
                                type="primary" 
                                icon={<SaveOutlined />}
                                onClick={this.setMessageType}
                                disabled={this.state.type === null}
                            >
                                Set Type
                            </Button>
                        </Space>
                    </Card>
                    
                    <Alert
                        message="Message Type Explanation"
                        description={
                            <>
                                <p><strong>Individual:</strong> Each alert sends a separate message for each league member.</p>
                                <p><strong>Batch:</strong> All alerts are combined into a single message for all league members.</p>
                            </>
                        }
                        type="info"
                    />
                </Space>
            </Card>
        )
    }
}

export default MessageType