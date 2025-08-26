import React from 'react'
import { Card, Table, Button, Input, Space, message, Tag, Tooltip, Select, Divider, Row, Col, Statistic, Typography, Badge } from 'antd'
import { 
    HistoryOutlined, 
    MessageOutlined, 
    TwitterOutlined, 
    CheckCircleOutlined, 
    CloseCircleOutlined,
    ReloadOutlined,
    FilterOutlined,
    InfoCircleOutlined
} from '@ant-design/icons'

const { Search } = Input
const { Option } = Select
const { Text, Title } = Typography

class MessageHistory extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            messageHistory: [],
            loading: true,
            pagination: {
                current: 1,
                pageSize: 10,
                total: 0,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} messages`
            },
            filters: {
                messageType: 'all',
                messagingService: 'all',
                success: 'all',
                searchText: ''
            },
            stats: null,
            expandedRowKeys: []
        }
    }

    componentDidMount() {
        this.fetchMessageHistory()
        this.fetchStats()
    }

    fetchMessageHistory = (page = 1, pageSize = 10) => {
        this.setState({ loading: true })
        
        // Calculate limit based on pagination
        const limit = pageSize * 2 // Fetch more to handle filtering
        
        fetch(`/api/messageHistory?limit=${limit}`)
            .then(res => res.json())
            .then((result) => {
                // Apply client-side filtering
                const filteredData = this.applyFilters(result)
                
                // Apply pagination to filtered data
                const startIndex = (page - 1) * pageSize
                const endIndex = startIndex + pageSize
                const paginatedData = filteredData.slice(startIndex, endIndex)
                
                this.setState({
                    messageHistory: paginatedData,
                    loading: false,
                    pagination: {
                        ...this.state.pagination,
                        current: page,
                        pageSize: pageSize,
                        total: filteredData.length
                    }
                })
            })
            .catch((error) => {
                console.error('Failed to fetch message history:', error)
                message.error('Failed to load message history')
                this.setState({ loading: false })
            })
    }

    fetchStats = () => {
        fetch('/api/messageHistory/stats')
            .then(res => res.json())
            .then((result) => {
                this.setState({ stats: result })
            })
            .catch((error) => {
                console.error('Failed to fetch stats:', error)
            })
    }

    applyFilters = (data) => {
        const { filters } = this.state
        
        return data.filter(item => {
            // Message type filter
            if (filters.messageType !== 'all' && item.messageType !== filters.messageType) {
                return false
            }
            
            // Messaging service filter
            if (filters.messagingService !== 'all' && item.messagingService !== filters.messagingService) {
                return false
            }
            
            // Success filter
            if (filters.success !== 'all') {
                const isSuccess = filters.success === 'true'
                if (item.success !== isSuccess) {
                    return false
                }
            }
            
            // Search text filter
            if (filters.searchText) {
                const searchLower = filters.searchText.toLowerCase()
                const searchableText = [
                    item.originalMessage,
                    item.schefterTweet,
                    item.playersInvolved?.join(' ')
                ].filter(Boolean).join(' ').toLowerCase()
                
                if (!searchableText.includes(searchLower)) {
                    return false
                }
            }
            
            return true
        })
    }

    handleTableChange = (pagination, filters, sorter) => {
        this.fetchMessageHistory(pagination.current, pagination.pageSize)
    }

    handleFilterChange = (filterType, value) => {
        this.setState(
            {
                filters: {
                    ...this.state.filters,
                    [filterType]: value
                },
                pagination: {
                    ...this.state.pagination,
                    current: 1 // Reset to first page when filtering
                }
            },
            () => this.fetchMessageHistory(1, this.state.pagination.pageSize)
        )
    }

    handleSearch = (value) => {
        this.handleFilterChange('searchText', value)
    }

    refresh = () => {
        this.fetchMessageHistory(this.state.pagination.current, this.state.pagination.pageSize)
        this.fetchStats()
    }

    getMessageTypeColor = (type) => {
        switch (type) {
            case 'TRANSACTION': return 'blue'
            case 'ALERT': return 'orange'
            case 'TEST': return 'purple'
            case 'STARTUP': return 'green'
            default: return 'default'
        }
    }

    getServiceColor = (service) => {
        switch (service) {
            case 'DISCORD': return 'blue'
            case 'SLACK': return 'purple'
            case 'GROUPME': return 'green'
            default: return 'default'
        }
    }

    formatTimestamp = (timestamp) => {
        return new Date(timestamp).toLocaleString()
    }

    expandedRowRender = (record) => {
        return (
            <div style={{ margin: 0 }}>
                <Divider orientation="left" style={{ margin: '12px 0' }}>Message Details</Divider>
                <Row gutter={[16, 8]}>
                    <Col span={24}>
                        <Text strong>Original Message:</Text>
                        <div style={{ 
                            backgroundColor: '#f5f5f5', 
                            padding: '8px 12px', 
                            borderRadius: '4px',
                            marginTop: '4px',
                            fontFamily: 'monospace'
                        }}>
                            {record.originalMessage}
                        </div>
                    </Col>
                    {record.schefterTweet && (
                        <Col span={24} style={{ marginTop: '12px' }}>
                            <Text strong>Schefter Tweet:</Text>
                            <div style={{ 
                                backgroundColor: '#e6f7ff', 
                                padding: '8px 12px', 
                                borderRadius: '4px',
                                marginTop: '4px',
                                fontFamily: 'monospace',
                                borderLeft: '3px solid #1890ff'
                            }}>
                                {record.schefterTweet}
                            </div>
                        </Col>
                    )}
                    {record.playersInvolved && record.playersInvolved.length > 0 && (
                        <Col span={24} style={{ marginTop: '12px' }}>
                            <Text strong>Players Involved:</Text>
                            <div style={{ marginTop: '4px' }}>
                                {record.playersInvolved.map(player => (
                                    <Tag key={player} color="geekblue">{player}</Tag>
                                ))}
                            </div>
                        </Col>
                    )}
                    {record.errorMessage && (
                        <Col span={24} style={{ marginTop: '12px' }}>
                            <Text strong type="danger">Error:</Text>
                            <div style={{ 
                                backgroundColor: '#fff2f0', 
                                padding: '8px 12px', 
                                borderRadius: '4px',
                                marginTop: '4px',
                                color: '#ff4d4f'
                            }}>
                                {record.errorMessage}
                            </div>
                        </Col>
                    )}
                </Row>
            </div>
        )
    }

    render() {
        const columns = [
            {
                title: 'Timestamp',
                dataIndex: 'timestamp',
                key: 'timestamp',
                width: 180,
                render: (timestamp) => (
                    <Tooltip title={timestamp}>
                        <Text code>{this.formatTimestamp(timestamp)}</Text>
                    </Tooltip>
                ),
                sorter: (a, b) => new Date(a.timestamp) - new Date(b.timestamp),
                defaultSortOrder: 'descend'
            },
            {
                title: 'Type',
                dataIndex: 'messageType',
                key: 'messageType',
                width: 120,
                render: (type, record) => (
                    <Space direction="vertical" size={2}>
                        <Tag color={this.getMessageTypeColor(type)}>
                            {type}
                        </Tag>
                        {record.transactionType && (
                            <Tag size="small" color="default">
                                {record.transactionType}
                            </Tag>
                        )}
                    </Space>
                )
            },
            {
                title: 'Service',
                dataIndex: 'messagingService',
                key: 'messagingService',
                width: 100,
                render: (service) => (
                    <Tag color={this.getServiceColor(service)}>
                        {service}
                    </Tag>
                )
            },
            {
                title: 'Status',
                dataIndex: 'success',
                key: 'status',
                width: 100,
                render: (success, record) => (
                    <Space direction="vertical" size={2}>
                        {success ? (
                            <Tag icon={<CheckCircleOutlined />} color="success">
                                Success
                            </Tag>
                        ) : (
                            <Tag icon={<CloseCircleOutlined />} color="error">
                                Failed
                            </Tag>
                        )}
                        {record.responseCode && (
                            <Text type="secondary" style={{ fontSize: '12px' }}>
                                {record.responseCode}
                            </Text>
                        )}
                    </Space>
                )
            },
            {
                title: 'Content',
                key: 'content',
                render: (_, record) => (
                    <Space>
                        <Tooltip title="Has original message">
                            <MessageOutlined style={{ color: '#1890ff' }} />
                        </Tooltip>
                        {record.hasSchefterTweet && (
                            <Tooltip title="Has Schefter tweet">
                                <Badge dot>
                                    <TwitterOutlined style={{ color: '#1da1f2' }} />
                                </Badge>
                            </Tooltip>
                        )}
                        {record.playersInvolved && record.playersInvolved.length > 0 && (
                            <Tooltip title={`${record.playersInvolved.length} players involved`}>
                                <Badge count={record.playersInvolved.length} size="small">
                                    <span>👥</span>
                                </Badge>
                            </Tooltip>
                        )}
                    </Space>
                )
            },
            {
                title: 'Preview',
                key: 'preview',
                width: 300,
                render: (_, record) => (
                    <div style={{ maxWidth: '300px' }}>
                        <Text 
                            ellipsis={{ tooltip: record.originalMessage }}
                            style={{ fontSize: '12px' }}
                        >
                            {record.originalMessage}
                        </Text>
                    </div>
                )
            }
        ]

        return (
            <Card
                title={
                    <Space>
                        <HistoryOutlined />
                        <span>Message History</span>
                        <Tooltip title="View all sent messages and their status">
                            <InfoCircleOutlined style={{ color: '#1890ff' }} />
                        </Tooltip>
                    </Space>
                }
                extra={
                    <Button 
                        icon={<ReloadOutlined />} 
                        onClick={this.refresh}
                        loading={this.state.loading}
                    >
                        Refresh
                    </Button>
                }
            >
                {/* Statistics */}
                {this.state.stats && (
                    <Card type="inner" style={{ marginBottom: 16 }}>
                        <Row gutter={16}>
                            <Col span={6}>
                                <Statistic 
                                    title="Total Messages" 
                                    value={this.state.stats.totalMessages} 
                                    prefix={<MessageOutlined />}
                                />
                            </Col>
                            <Col span={6}>
                                <Statistic 
                                    title="Successful" 
                                    value={this.state.stats.successfulMessages} 
                                    valueStyle={{ color: '#3f8600' }}
                                    prefix={<CheckCircleOutlined />}
                                />
                            </Col>
                            <Col span={6}>
                                <Statistic 
                                    title="Failed" 
                                    value={this.state.stats.failedMessages} 
                                    valueStyle={{ color: '#cf1322' }}
                                    prefix={<CloseCircleOutlined />}
                                />
                            </Col>
                            <Col span={6}>
                                <Statistic 
                                    title="Schefter Tweets" 
                                    value={this.state.stats.schefterTweetsGenerated} 
                                    prefix={<TwitterOutlined />}
                                />
                            </Col>
                        </Row>
                    </Card>
                )}

                {/* Filters */}
                <Card type="inner" title={<Space><FilterOutlined />Filters</Space>} style={{ marginBottom: 16 }}>
                    <Row gutter={[16, 16]}>
                        <Col xs={24} sm={12} md={6}>
                            <span style={{ marginRight: 8 }}>Type:</span>
                            <Select
                                value={this.state.filters.messageType}
                                style={{ width: '100%' }}
                                onChange={(value) => this.handleFilterChange('messageType', value)}
                            >
                                <Option value="all">All Types</Option>
                                <Option value="TRANSACTION">Transactions</Option>
                                <Option value="ALERT">Alerts</Option>
                                <Option value="TEST">Test Messages</Option>
                                <Option value="STARTUP">Startup</Option>
                            </Select>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                            <span style={{ marginRight: 8 }}>Service:</span>
                            <Select
                                value={this.state.filters.messagingService}
                                style={{ width: '100%' }}
                                onChange={(value) => this.handleFilterChange('messagingService', value)}
                            >
                                <Option value="all">All Services</Option>
                                <Option value="DISCORD">Discord</Option>
                                <Option value="SLACK">Slack</Option>
                                <Option value="GROUPME">GroupMe</Option>
                            </Select>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                            <span style={{ marginRight: 8 }}>Status:</span>
                            <Select
                                value={this.state.filters.success}
                                style={{ width: '100%' }}
                                onChange={(value) => this.handleFilterChange('success', value)}
                            >
                                <Option value="all">All Status</Option>
                                <Option value="true">Success Only</Option>
                                <Option value="false">Failed Only</Option>
                            </Select>
                        </Col>
                        <Col xs={24} sm={12} md={6}>
                            <Search
                                placeholder="Search messages..."
                                allowClear
                                onSearch={this.handleSearch}
                                style={{ width: '100%' }}
                            />
                        </Col>
                    </Row>
                </Card>

                {/* Table */}
                <Table
                    columns={columns}
                    dataSource={this.state.messageHistory}
                    rowKey="id"
                    loading={this.state.loading}
                    pagination={this.state.pagination}
                    onChange={this.handleTableChange}
                    expandable={{
                        expandedRowRender: this.expandedRowRender,
                        expandedRowKeys: this.state.expandedRowKeys,
                        onExpand: (expanded, record) => {
                            const expandedRowKeys = expanded 
                                ? [...this.state.expandedRowKeys, record.id]
                                : this.state.expandedRowKeys.filter(key => key !== record.id)
                            this.setState({ expandedRowKeys })
                        }
                    }}
                    scroll={{ x: 1200 }}
                    size="small"
                />
            </Card>
        )
    }
}

export default MessageHistory
