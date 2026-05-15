(function () {
    var root = document.getElementById('missionsWizard');
    if (!root) return;

    var total = parseInt(root.getAttribute('data-total-steps') || '5', 10);
    var step = 1;
    var items = root.querySelectorAll('.missions-stepper-item');
    var panels = root.querySelectorAll('.missions-wizard-panel');
    var prevBtn = root.querySelector('.missions-wizard-prev');
    var nextBtn = root.querySelector('.missions-wizard-next');
    var saveBtn = root.querySelector('.missions-wizard-save');
    var metaEl = root.querySelector('.missions-wizard-meta');
    var tpl = metaEl ? metaEl.getAttribute('data-template') : '';
    var delBtn = root.querySelector('.missions-wizard-delete');
    var dlLink = root.querySelector('.missions-wizard-download');

    function applyStep() {
        panels.forEach(function (panel) {
            var n = parseInt(panel.getAttribute('data-step'), 10);
            var active = n === step;
            panel.classList.toggle('is-active', active);
            panel.toggleAttribute('hidden', !active);
            panel.setAttribute('aria-hidden', active ? 'false' : 'true');
        });

        items.forEach(function (li, idx) {
            var n = idx + 1;
            li.classList.toggle('is-active', n === step);
            li.classList.toggle('is-complete', n < step);
            li.setAttribute('aria-current', n === step ? 'step' : 'false');
        });

        if (prevBtn) prevBtn.disabled = step <= 1;
        if (nextBtn) nextBtn.hidden = step >= total;
        if (saveBtn) saveBtn.hidden = step < total;

        if (delBtn) delBtn.hidden = step < total;
        if (dlLink) dlLink.hidden = step < total;

        if (metaEl && tpl) {
            metaEl.textContent = tpl.replace('{0}', String(step)).replace('{1}', String(total));
        }
    }

    function validateStep(n) {
        var panel = root.querySelector('.missions-wizard-panel[data-step="' + n + '"]');
        if (!panel) return true;
        var fields = panel.querySelectorAll('input, select, textarea');
        for (var i = 0; i < fields.length; i++) {
            var el = fields[i];
            if (el.closest('[hidden]')) continue;
            if (!el.checkValidity()) {
                el.reportValidity();
                return false;
            }
        }
        return true;
    }

    if (prevBtn) {
        prevBtn.addEventListener('click', function () {
            if (step > 1) {
                step--;
                applyStep();
            }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', function () {
            if (!validateStep(step)) return;
            if (step < total) {
                step++;
                applyStep();
            }
        });
    }

    applyStep();
})();
