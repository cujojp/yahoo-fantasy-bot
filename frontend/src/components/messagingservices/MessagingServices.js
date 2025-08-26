import React from 'react'
import { Card, Table, Button, Input, Form, Space, message, Popconfirm, Tag, Tooltip, Select, Modal } from 'antd'
import { DeleteOutlined, PlusOutlined, CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined, SendOutlined } from '@ant-design/icons'

const { Option } = Select

class MessagingServices extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            messagingServices: [],
            loading: true,
            testModalVisible: false,
            testMessageLoading: false
        }

        this.formRef = React.createRef()
        this.testFormRef = React.createRef()
    }

    componentDidMount() {
        this.fetchMessagingServices()
    }

    fetchMessagingServices = () => {
        this.setState({ loading: true })
        fetch("/messagingServices")
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    messagingServices: result,
                    loading: false
                })
            },
                (error) => {
                    console.log(error)
                    message.error('Failed to load messaging services')
                    this.setState({ loading: false })
                })
    }

    mapToAlertName(name) {
        switch (name) {
            case 0: return "Discord"
            case 1: return "Slack"
            case 2: return "GroupMe"
            default: return "N/A"
        }
    }

    mapNameToValue(name) {
        switch (name) {
            case "Discord": return 0
            case "Slack": return 1
            case "GroupMe": return 2
            default: return -1
        }
    }

    addMessagingService = (values) => {
        const messagingServices = [...this.state.messagingServices]
        const newService = {
            service: this.mapNameToValue(values.name),
            url: values.webHookUrl
        }
        messagingServices.push(newService)

        fetch("/messagingServices", {
            method: "PUT",
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(messagingServices)
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error(`HTTP error! status: ${res.status}`)
                }
                return res.text()
            })
            .then(text => {
                if (!text) {
                    throw new Error("Empty response from server")
                }
                return JSON.parse(text)
            })
            .then((result) => {
                this.setState({
                    messagingServices: result
                })
                message.success('Messaging service added successfully')
                this.formRef.current.resetFields()
            })
            .catch((error) => {
                console.error('Failed to add messaging service:', error)
                message.error('Failed to add messaging service. Server error.')
            })
    }

    deleteMessagingService = (index) => {
        const messagingServices = [...this.state.messagingServices]
        messagingServices.splice(index, 1)

        fetch("/messagingServices", {
            method: "PUT",
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(messagingServices)
        })
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    messagingServices: result
                })
                message.success('Messaging service deleted successfully')
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to delete messaging service')
                })
    }

    getServiceStatus = (service) => {
        // This is a placeholder - you might want to implement actual status checking
        // For now, we'll assume all configured services are active
        return service.url ? 'active' : 'inactive'
    }

    sendTestMessage = (values) => {
        this.setState({ testMessageLoading: true })
        
        fetch("/testMessage", {
            method: "POST",
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ message: values.message || undefined })
        })
            .then(res => {
                if (!res.ok) {
                    throw new Error(`HTTP error! status: ${res.status}`)
                }
                return res.json()
            })
            .then((results) => {
                // Display results
                let successCount = 0
                let failureCount = 0
                let resultMessages = []
                
                Object.entries(results).forEach(([service, status]) => {
                    if (status === "Success") {
                        successCount++
                        resultMessages.push(`✅ ${service}: ${status}`)
                    } else {
                        failureCount++
                        resultMessages.push(`❌ ${service}: ${status}`)
                    }
                })
                
                Modal.info({
                    title: 'Test Message Results',
                    content: (
                        <div>
                            <p>Sent test message to {Object.keys(results).length} service(s):</p>
                            <div style={{ marginTop: 10 }}>
                                {resultMessages.map((msg) => (
                                    <div key={msg} style={{ marginBottom: 5 }}>{msg}</div>
                                ))}
                            </div>
                            {successCount > 0 && <p style={{ marginTop: 10, color: '#52c41a' }}>✅ {successCount} successful</p>}
                            {failureCount > 0 && <p style={{ color: '#ff4d4f' }}>❌ {failureCount} failed</p>}
                        </div>
                    ),
                    onOk: () => {
                        this.setState({ testModalVisible: false })
                        this.testFormRef?.current?.resetFields()
                    },
                })
                
                this.setState({ testMessageLoading: false })
            })
            .catch((error) => {
                console.error('Failed to send test message:', error)
                message.error('Failed to send test message. Server error.')
                this.setState({ testMessageLoading: false })
            })
    }

    showTestModal = () => {
        if (this.state.messagingServices.length === 0) {
            message.warning('Please add at least one messaging service before sending a test message.')
            return
        }
        this.setState({ testModalVisible: true })
    }

    render() {
        const columns = [
            {
                title: 'Service',
                dataIndex: 'service',
                key: 'service',
                render: (service) => (
                    <Space>
                        <span>{this.mapToAlertName(service)}</span>
                        {service === 0 && <Tag color="blue">Discord</Tag>}
                        {service === 1 && <Tag color="purple">Slack</Tag>}
                        {service === 2 && <Tag color="green">GroupMe</Tag>}
                    </Space>
                )
            },
            {
                title: 'Webhook URL',
                dataIndex: 'url',
                key: 'url',
                render: (url) => (
                    <Tooltip title={url}>
                        <span style={{ 
                            maxWidth: '300px', 
                            overflow: 'hidden', 
                            textOverflow: 'ellipsis',
                            display: 'inline-block'
                        }}>
                            {url}
                        </span>
                    </Tooltip>
                )
            },
            {
                title: 'Status',
                key: 'status',
                render: (_, record) => {
                    const status = this.getServiceStatus(record)
                    return status === 'active' ? (
                        <Tag icon={<CheckCircleOutlined />} color="success">Active</Tag>
                    ) : (
                        <Tag icon={<CloseCircleOutlined />} color="error">Inactive</Tag>
                    )
                }
            },
            {
                title: 'Action',
                key: 'action',
                render: (_, record, index) => (
                    <Popconfirm
                        title="Delete this messaging service?"
                        description="Are you sure to delete this messaging service?"
                        onConfirm={() => this.deleteMessagingService(index)}
                        okText="Yes"
                        cancelText="No"
                    >
                        <Button 
                            type="text" 
                            danger 
                            icon={<DeleteOutlined />}
                        />
                    </Popconfirm>
                ),
            },
        ]

        return (
            <Card 
                title={
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Space>
                            <span>Messaging Services</span>
                            <Tooltip title="Configure webhook URLs for different messaging platforms">
                                <InfoCircleOutlined style={{ color: '#1890ff' }} />
                            </Tooltip>
                        </Space>
                        <Button 
                            type="primary"
                            icon={<SendOutlined />}
                            onClick={this.showTestModal}
                            disabled={this.state.messagingServices.length === 0}
                        >
                            Send Test Message
                        </Button>
                    </div>
                }
            >
                <Table
                    columns={columns}
                    dataSource={this.state.messagingServices}
                    rowKey={(record, index) => index}
                    loading={this.state.loading}
                    pagination={false}
                    style={{ marginBottom: 24 }}
                />
                
                <Card type="inner" title="Add New Service">
                    <Form
                        ref={this.formRef}
                        layout="vertical"
                        onFinish={this.addMessagingService}
                    >
                        <Form.Item
                            name="name"
                            label="Service Type"
                            rules={[{ required: true, message: 'Please select a service type' }]}
                        >
                            <Select placeholder="Select a messaging service">
                                <Option value="Slack">Slack</Option>
                                <Option value="Discord">Discord</Option>
                                <Option value="GroupMe">GroupMe</Option>
                            </Select>
                        </Form.Item>
                        
                        <Form.Item
                            name="webHookUrl"
                            label="Webhook URL"
                            rules={[
                                { required: true, message: 'Please enter webhook URL' },
                                { type: 'url', message: 'Please enter a valid URL' }
                            ]}
                        >
                            <Input 
                                placeholder="https://hooks.slack.com/services/..." 
                                prefix={<InfoCircleOutlined />}
                            />
                        </Form.Item>
                        
                        <Form.Item>
                            <Button 
                                type="primary" 
                                htmlType="submit"
                                icon={<PlusOutlined />}
                            >
                                Add Service
                            </Button>
                        </Form.Item>
                    </Form>
                </Card>

                <Modal
                    title="Send Test Message"
                    open={this.state.testModalVisible}
                    onCancel={() => this.setState({ testModalVisible: false })}
                    footer={null}
                >
                    <Form
                        ref={this.testFormRef}
                        layout="vertical"
                        onFinish={this.sendTestMessage}
                    >
                        <Form.Item
                            name="message"
                            label="Custom Message (optional)"
                            help="Leave empty to send default test message"
                        >
                            <Input.TextArea 
                                placeholder="Enter a custom test message..."
                                rows={3}
                            />
                        </Form.Item>
                        
                        <Form.Item>
                            <Space>
                                <Button 
                                    type="primary" 
                                    htmlType="submit"
                                    icon={<SendOutlined />}
                                    loading={this.state.testMessageLoading}
                                >
                                    Send Test
                                </Button>
                                <Button 
                                    onClick={() => this.setState({ testModalVisible: false })}
                                >
                                    Cancel
                                </Button>
                            </Space>
                        </Form.Item>
                    </Form>
                </Modal>
            </Card>
        )
    }
}

export default MessagingServices