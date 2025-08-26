import React from 'react'
import { Card, Table, Button, Input, Form, Select, Space, message, Popconfirm, Tag } from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'

const { Option } = Select

class Leagues extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            leagues: [],
            loading: true
        }

        this.formRef = React.createRef()
    }

    componentDidMount() {
        this.fetchLeagues()
    }

    fetchLeagues = () => {
        this.setState({ loading: true })
        fetch("/leagues")
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    leagues: result,
                    loading: false
                })
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to load leagues')
                    this.setState({ loading: false })
                })
    }

    mapToGameKey(gameKey) {
        switch (gameKey) {
            case 0: return "NFL"
            case 1: return "MLB"
            case 2: return "NBA"
            default: return "N/A"
        }
    }

    addLeague = (values) => {
        const leagues = [...this.state.leagues]
        leagues.push({
            leagueId: values.leagueId,
            gameKey: values.gameKey
        })

        fetch("/leagues", {
            method: "PUT",
            body: JSON.stringify(leagues)
        })
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    leagues: result
                })
                message.success('League added successfully')
                this.formRef.current.resetFields()
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to add league')
                })
    }

    deleteLeague = (index) => {
        const leagues = [...this.state.leagues]
        leagues.splice(index, 1)

        fetch("/leagues", {
            method: "PUT",
            body: JSON.stringify(leagues)
        })
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    leagues: result
                })
                message.success('League deleted successfully')
            },
                (error) => {
                    console.log("Error mate!")
                    message.error('Failed to delete league')
                })
    }

    getGameKeyTag = (gameKey) => {
        const colorMap = {
            'NFL': 'blue',
            'MLB': 'red',
            'NBA': 'orange'
        }
        return <Tag color={colorMap[gameKey] || 'default'}>{gameKey}</Tag>
    }

    render() {
        const columns = [
            {
                title: 'League ID',
                dataIndex: 'leagueId',
                key: 'leagueId',
            },
            {
                title: 'Game Key',
                dataIndex: 'gameKey',
                key: 'gameKey',
                render: (gameKey) => this.getGameKeyTag(gameKey)
            },
            {
                title: 'Action',
                key: 'action',
                render: (_, record, index) => (
                    <Popconfirm
                        title="Delete this league?"
                        description="Are you sure to delete this league?"
                        onConfirm={() => this.deleteLeague(index)}
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
            <Card title="Leagues">
                <Table
                    columns={columns}
                    dataSource={this.state.leagues}
                    rowKey={(record, index) => index}
                    loading={this.state.loading}
                    pagination={false}
                    style={{ marginBottom: 24 }}
                />
                
                <Card type="inner" title="Add New League">
                    <Form
                        ref={this.formRef}
                        layout="vertical"
                        onFinish={this.addLeague}
                    >
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <Form.Item
                                name="leagueId"
                                label="League ID"
                                rules={[{ required: true, message: 'Please enter league ID' }]}
                            >
                                <Input placeholder="Enter your league ID" />
                            </Form.Item>
                            
                            <Form.Item
                                name="gameKey"
                                label="Game Key"
                                rules={[{ required: true, message: 'Please select game key' }]}
                            >
                                <Select placeholder="Select game type">
                                    {Array.from(Array(3).keys()).map(num => {
                                        const val = this.mapToGameKey(num)
                                        return <Option key={val} value={val}>{val}</Option>
                                    })}
                                </Select>
                            </Form.Item>
                            
                            <Form.Item>
                                <Button 
                                    type="primary" 
                                    htmlType="submit"
                                    icon={<PlusOutlined />}
                                >
                                    Add League
                                </Button>
                            </Form.Item>
                        </Space>
                    </Form>
                </Card>
            </Card>
        )
    }
}

export default Leagues