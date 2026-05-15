/**
 * Unified date pickers: display dd/MM/yyyy (and dd/MM/yyyy HH:mm for datetimes).
 * Submitted values remain ISO (Y-m-d / Y-m-dTH:mm) for Spring MVC binding.
 */
(function () {
    'use strict';

    function frLocale() {
        return typeof flatpickr !== 'undefined' && flatpickr.l10ns && flatpickr.l10ns.fr
            ? flatpickr.l10ns.fr
            : undefined;
    }

    /**
     * showModal() dialogs use the browser top layer; calendars on document.body stay behind.
     * Mount Flatpickr inside the same dialog element so the picker appears above the modal.
     */
    function flatpickrAppendTarget(el) {
        var dlg = el.closest('dialog');
        return dlg || document.body;
    }

    function bindDateInputs() {
        document.querySelectorAll('input[type="date"]').forEach(function (el) {
            if (el.dataset.syscoDatepicker === 'off') return;
            if (el._flatpickr) return;
            flatpickr(el, {
                altInput: true,
                altFormat: 'd/m/Y',
                dateFormat: 'Y-m-d',
                allowInput: true,
                locale: frLocale(),
                altInputClass: 'form-input',
                disableMobile: true,
                appendTo: flatpickrAppendTarget(el),
            });
        });
    }

    function bindDateTimeInputs() {
        document.querySelectorAll('input[type="datetime-local"]').forEach(function (el) {
            if (el.dataset.syscoDatepicker === 'off') return;
            if (el._flatpickr) return;
            flatpickr(el, {
                enableTime: true,
                time_24hr: true,
                altInput: true,
                altFormat: 'd/m/Y H:i',
                dateFormat: 'Y-m-d\\TH:i',
                allowInput: true,
                locale: frLocale(),
                altInputClass: 'form-input',
                disableMobile: true,
                appendTo: flatpickrAppendTarget(el),
            });
        });
    }

    function init() {
        if (typeof flatpickr === 'undefined') return;
        bindDateInputs();
        bindDateTimeInputs();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
