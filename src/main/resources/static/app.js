let selectedId = null;
let servers = [];
let websocket = null;
let toastTimeout = null;

const BASE = '';

// ── Helpers ──

function $(id) { return document.getElementById(id); }

function api(path, opts = {}) {
    return fetch(BASE + path, {
        headers: { 'Content-Type': 'application/json', ...opts.headers },
        ...opts
    }).then(async r => {
        if (!r.ok) {
            let msg = `HTTP ${r.status}`;
            try { const e = await r.json(); msg = e.error || msg; } catch {}
            throw new Error(msg);
        }
        const ct = r.headers.get('content-type') || '';
        return ct.includes('json') ? r.json() : r.text();
    });
}

function toast(msg, type = 'error') {
    const el = $('toast');
    el.textContent = msg;
    el.className = 'toast ' + type;
    clearTimeout(toastTimeout);
    toastTimeout = setTimeout(() => el.style.display = 'none', 4000);
}

function selectedServer() { return servers.find(s => s.id === selectedId); }

// ── Tab switching ──

document.querySelectorAll('.tabs button').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
        btn.classList.add('active');
        $(`tab-${btn.dataset.tab}`).classList.add('active');
    });
});

// ── Server List ──

function renderServerList() {
    const list = $('serverList');
    list.innerHTML = servers.map(s => `
        <div class="server-item ${s.id === selectedId ? 'active' : ''}" data-id="${s.id}">
            <span class="dot ${stateClass(s.state)}"></span>
            <div class="info">
                <div class="name">${esc(s.worldName)}</div>
                <div class="meta">Port ${s.port} · ${s.state}</div>
            </div>
        </div>
    `).join('');
    list.querySelectorAll('.server-item').forEach(el => {
        el.addEventListener('click', () => selectServer(Number(el.dataset.id)));
    });
}

function stateClass(st) {
    switch (st) {
        case 'RUNNING': return 'online';
        case 'STARTING': case 'STOPPING': return 'starting';
        case 'CRASHED': return 'crashed';
        default: return 'offline';
    }
}

function selectServer(id) {
    console.log('selectServer called with id=' + id + ', servers=', servers);
    selectedId = id;
    const s = selectedServer();
    console.log('selectServer: found server?', s);
    if (!s) { console.log('selectServer: server not found, aborting'); return; }
    renderServerList();
    updateTopbar(s);
    refreshPlayers();
    refreshBans();
    if (s.type === 'TMODLOADER') refreshMods();
}

function updateTopbar(s) {
    if (!s) {
        $('statusDot').className = 'dot offline';
        $('statusLabel').textContent = 'Select a server';
        $('serverMeta').textContent = '';
        $('btnStart').disabled = true;
        $('btnStop').disabled = true;
        $('btnSave').disabled = true;
        return;
    }
    $('statusDot').className = 'dot ' + stateClass(s.state);
    $('statusLabel').textContent = `${s.worldName} — ${s.state}`;
    $('serverMeta').textContent = `Port ${s.port} · ${s.serverPath}`;
    $('btnStart').disabled = s.state === 'RUNNING' || s.state === 'STARTING';
    $('btnStop').disabled = s.state !== 'RUNNING';
    $('btnSave').disabled = s.state !== 'RUNNING';
}

function esc(s) {
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}

// ── CRUD ──

function loadServers() {
    api('/server/').then(data => {
        console.log('loadServers: got', JSON.stringify(data));
        servers = data;
        renderServerList();
        if (!selectedServer() && servers.length > 0) selectServer(servers[0].id);
        else if (selectedServer()) {
            const stillExists = servers.find(s => s.id === selectedId);
            if (!stillExists) { selectedId = null; updateTopbar(null); renderServerList(); }
            else updateTopbar(stillExists);
        } else updateTopbar(null);
    }).catch(e => { console.log('loadServers: error', e); toast('Failed to load servers: ' + e.message); });
}

function showCreateModal() { $('createModal').classList.add('open'); }

function closeCreateModal() { $('createModal').classList.remove('open'); }

function createServer() {
    const body = {
        port: parseInt($('createPort').value),
        worldName: $('createWorld').value || 'World',
        type: $('createType').value
    };
    api('/server/create', { method: 'POST', body: JSON.stringify(body) })
        .then(s => { closeCreateModal(); toast('Server created', 'success'); loadServers(); })
        .catch(e => toast('Create failed: ' + e.message));
}

// ── Server Actions ──

function startServer() {
    if (selectedId === null || selectedId === undefined) {
        console.log('startServer: no selectedId, trying auto-select');
        if (servers.length > 0) { selectServer(servers[0].id); }
        else { return; }
    }
    console.log('startServer: starting server id=' + selectedId);
    const btn = $('btnStart');
    const orig = btn.textContent;
    btn.textContent = 'Starting...';
    btn.disabled = true;
    api(`/server/start/${selectedId}`, { method: 'POST' })
        .then(s => { console.log('startServer: ok', s); selectedId = s.id; loadServers(); })
        .catch(e => { console.log('startServer: error', e.message); toast('Start failed: ' + e.message); btn.textContent = orig; btn.disabled = false; });
}

function stopServer() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/stop/${selectedId}`, { method: 'POST' })
        .then(s => { selectedId = s.id; loadServers(); })
        .catch(e => toast('Stop failed: ' + e.message));
}

function saveServer() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/save`, { method: 'POST' })
        .then(() => toast('World saved', 'success'))
        .catch(e => toast('Save failed: ' + e.message));
}

// ── WebSocket Console ──

function connectWebSocket() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${proto}//${location.host}/ws/logs`;
    websocket = new WebSocket(url);
    websocket.onmessage = handleWsMessage;
    websocket.onclose = () => setTimeout(connectWebSocket, 2000);
    websocket.onerror = () => websocket.close();
}

function handleWsMessage(event) {
    let data;
    try { data = JSON.parse(event.data); } catch { data = { type: "raw", message: event.data }; }
    console.log("WS raw:", event.data);
    console.log("WS parsed:", data);

    const consoleEl = $('console');
    const entry = document.createElement('div');
    entry.className = 'log-entry';

    const time = document.createElement('span');
    time.className = 'timestamp';
    time.textContent = new Date().toLocaleTimeString();
    entry.appendChild(time);

    const msg = document.createElement('span');

    switch (data.type) {
        case 'state':
            msg.className = 'level-state';
            msg.textContent = `[${data.state}] ${data.text}`;
            // update server state in sidebar if it matches
            const srv = servers.find(s => s.id === data.serverId);
            if (srv) { srv.state = data.state; renderServerList(); if (srv.id === selectedId) updateTopbar(srv); }
            break;
        case 'log':
            msg.className = 'level-info';
            msg.textContent = data.text;
            break;
        case 'error':
            msg.className = 'level-error';
            msg.textContent = `ERROR: ${data.reason} — ${data.detail}`;
            break;
        case 'game_action':
            msg.className = 'level-action';
            msg.textContent = `[${data.action}]${data.target ? ' ' + data.target : ''}${data.detail ? ' — ' + data.detail : ''}`;
            break;
        default:
            msg.className = 'level-info';
            msg.textContent = event.data;
    }

    entry.appendChild(msg);
    consoleEl.appendChild(entry);
    consoleEl.scrollTop = consoleEl.scrollHeight;
    // keep last 500 lines
    while (consoleEl.children.length > 500) consoleEl.removeChild(consoleEl.firstChild);
}

// ── Players ──

function refreshPlayers() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/players`).then(players => {
        const tbody = $('playerTable');
        if (!players || players.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="empty-state">No players connected</td></tr>';
            return;
        }
        tbody.innerHTML = players.map(p => `
            <tr>
                <td>${esc(p.name)}</td>
                <td>${p.ip || '—'}</td>
                <td>
                    <button class="btn-sm btn-danger" onclick="kickPlayer('${esc(p.name)}')">Kick</button>
                    <button class="btn-sm btn-danger" onclick="banPlayerName('${esc(p.name)}')" style="margin-left:4px">Ban</button>
                </td>
            </tr>
        `).join('');
    }).catch(() => {});
}

function kickPlayer(name) {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/kick`, { method: 'POST', body: JSON.stringify({ name, details: 'Kicked by admin' }) })
        .then(() => { toast('Kicked ' + name, 'success'); refreshPlayers(); })
        .catch(e => toast('Kick failed: ' + e.message));
}

// ── Bans ──

function refreshBans() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/ban`).then(bans => {
        const tbody = $('banTable');
        if (!bans || bans.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="empty-state">No bans</td></tr>';
            return;
        }
        tbody.innerHTML = bans.map(b => `
            <tr>
                <td>${esc(b.name)}</td>
                <td>${b.reason || '—'}</td>
                <td><button class="btn-sm btn-primary" onclick="unbanPlayerName('${esc(b.name)}')">Unban</button></td>
            </tr>
        `).join('');
    }).catch(() => {});
}

function banPlayer() {
    const name = $('banNameInput').value.trim();
    if (!name || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/ban`, { method: 'POST', body: JSON.stringify({ name, details: 'Banned by admin' }) })
        .then(() => { toast('Banned ' + name, 'success'); $('banNameInput').value = ''; refreshBans(); refreshPlayers(); })
        .catch(e => toast('Ban failed: ' + e.message));
}

function banPlayerName(name) {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/ban`, { method: 'POST', body: JSON.stringify({ name, details: 'Banned by admin' }) })
        .then(() => { toast('Banned ' + name, 'success'); refreshBans(); refreshPlayers(); })
        .catch(e => toast('Ban failed: ' + e.message));
}

function unbanPlayer() {
    const name = $('unbanNameInput').value.trim();
    if (!name || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/unban`, { method: 'POST', body: JSON.stringify({ name }) })
        .then(() => { toast('Unbanned ' + name, 'success'); $('unbanNameInput').value = ''; refreshBans(); })
        .catch(e => toast('Unban failed: ' + e.message));
}

function unbanPlayerName(name) {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/unban`, { method: 'POST', body: JSON.stringify({ name }) })
        .then(() => { toast('Unbanned ' + name, 'success'); refreshBans(); })
        .catch(e => toast('Unban failed: ' + e.message));
}

// ── Controls ──

function sayMessage() {
    const msg = $('sayInput').value.trim();
    if (!msg || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/say`, { method: 'POST', body: JSON.stringify({ message: msg }) })
        .then(() => { $('sayInput').value = ''; toast('Message sent', 'success'); })
        .catch(e => toast('Say failed: ' + e.message));
}

function setTime() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/time`, { method: 'POST', body: JSON.stringify({ time: $('timeSelect').value }) })
        .then(() => toast('Time set', 'success'))
        .catch(e => toast('Set time failed: ' + e.message));
}

function setPassword() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/password`, { method: 'POST', body: JSON.stringify({ password: $('passwordInput').value }) })
        .then(() => { toast('Password set', 'success'); $('passwordInput').value = ''; })
        .catch(e => toast('Password failed: ' + e.message));
}

function queryTime() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/time`).then(r => $('timeDisplay').textContent = r.time)
        .catch(e => toast('Query time failed: ' + e.message));
}

function querySeed() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/seed`).then(r => $('seedDisplay').textContent = r.seed)
        .catch(e => toast('Query seed failed: ' + e.message));
}

function settleWater() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/settle`, { method: 'POST' })
        .then(r => toast(r.message || 'Settled', 'success'))
        .catch(e => toast('Settle failed: ' + e.message));
}

// ── Commands ──

function tpToSpawn() {
    const name = $('spawnInput').value.trim();
    if (!name || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/spawn`, { method: 'POST', body: JSON.stringify({ name }) })
        .then(r => { toast(r.message || 'TP sent', 'success'); $('spawnInput').value = ''; })
        .catch(e => toast('TP failed: ' + e.message));
}

function tpToPlayer() {
    const name = $('tpFromInput').value.trim();
    const target = $('tpToInput').value.trim();
    if (!name || !target || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/tp`, { method: 'POST', body: JSON.stringify({ name, target }) })
        .then(r => { toast(r.message || 'TP sent', 'success'); $('tpFromInput').value = ''; $('tpToInput').value = ''; })
        .catch(e => toast('TP failed: ' + e.message));
}

function spawnMob() {
    const npcName = $('spawnMobNpc').value.trim();
    const playerName = $('spawnMobPlayer').value.trim();
    if (!npcName || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/spawnmob`, { method: 'POST', body: JSON.stringify({ npcName, playerName }) })
        .then(r => { toast(r.message || 'Mob spawned', 'success'); $('spawnMobNpc').value = ''; $('spawnMobPlayer').value = ''; })
        .catch(e => toast('Spawn mob failed: ' + e.message));
}

function killEntity() {
    const name = $('killInput').value.trim();
    if (!name || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/kill`, { method: 'POST', body: JSON.stringify({ name }) })
        .then(r => { toast(r.message || 'Kill sent', 'success'); $('killInput').value = ''; })
        .catch(e => toast('Kill failed: ' + e.message));
}

function giveItem() {
    const playerName = $('givePlayerInput').value.trim();
    const itemName = $('giveItemInput').value.trim();
    if (!playerName || !itemName || (selectedId === null || selectedId === undefined)) return;
    api(`/server/${selectedId}/give`, { method: 'POST', body: JSON.stringify({ playerName, itemName }) })
        .then(r => { toast(r.message || 'Item given', 'success'); $('givePlayerInput').value = ''; $('giveItemInput').value = ''; })
        .catch(e => toast('Give failed: ' + e.message));
}

// ── Mods ──

function refreshMods() {
    if (selectedId === null || selectedId === undefined) return;
    api(`/server/${selectedId}/mods`).then(mods => {
        const el = $('modList');
        if (!mods || mods.length === 0) { el.innerHTML = '<p class="empty-state">No mods found</p>'; return; }
        el.innerHTML = '<div style="display:flex;flex-wrap:wrap;gap:6px">' +
            mods.map(m => `<span style="background:#21262d;border:1px solid #30363d;border-radius:20px;padding:4px 12px;font-size:13px">${esc(m.name)}</span>`).join('') +
            '</div>';
    }).catch(e => { $('modList').innerHTML = '<p class="empty-state">' + esc(e.message) + '</p>'; });
}

// ── Init ──

loadServers();
connectWebSocket();
setInterval(loadServers, 5000);
setInterval(() => { if (selectedId) refreshPlayers(); }, 10000);