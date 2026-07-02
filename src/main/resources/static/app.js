document.getElementById("startServer").addEventListener("click", async (event) => {
    const result = await startServer();
    if (result) {
        document.getElementById("serverStatus").innerHTML = result
    }
});

document.getElementById("stopServer").addEventListener("click", async (event) => {
    const result = await stopServer();
    if (result) {
        document.getElementById("serverStatus").innerHTML = result;
    }

});


async function startServer() {
    const url = "http://localhost:8080/server/start";
    try {
        const response = await fetch(url, {
            method: "POST"
        });

        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        const result = await response.text();
        return result;
    } catch (error) {
        console.error(error.message);
    }
}

async function stopServer() {
    const url = "http://localhost:8080/server/stop";
    try {
        const response = await fetch(url, {
            method: "POST"
        });

        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        const result = await response.text();
        return result;
    } catch (error) {
        console.error(error.message);
    }
}

async function getLogs() {
    const url = "http://localhost:8080/server/logs";
    try {
        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        const result = await response.json();
        return result;
    } catch (error) {
        console.error(error.message);
    }
}

const socket = new WebSocket("ws://localhost:8080/ws/logs");

socket.onmessage = function(event) {
    const data = event.data;

    if (data === "Starting Server!") {
        document.getElementById("serverStatus").innerHTML = "Starting...";
    } else if (data === "Server Started!") {
        document.getElementById("serverStatus").innerHTML = "Running";
    } else if (data === "Stopped Server!") {
        document.getElementById("serverStatus").innerHTML = "Stopped";
    } else if (data === "Stopping Server!") {
        document.getElementById("serverStatus").innerHTML = "Stopping";
    } else {
        // normal log line
        const p = document.createElement("p");
        p.textContent = data;
        document.getElementById("logs").appendChild(p);
    }
};