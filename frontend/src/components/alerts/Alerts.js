import React from 'react'
import { Card, Table, Button, Select, Form, Space, Typography, Popconfirm, Row, Col, message } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'

const { Title, Link } = Typography
const { Option } = Select

const months = ["January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"]
const dayOfWeek = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]

class Alerts extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            alerts: [],
            loading: true
        }

        this.formRef = React.createRef()
    }

    componentDidMount() {
        this.fetchAlerts()
    }

    fetchAlerts = () => {
        this.setState({ loading: true })
        fetch("/alerts")
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    alerts: result,
                    loading: false
                })
            },
                (error) => {
                    console.log(error)
                    message.error('Failed to load alerts')
                    this.setState({ loading: false })
                })
    }

    mapToAlertName(alert) {
        switch (alert) {
            case 0: return "Score"
            case 1: return "Close Score"
            case 2: return "Standings"
            case 3: return "Matchup"
            default: return "N/A"
        }
    }

    mapToMonth(month) {
        if (month < 1 || month > 12) {
            return "N/A"
        }
        return months[month - 1]
    }

    mapToDayOfWeek(day) {
        if (day < 1 || day > 7) {
            return "N/A"
        }
        return dayOfWeek[day - 1]
    }

    formatMinute(minute) {
        if (minute < 10) {
            return `0${minute}`
        }
        return minute
    }

    addAlert = (values) => {
        const alerts = [...this.state.alerts]
        alerts.push(values)

        fetch("/alerts", {
            method: "PUT",
            body: JSON.stringify(alerts)
        })
        .then(res => res.json())
        .then((result) => {
            this.setState({
                alerts: result
            })
                message.success('Alert added successfully')
                this.formRef.current.resetFields()
        },
        (error) => {
            console.log(error)
                    message.error('Failed to add alert')
        })
    }

    deleteAlert = (index) => {
        const alerts = [...this.state.alerts]
        alerts.splice(index, 1)

        fetch("/alerts", {
            method: "PUT",
            body: JSON.stringify(alerts)
        })
        .then(res => res.json())
        .then((result) => {
            this.setState({
                alerts: result
            })
                message.success('Alert deleted successfully')
        },
        (error) => {
            console.log("Error mate!")
                    message.error('Failed to delete alert')
        })
    }

    render() {
        const columns = [
            {
                title: 'Type',
                dataIndex: 'type',
                key: 'type',
                render: (type) => this.mapToAlertName(type)
            },
            {
                title: 'Hour',
                dataIndex: 'hour',
                key: 'hour',
            },
            {
                title: 'Minute',
                dataIndex: 'minute',
                key: 'minute',
                render: (minute) => this.formatMinute(minute)
            },
            {
                title: 'Start Month',
                dataIndex: 'startMonth',
                key: 'startMonth',
                render: (month) => this.mapToMonth(month)
            },
            {
                title: 'End Month',
                dataIndex: 'endMonth',
                key: 'endMonth',
                render: (month) => this.mapToMonth(month)
            },
            {
                title: 'Day Of Week',
                dataIndex: 'dayOfWeek',
                key: 'dayOfWeek',
                render: (day) => this.mapToDayOfWeek(day)
            },
            {
                title: 'Action',
                key: 'action',
                render: (_, record, index) => (
                    <Popconfirm
                        title="Delete this alert?"
                        description="Are you sure to delete this alert?"
                        onConfirm={() => this.deleteAlert(index)}
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
                        <span>Alerts</span>
                        <Typography.Text type="secondary" style={{ fontSize: '14px' }}>
                            (all times are UTC | 
                            <Link href="https://www.timeanddate.com/worldclock/converter.html?iso=20200917T200000&p1=1440" target="_blank">
                                {' '}Time Converter
                            </Link>
                            )
                        </Typography.Text>
                    </Space>
                }
            >
                <Table
                    columns={columns}
                    dataSource={this.state.alerts}
                    rowKey={(record, index) => index}
                    loading={this.state.loading}
                    pagination={false}
                    style={{ marginBottom: 24 }}
                />
                
                <Card type="inner" title="Add New Alert">
                    <Form
                        ref={this.formRef}
                        layout="vertical"
                        onFinish={this.addAlert}
                    >
                        <Row gutter={16}>
                            <Col xs={24} sm={8} md={4}>
                                <Form.Item
                                    name="type"
                                    label="Type"
                                    rules={[{ required: true, message: 'Please select alert type' }]}
                                >
                                    <Select placeholder="Select type">
                                        {Array.from(Array(4).keys()).map(num => (
                                            <Option key={num} value={num}>
                                                {this.mapToAlertName(num)}
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                            
                            <Col xs={12} sm={8} md={4}>
                                <Form.Item
                                    name="hour"
                                    label="Hour"
                                    rules={[{ required: true, message: 'Please select hour' }]}
                                >
                                    <Select placeholder="Hour">
                                        {Array.from(Array(24).keys()).map(num => (
                                            <Option key={num} value={num}>{num}</Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                            
                            <Col xs={12} sm={8} md={4}>
                                <Form.Item
                                    name="minute"
                                    label="Minute"
                                    rules={[{ required: true, message: 'Please select minute' }]}
                                >
                                    <Select placeholder="Minute">
                                        {Array.from(Array(60).keys()).map(num => (
                                            <Option key={num} value={num}>{num}</Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                            
                            <Col xs={12} sm={8} md={4}>
                                <Form.Item
                                    name="startMonth"
                                    label="Start Month"
                                    rules={[{ required: true, message: 'Please select start month' }]}
                                >
                                    <Select placeholder="Start Month">
                                        {Array.from(Array(12).keys()).map(num => (
                                            <Option key={num + 1} value={num + 1}>
                                                {this.mapToMonth(num + 1)}
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                            
                            <Col xs={12} sm={8} md={4}>
                                <Form.Item
                                    name="endMonth"
                                    label="End Month"
                                    rules={[{ required: true, message: 'Please select end month' }]}
                                >
                                    <Select placeholder="End Month">
                                        {Array.from(Array(12).keys()).map(num => (
                                            <Option key={num + 1} value={num + 1}>
                                                {this.mapToMonth(num + 1)}
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                            
                            <Col xs={24} sm={8} md={4}>
                                <Form.Item
                                    name="dayOfWeek"
                                    label="Day Of Week"
                                    rules={[{ required: true, message: 'Please select day of week' }]}
                                >
                                    <Select placeholder="Day Of Week">
                                        {Array.from(Array(7).keys()).map(num => (
                                            <Option key={num + 1} value={num + 1}>
                                                {this.mapToDayOfWeek(num + 1)}
                                            </Option>
                                        ))}
                                    </Select>
                                </Form.Item>
                            </Col>
                        </Row>
                        
                        <Form.Item>
                            <Button 
                                type="primary" 
                                htmlType="submit"
                                icon={<PlusOutlined />}
                            >
                                Add Alert
                            </Button>
                        </Form.Item>
                    </Form>
                </Card>
            </Card>
        )
    }
}

export default Alerts