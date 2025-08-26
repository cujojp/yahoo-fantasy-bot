import React from 'react'
import { Card, Table, Button, Input, Form, Space, message, Popconfirm, Tag, Tooltip, Select } from 'antd'
import { DeleteOutlined, PlusOutlined, CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined } from '@ant-design/icons'

const { Option } = Select

class MessagingServices extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            messagingServices: [],
            loading: true
        }

        this.formRef = React.createRef()
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
            case 0: return "Slack"
            case 1: return "Discord"
            case 2: return "GroupMe"
            default: return "N/A"
        }
    }

    mapNameToValue(name) {
        switch (name) {
            case "Slack": return 0
            case "Discord": return 1
            case "GroupMe": return 2
            default: return -1
        }
    }

    addMessagingService = (values) => {
        const messagingServices = [...this.state.messagingServices]
        const newService = {
            name: this.mapNameToValue(values.name),
            webHookUrl: values.webHookUrl
        }
        messagingServices.push(newService)

        fetch("/messagingServices", {
            method: "PUT",
            body: JSON.stringify(messagingServices)
        })
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    messagingServices: result
                })
                message.success('Messaging service added successfully')
                this.formRef.current.resetFields()
            },
                (error) => {
                    console.log(error)
                    message.error('Failed to add messaging service')
                })
    }

    deleteMessagingService = (index) => {
        const messagingServices = [...this.state.messagingServices]
        messagingServices.splice(index, 1)

        fetch("/messagingServices", {
            method: "PUT",
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
        return service.webHookUrl ? 'active' : 'inactive'
    }

    render() {
        const columns = [
            {
                title: 'Service',
                dataIndex: 'name',
                key: 'name',
                render: (name) => (
                    <Space>
                        <span>{this.mapToAlertName(name)}</span>
                        {name === 0 && <Tag color="purple">Slack</Tag>}
                        {name === 1 && <Tag color="blue">Discord</Tag>}
                        {name === 2 && <Tag color="green">GroupMe</Tag>}
                    </Space>
                )
            },
            {
                title: 'Webhook URL',
                dataIndex: 'webHookUrl',
                key: 'webHookUrl',
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
                    <Space>
                        <span>Messaging Services</span>
                        <Tooltip title="Configure webhook URLs for different messaging platforms">
                            <InfoCircleOutlined style={{ color: '#1890ff' }} />
                        </Tooltip>
                    </Space>
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
            </Card>
        )
    }
}

export default MessagingServices