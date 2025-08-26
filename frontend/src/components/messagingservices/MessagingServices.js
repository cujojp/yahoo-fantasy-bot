import React from 'react'
import './MessagingServices.scss'
import { MdDeleteForever } from 'react-icons/md'

class MessagingServices extends React.Component {
    constructor(props) {
        super(props)

        this.state = {
            messagingServices: [],
            service: "",
            url: "",
            testingMessage: false,
            testResults: null
        }

        this.addMessagingService = this.addMessagingService.bind(this)
        this.sendTestMessage = this.sendTestMessage.bind(this)
    }

    componentDidMount() {
        fetch("/messagingServices")
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    messagingServices: result
                })
            },
                (error) => {
                    console.log("Error mate!")
                })
    }

    handleService = (event) => {
        this.setState({
            service: event.target.value
        })
    }

    handleUrl = (event) => {
        this.setState({
            url: event.target.value
        })
    }

    mapToService(service){
        switch(service) {
            case 0: return "Discord"
            case 1: return "Slack"
            case 2: return "GroupMe"
            default: return "N/A"
        }
    }

    addMessagingService() {
        const messagingServices = [...this.state.messagingServices]
        messagingServices.push({
            service: parseInt(this.state.service),
            url: this.state.url
        })
        console.log(messagingServices)

        fetch("/messagingServices", {
            method: "PUT",
            body: JSON.stringify(messagingServices)
        })
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    messagingServices: result
                })
            },
                (error) => {
                    console.log("Error mate!")
                })
        console.log(this.state)
    }

    deleteMessagingService(event, index) {
        const messagingServices = [...this.state.messagingServices]
        messagingServices.splice(index, 1)
        console.log(messagingServices)

        fetch("/messagingServices", {
            method: "PUT",
            body: JSON.stringify(messagingServices)
        })
            .then(res => res.json())
            .then((result) => {
                console.log(result)
                this.setState({
                    messagingServices: result
                })
            },
                (error) => {
                    console.log("Error mate!")
                })
        console.log(this.state)
    }

    sendTestMessage() {
        this.setState({ testingMessage: true, testResults: null })
        
        fetch("/testMessage", {
            method: "POST",
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                message: "🤖 Test message from TECHMO BOT!"
            })
        })
            .then(res => res.json())
            .then((result) => {
                console.log("Test results:", result)
                this.setState({
                    testingMessage: false,
                    testResults: result
                })
                // Clear results after 5 seconds
                setTimeout(() => {
                    this.setState({ testResults: null })
                }, 5000)
            },
                (error) => {
                    console.log("Error sending test message:", error)
                    this.setState({
                        testingMessage: false,
                        testResults: { error: "Failed to send test message" }
                    })
                })
    }

    render() {
        const columns = [
            "Service",
            "Url"
        ]

        return (
            <div id="messagingservices-area">
                <h2 className="area-header">Messaging Services</h2>
                <table>
                    <thead>
                        <tr>
                            {
                                columns.map(column => {
                                    return <th>{column}</th>
                                })
                            }
                        </tr>
                    </thead>
                    <tbody>
                        {
                            this.state.messagingServices.map((messagingService, index) => {
                                return <tr key={index}>
                                    <td>{this.mapToService(messagingService.service)}</td>
                                    <td>{messagingService.url}</td>
                                    <td><button onClick={(event) => this.deleteMessagingService(event, index)}><MdDeleteForever /></button></td>
                                </tr>
                            })
                        }
                    </tbody>
                </table>
                <div>
                    <div className="add-area">
                        <select name="service" onChange={this.handleService} value={this.state.service}>
                            <option value="" disabled selected>Service</option>
                            {
                                Array.from(Array(3).keys()).map(num => {
                                    const val = this.mapToService(num)
                                    return <option value={num}>{val}</option>
                                })
                            }
                        </select>
                        <input placeholder="Url" onChange={this.handleUrl} value={this.state.url}></input>
                    </div>
                    <div className="add-button-area">
                        <button id="add-button" type="button" onClick={this.addMessagingService}>Add Service</button>
                        <button 
                            id="test-button" 
                            type="button" 
                            onClick={this.sendTestMessage}
                            disabled={this.state.testingMessage || this.state.messagingServices.length === 0}
                            style={{ marginLeft: '10px' }}
                        >
                            {this.state.testingMessage ? 'Sending...' : 'Test Messages'}
                        </button>
                    </div>
                    {this.state.testResults && (
                        <div className="test-results" style={{ marginTop: '10px', padding: '10px', background: '#f0f0f0', borderRadius: '5px' }}>
                            <h4>Test Results:</h4>
                            {Object.entries(this.state.testResults).map(([service, result]) => (
                                <div key={service}>
                                    <strong>{service}:</strong> {result}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        )
    }
}

export default MessagingServices