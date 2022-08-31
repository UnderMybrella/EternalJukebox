import React from "react";
import {Button, ButtonGroup} from "react-bootstrap";
import logo from './logo.svg';
import './App.css';

type EternalBoxPlayControlsState = {
    isPlaying: boolean
}

class EternalBoxPlayControls extends React.Component<any, EternalBoxPlayControlsState> {
    constructor(props: any) {
        super(props);
        this.state = {isPlaying: false}

        this.handleClick = this.handleClick.bind(this);
    }

    handleClick() {
        this.setState(prevState => ({
            isPlaying: !prevState.isPlaying
        }));
    }

    render() {
        return <ButtonGroup>
            {
                this.state.isPlaying
                    ? <Button variant="outline-danger" onClick={this.handleClick()}>Stop</Button>
                    : <Button variant="outline-success">Play</Button>
            }
            <Button variant="outline-primary">Tune</Button>
            <Button variant="outline-primary">Star</Button>
            <Button variant="outline-primary">Share</Button>
            <Button variant="outline-primary">Sources</Button>
        </ButtonGroup>
    }
}

class EternalBoxPlay extends React.Component {
    render() {
        return <div className="App">
            <header className="App-header">
                <EternalBoxPlayControls/>
                <img src={logo} className="App-logo" alt="logo"/>
                <p>
                    Edit <code>src/App.tsx</code> and save to reload.
                </p>
                <a
                    className="App-link"
                    href="https://reactjs.org"
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    Learn React
                </a>
            </header>
        </div>;
    }
}

export default EternalBoxPlay;