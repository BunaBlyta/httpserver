"use strict";

const historyKey = "networkDashboardHistory";
const historyList = document.querySelector("#history-list");
const emptyHistory = document.querySelector("#empty-history");
let historyEntries = loadHistory();

function loadHistory() {
    try {
        return JSON.parse(sessionStorage.getItem(historyKey) || "[]");
    } catch {
        return [];
    }
}

function saveHistory() {
    sessionStorage.setItem(historyKey, JSON.stringify(historyEntries));
}

function renderHistory() {
    historyList.replaceChildren();
    emptyHistory.hidden = historyEntries.length > 0;

    for (const entry of historyEntries) {
        const item = document.createElement("li");
        item.className = "history-item";

        const title = document.createElement("strong");
        title.textContent = entry.title;

        const detail = document.createElement("span");
        detail.textContent = entry.detail;

        const time = document.createElement("time");
        time.dateTime = entry.time;
        time.textContent = new Date(entry.time).toLocaleString();

        item.append(title, detail, time);
        historyList.append(item);
    }
}

function addHistory(title, detail) {
    historyEntries.unshift({title, detail, time: new Date().toISOString()});
    historyEntries = historyEntries.slice(0, 20);
    saveHistory();
    renderHistory();
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    let body;
    try {
        body = await response.json();
    } catch {
        throw new Error(`Server returned HTTP ${response.status}`);
    }
    if (!response.ok) {
        throw new Error(body.message || `Request failed with HTTP ${response.status}`);
    }
    return body;
}

function setBusy(form, busy) {
    const button = form.querySelector("button[type='submit']");
    const loading = form.querySelector(".loading");
    button.disabled = busy;
    loading.hidden = !busy;
}

function setError(form, message = "") {
    form.querySelector(".form-error").textContent = message;
}

function formatUptime(seconds) {
    const total = Math.max(0, Number(seconds) || 0);
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const remainingSeconds = total % 60;
    return `${hours}h ${minutes}m ${remainingSeconds}s`;
}

async function refreshStatus() {
    const badge = document.querySelector("#status-badge");
    try {
        const status = await requestJson("/api/status");
        badge.textContent = "Server online";
        badge.className = "status-badge online";
        document.querySelector("#server-time").textContent = new Date(status.serverTime).toLocaleString();
        document.querySelector("#uptime").textContent = formatUptime(status.uptimeSeconds);
        document.querySelector("#request-count").textContent = String(status.requestCount);
        document.querySelector("#java-version").textContent = String(status.javaVersion);
    } catch {
        badge.textContent = "Server unavailable";
        badge.className = "status-badge offline";
    }
}

document.querySelector("#dns-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const host = form.elements.host.value.trim();
    setError(form);
    if (!host) {
        setError(form, "Enter a hostname.");
        return;
    }

    setBusy(form, true);
    try {
        const result = await requestJson(`/api/dns?host=${encodeURIComponent(host)}`);
        const addresses = result.addresses.length ? result.addresses.join(", ") : "No addresses";
        addHistory("DNS lookup", `${result.host}: ${addresses} (${result.durationMs} ms) — ${result.message}`);
    } catch (error) {
        setError(form, error.message);
    } finally {
        setBusy(form, false);
        refreshStatus();
    }
});

document.querySelector("#port-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const host = form.elements.host.value.trim();
    const port = Number(form.elements.port.value);
    setError(form);
    if (!host || !Number.isInteger(port) || port < 1 || port > 65535) {
        setError(form, "Enter a hostname and a port from 1 through 65535.");
        return;
    }

    setBusy(form, true);
    try {
        const result = await requestJson(
            `/api/port-check?host=${encodeURIComponent(host)}&port=${encodeURIComponent(port)}`);
        const state = result.open ? "open" : "closed or unreachable";
        addHistory("TCP port check",
            `${result.host}:${result.port} is ${state} (${result.durationMs} ms) — ${result.message}`);
    } catch (error) {
        setError(form, error.message);
    } finally {
        setBusy(form, false);
        refreshStatus();
    }
});

document.querySelector("#echo-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const message = form.elements.message.value;
    setError(form);
    if (!message) {
        setError(form, "Enter a message.");
        return;
    }

    setBusy(form, true);
    try {
        const result = await requestJson("/api/echo", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({message})
        });
        addHistory("Echo message", result.message);
    } catch (error) {
        setError(form, error.message);
    } finally {
        setBusy(form, false);
        refreshStatus();
    }
});

document.querySelector("#clear-history").addEventListener("click", () => {
    historyEntries = [];
    saveHistory();
    renderHistory();
});

renderHistory();
refreshStatus();
window.setInterval(refreshStatus, 5000);
