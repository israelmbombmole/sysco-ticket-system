(function () {
    var cfg = window.syscoMissionsAssistant || {};
    var DEFAULT_CHAT_URL = '/app/missions/api/assistant/chat';

    var fab = document.getElementById('missionsAiFab');
    var drawer = document.getElementById('missionsAiDrawer');
    var dismissTap = document.getElementById('missionsAiDismissTap');

    if (!fab || !drawer) {
        return;
    }

    var labels = cfg.labels || {};
    var chatUrl = (cfg.chatUrl && String(cfg.chatUrl).trim()) || DEFAULT_CHAT_URL;
    var assistantLive = cfg.assistantLive === true;

    var thread = document.getElementById('missionsAiThread');
    var input = document.getElementById('missionsAiInput');
    var sendBtn = document.getElementById('missionsAiSendBtn');
    var attachChk = document.getElementById('missionsAiAttachForm');
    var suggestsRoot = document.getElementById('missionsAiSuggests');

    var offlineBannerInserted = false;

    function csrfPair() {
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var token = tokenMeta ? tokenMeta.getAttribute('content') : '';
        var header = headerMeta ? headerMeta.getAttribute('content') : '';
        if (!token) {
            var hidden =
                document.querySelector('form.missions-mission-form input[name="_csrf"]') ||
                document.querySelector('input[type="hidden"][name="_csrf"]');
            if (hidden && hidden.value) {
                token = hidden.value;
            }
        }
        /*
         * Spring Security commonly expects header X-XSRF-TOKEN or X-CSRF-TOKEN; if layout omits meta, still send token.
         */
        if (token && !header) {
            header = 'X-XSRF-TOKEN';
        }
        return { token: token || '', header: header || '' };
    }

    function applyCsrfHeaders(headers) {
        var csrf = csrfPair();
        if (csrf.token && csrf.header) {
            headers[csrf.header] = csrf.token;
        }
    }

    function esc(s) {
        return (s || '').replace(/[&<>"']/g, function (c) {
            return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]);
        });
    }

    function insertOfflineBannerIfNeeded() {
        if (offlineBannerInserted || assistantLive || !thread) {
            return;
        }
        offlineBannerInserted = true;
        var title = cfg.offlineBanner || '';
        var hint = cfg.adminHint || '';
        var wrap = document.createElement('div');
        wrap.className = 'missions-ai-offline-banner';
        wrap.setAttribute('role', 'status');
        var p1 = document.createElement('p');
        p1.className = 'missions-ai-offline-banner-title';
        p1.textContent = title;
        wrap.appendChild(p1);
        if (hint) {
            var p2 = document.createElement('p');
            p2.className = 'muted missions-ai-offline-banner-hint';
            p2.textContent = hint;
            wrap.appendChild(p2);
        }
        thread.insertBefore(wrap, thread.firstChild);
    }

    function closeAssistant(ev) {
        if (ev) {
            ev.preventDefault();
            ev.stopPropagation();
        }
        if (drawer.open && typeof drawer.close === 'function') {
            drawer.close();
        }
    }

    /** Programmatic close (e.g. layout hooks); « Fermer » uses form[method=dialog] in HTML. */
    window.syscoCloseMissionsAssistant = closeAssistant;

    function openAssistant(ev) {
        if (ev) {
            ev.preventDefault();
        }
        document.body.classList.add('missions-ai-drawer-open');
        insertOfflineBannerIfNeeded();
        if (typeof drawer.showModal === 'function') {
            try {
                if (!drawer.open) {
                    drawer.showModal();
                }
            } catch (ignore) {
                /* Invalid state if already open */
            }
        } else if (!drawer.open) {
            drawer.setAttribute('open', '');
        }
        window.setTimeout(function () {
            if (input) {
                input.focus();
            }
        }, 50);
    }

    fab.addEventListener('click', openAssistant);

    if (dismissTap) {
        dismissTap.addEventListener('click', closeAssistant);
    }

    drawer.addEventListener('close', function () {
        document.body.classList.remove('missions-ai-drawer-open');
        try {
            fab.focus();
        } catch (ignore) {}
    });

    /* Mission list toggle */
    var toggleBtn = document.getElementById('missionsToggleListBtn');
    var listPanel = document.getElementById('missionsListPanel');
    if (toggleBtn && listPanel) {
        var showLabel = toggleBtn.getAttribute('data-label-show') || '';
        var hideLabel = toggleBtn.getAttribute('data-label-hide') || '';
        var labelSpan = toggleBtn.querySelector('.missions-toggle-list-label');

        function syncListToggleUi(open) {
            toggleBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
            listPanel.hidden = !open;
            if (labelSpan) {
                labelSpan.textContent = open ? hideLabel : showLabel;
            }
        }

        toggleBtn.addEventListener('click', function () {
            var open = toggleBtn.getAttribute('aria-expanded') === 'true';
            syncListToggleUi(!open);
        });
        syncListToggleUi(false);
    }

    if (!thread || !input || !sendBtn) {
        return;
    }

    function renderSuggests() {
        if (!suggestsRoot || !cfg.suggests || !cfg.suggests.length) {
            return;
        }
        suggestsRoot.innerHTML = '';
        cfg.suggests.forEach(function (text) {
            if (!text) {
                return;
            }
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'missions-ai-suggest-chip';
            b.textContent = text;
            b.addEventListener('click', function () {
                input.value = text;
                input.focus();
            });
            suggestsRoot.appendChild(b);
        });
    }

    renderSuggests();

    function appendBubble(role, text) {
        var div = document.createElement('div');
        div.className = 'chat-msg ct-assistant-msg missions-ai-msg' + (role === 'user' ? ' mine' : '');
        var who = role === 'user' ? (labels.user || 'You') : (labels.assistant || 'Assistant');
        var av = role === 'user' ? '●' : '✦';
        var inner = '<div class="chat-msg-head"><div class="chat-avatar">' + esc(av) +
            '</div><strong>' + esc(who) + '</strong></div>';
        inner += '<div class="ct-assistant-msg-body">';
        var lines = (text || '').split('\n');
        for (var i = 0; i < lines.length; i++) {
            inner += '<p>' + esc(lines[i]) + '</p>';
        }
        inner += '</div>';
        div.innerHTML = inner;
        thread.appendChild(div);
        thread.scrollTop = thread.scrollHeight;
    }

    function collectMissionContext() {
        if (!attachChk || !attachChk.checked) {
            return '';
        }
        var missionForm = document.querySelector('form.missions-mission-form');
        if (missionForm) {
            var fd = new FormData(missionForm);
            var keys = [
                'code', 'title', 'site', 'startDate', 'endDate', 'status', 'leadUserId',
                'objectives', 'durationNote', 'departureNote', 'returnNote', 'transportDetail',
                'expensesNote', 'description', 'observationsNote',
                'orderReference', 'orderIssueDate', 'orderIssuedBy', 'orderBody'
            ];
            var lines = [];
            keys.forEach(function (k) {
                var v = fd.get(k);
                if (v != null && String(v).trim() !== '') {
                    lines.push(k + ': ' + String(v).trim());
                }
            });
            var men = fd.getAll('participantMenIds');
            var women = fd.getAll('participantWomenIds');
            if (men.length) {
                lines.push('participantMenIds: ' + men.join(','));
            }
            if (women.length) {
                lines.push('participantWomenIds: ' + women.join(','));
            }
            return lines.join('\n');
        }
        var reportForm = document.querySelector('form.missions-report-save-form');
        if (reportForm) {
            var rt = reportForm.querySelector('textarea[name="reportText"]');
            var code = reportForm.querySelector('input[name="code"]');
            var bits = [];
            if (code && code.value) {
                bits.push('code: ' + code.value.trim());
            }
            if (rt && rt.value) {
                bits.push('reportText:\n' + rt.value.trim());
            }
            return bits.join('\n');
        }
        return '';
    }

    /** Apply server-suggested values only into empty text inputs / textareas (never overwrite user text). */
    function applyMissionPrefill(prefill) {
        if (!prefill || typeof prefill !== 'object') {
            return;
        }
        var missionForm = document.querySelector('form.missions-mission-form');
        if (!missionForm) {
            return;
        }
        Object.keys(prefill).forEach(function (k) {
            var v = prefill[k];
            if (v == null || String(v).trim() === '') {
                return;
            }
            var el = missionForm.elements.namedItem(k);
            if (!el || !el.tagName) {
                return;
            }
            var tag = el.tagName.toUpperCase();
            if (tag !== 'TEXTAREA' && tag !== 'INPUT') {
                return;
            }
            var cur = (el.value || '').trim();
            if (cur !== '') {
                return;
            }
            el.value = String(v).trim();
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        });
    }

    function setBusy(b) {
        sendBtn.disabled = b;
        input.disabled = b;
        var spin = thread.querySelector('.missions-ai-busy');
        if (b) {
            if (!spin) {
                spin = document.createElement('p');
                spin.className = 'muted missions-ai-busy';
                spin.textContent = labels.busy || '…';
                thread.appendChild(spin);
            }
            thread.scrollTop = thread.scrollHeight;
        } else if (spin) {
            spin.remove();
        }
    }

    function postChat(message) {
        var headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
        applyCsrfHeaders(headers);
        fetch(chatUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: headers,
            body: JSON.stringify({
                message: message == null ? '' : message,
                context: collectMissionContext()
            })
        })
            .then(function (r) {
                return r.text().then(function (text) {
                    if (!r.ok) {
                        throw new Error('HTTP ' + r.status);
                    }
                    try {
                        return text ? JSON.parse(text) : {};
                    } catch (ignore) {
                        throw new Error('bad-json');
                    }
                });
            })
            .then(function (data) {
                setBusy(false);
                var reply = data && data.reply != null ? String(data.reply) : '';
                var src = data && data.source != null ? String(data.source) : '';
                if (src === 'local' && assistantLive && labels.aiUnavailable) {
                    reply = labels.aiUnavailable + '\n\n' + reply;
                }
                applyMissionPrefill(data && data.prefill);
                appendBubble('assistant', reply);
            })
            .catch(function () {
                setBusy(false);
                appendBubble('assistant', labels.error || 'Error');
            });
    }

    sendBtn.addEventListener('click', function () {
        var msg = (input.value || '').trim();
        if (!msg) {
            input.focus();
            return;
        }
        appendBubble('user', msg);
        input.value = '';
        setBusy(true);
        postChat(msg);
    });
})();
