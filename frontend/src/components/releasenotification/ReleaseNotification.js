import React from 'react'
import { Alert, Button, Modal, Typography, Space } from 'antd'
import { InfoCircleOutlined } from '@ant-design/icons'

const { Title, Text } = Typography

class ReleaseNotification extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            releaseInformation: {},
            showModal: false
        }

        this.showNotification = this.showNotification.bind(this)
        this.handleOpenModal = this.handleOpenModal.bind(this)
        this.handleCloseModal = this.handleCloseModal.bind(this)
    }

    componentDidMount() {
        fetch("/releaseInformation")
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    releaseInformation: result
                })
            },
                (error) => {
                    console.log(error)
                })
    }

    showNotification() {
        if (this.state.releaseInformation.upgrade) {
            return (
                <Alert
                    message={`Version ${this.state.releaseInformation.latestVersion} is available!`}
                    description={
                        <Space>
                            <Text>A new version has been released.</Text>
                            <Button 
                                type="link" 
                                size="small" 
                                icon={<InfoCircleOutlined />}
                                onClick={this.handleOpenModal}
                            >
                                Show Changelog
                            </Button>
                        </Space>
                    }
                    type="info"
                    showIcon
                    closable
                    style={{ marginBottom: 16 }}
                />
            )
        }
        return null
    }

    handleOpenModal() {
        this.setState({ showModal: true })
    }

    handleCloseModal() {
        this.setState({ showModal: false })
    }

    handleOpenRepo = () => {
        window.open('http://github.com/cujojp/yahoo-fantasy-bot', '_blank')
    }

    render() {
        return (
            <>
                {this.showNotification()}
                <Modal
                    title="Changelog"
                    open={this.state.showModal}
                    onCancel={this.handleCloseModal}
                    footer={[
                        <Button key="close" onClick={this.handleCloseModal}>
                            Close
                        </Button>,
                        <Button key="download" type="primary" onClick={this.handleOpenRepo}>
                            Get the latest version!
                        </Button>
                    ]}
                >
                    <Text>{this.state.releaseInformation.changelog}</Text>
                </Modal>
            </>
        )
    }
}

export default ReleaseNotification