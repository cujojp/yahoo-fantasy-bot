import React from 'react'
import { Row, Col, Typography, Space } from 'antd'
import GitHubButton from 'react-github-btn'

const { Title, Text } = Typography

class Header extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            version: ""
        }
    }

    componentDidMount() {
        fetch("/releaseInformation")
            .then(res => res.json())
            .then((result) => {
                this.setState({
                    version: result.currentVersion
                })
            },
                (error) => {
                    console.log("Error mate!")
                })
    }

    render() {
        return (
            <Row justify="space-between" align="middle" style={{ height: '100%' }}>
                <Col>
                    <Space align="baseline">
                        <Title level={3} style={{ margin: 0 }}>Yahoo Fantasy Bot</Title>
                        <Text type="secondary">v{this.state.version}</Text>
                    </Space>
                </Col>
                <Col>
                    <Space size="small">
                        <GitHubButton href="https://github.com/LandonPatmore" data-color-scheme="no-preference: dark; light: dark; dark: dark;" aria-label="Follow @LandonPatmore on GitHub">Follow @LandonPatmore</GitHubButton>
                        <GitHubButton href="https://github.com/cujojp/yahoo-fantasy-bot" data-color-scheme="no-preference: dark; light: dark; dark: dark;" data-icon="octicon-star" data-show-count="true" aria-label="Star cujojp/yahoo-fantasy-bot on GitHub">Star</GitHubButton>
                        <GitHubButton href="https://github.com/cujojp/yahoo-fantasy-bot/fork" data-color-scheme="no-preference: dark; light: dark; dark: dark;" data-icon="octicon-repo-forked" data-show-count="true" aria-label="Fork cujojp/yahoo-fantasy-bot on GitHub">Fork</GitHubButton>
                        <GitHubButton href="https://github.com/cujojp/yahoo-fantasy-bot/issues" data-color-scheme="no-preference: dark; light: dark; dark: dark;" data-icon="octicon-issue-opened" aria-label="Issue cujojp/yahoo-fantasy-bot on GitHub">Issue</GitHubButton>
                    </Space>
                </Col>
            </Row>
        )
    }
}

export default Header