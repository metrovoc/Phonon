(function() {
    'use strict';

    const API_BASE = '/api';
    const POLL_INTERVAL = 2000;

    let tracks = [];
    let token = localStorage.getItem('phonon_token') || '';

    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    function headers() {
        const h = { 'Content-Type': 'application/json' };
        if (token) h['Authorization'] = 'Bearer ' + token;
        return h;
    }

    function showAuthDialog() {
        if ($('#auth-overlay')) return;

        const overlay = document.createElement('div');
        overlay.id = 'auth-overlay';
        overlay.innerHTML = `
            <div class="auth-dialog">
                <h2>Authentication Required</h2>
                <p>Enter the token from server config</p>
                <input type="password" id="auth-token" placeholder="Token">
                <button id="auth-submit">Submit</button>
                <div id="auth-error" class="hidden"></div>
            </div>
        `;
        document.body.appendChild(overlay);

        const submit = () => {
            const input = $('#auth-token').value.trim();
            if (!input) return;
            token = input;
            localStorage.setItem('phonon_token', token);
            overlay.remove();
            loadTracks();
        };

        $('#auth-submit').addEventListener('click', submit);
        $('#auth-token').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') submit();
        });
        $('#auth-token').focus();
    }

    async function api(method, path, body) {
        const opts = { method, headers: headers() };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(API_BASE + path, opts);

        if (res.status === 401) {
            showAuthDialog();
            throw new Error('Unauthorized');
        }

        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Request failed');
        return data;
    }

    function formatDuration(ms) {
        if (ms <= 0) return '--:--';
        const s = Math.floor(ms / 1000);
        const m = Math.floor(s / 60);
        const sec = s % 60;
        return m + ':' + (sec < 10 ? '0' : '') + sec;
    }

    function renderTracks(filter = '') {
        const list = $('#tracks-list');
        const filtered = tracks.filter(t =>
            t.name.toLowerCase().includes(filter.toLowerCase())
        );

        if (filtered.length === 0) {
            list.innerHTML = '<li class="empty">No tracks found</li>';
            return;
        }

        list.innerHTML = filtered.map(t => `
            <li data-id="${t.id}">
                <div class="track-info">
                    <div class="track-name">${escapeHtml(t.name)}</div>
                    <div class="track-meta">${formatDuration(t.durationMs)}</div>
                </div>
                <div class="track-actions">
                    <button class="btn-delete" onclick="deleteTrack('${t.id}')">Delete</button>
                </div>
            </li>
        `).join('');
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    async function loadTracks() {
        try {
            tracks = await api('GET', '/tracks');
            renderTracks($('#search').value);
        } catch (e) {
            if (e.message === 'Unauthorized') return; // auth dialog shown
            console.error('Failed to load tracks:', e);
            $('#tracks-list').innerHTML = '<li class="empty">Failed to load tracks</li>';
        }
    }

    async function addTrack(name, url) {
        const status = $('#add-status');
        status.className = 'status';
        status.textContent = 'Adding track...';
        status.classList.remove('hidden');

        try {
            await api('POST', '/tracks', { name, url });
            status.className = 'status success';
            status.textContent = 'Track added! Downloading...';
            $('#track-name').value = '';
            $('#track-url').value = '';
            setTimeout(loadTracks, 1000);
        } catch (e) {
            status.className = 'status error';
            status.textContent = e.message;
        }
    }

    window.deleteTrack = async function(id) {
        if (!confirm('Delete this track?')) return;
        try {
            await api('DELETE', '/tracks/' + id);
            loadTracks();
        } catch (e) {
            alert('Failed to delete: ' + e.message);
        }
    };

    async function pollDownloads() {
        try {
            const downloads = await api('GET', '/downloads');
            const section = $('#downloads-section');
            const list = $('#downloads-list');

            if (downloads.length === 0) {
                section.classList.add('hidden');
                return;
            }

            section.classList.remove('hidden');
            list.innerHTML = downloads.map(d => `
                <li>
                    <div class="download-progress">
                        <div>${escapeHtml(d.status)}</div>
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: ${d.percent}%"></div>
                        </div>
                    </div>
                </li>
            `).join('');
        } catch (e) {
            if (e.message === 'Unauthorized') return;
            console.error('Failed to poll downloads:', e);
        }
    }

    function init() {
        $('#add-form').addEventListener('submit', (e) => {
            e.preventDefault();
            const name = $('#track-name').value.trim();
            const url = $('#track-url').value.trim();
            if (name && url) addTrack(name, url);
        });

        $('#search').addEventListener('input', (e) => {
            renderTracks(e.target.value);
        });

        loadTracks();
        setInterval(() => {
            pollDownloads();
            loadTracks();
        }, POLL_INTERVAL);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
