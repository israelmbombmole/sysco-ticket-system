(function () {
  function each(root, sel, fn) {
    Array.prototype.forEach.call(root.querySelectorAll(sel), fn);
  }

  function showPanel(wizard, name) {
    each(wizard, '.js-esc-panel', function (p) {
      p.hidden = p.getAttribute('data-panel') !== name;
    });
  }

  function resetSdFilters(panel) {
    if (!panel) {
      return;
    }
    each(panel, '.js-sd-filter-btn', function (b) {
      b.classList.remove('is-active');
    });
    each(panel, '.js-esc-target-row', function (row) {
      row.hidden = false;
    });
  }

  function applySdFilter(panel, sdToken) {
    if (!panel) {
      return;
    }
    each(panel, '.js-esc-target-row', function (row) {
      var rowSd = row.getAttribute('data-sd') || 'none';
      row.hidden = rowSd !== sdToken;
    });
  }

  function resetWizard(wizard) {
    showPanel(wizard, 'root');
    resetSdFilters(wizard.querySelector('[data-panel="esc-int"]'));
    resetSdFilters(wizard.querySelector('[data-panel="re-int"]'));
  }

  function bindWizardShell(wizard) {
    var details = wizard.closest('details.my-work-escalate-details');
    if (details) {
      details.addEventListener('toggle', function () {
        if (!details.open) {
          resetWizard(wizard);
        }
      });
    }
    var dialog = wizard.closest('dialog.sysco-escalate-dialog');
    if (dialog) {
      dialog.addEventListener('close', function () {
        resetWizard(wizard);
      });
    }
  }

  document.querySelectorAll('.js-my-work-escalate-wizard').forEach(function (wizard) {
    bindWizardShell(wizard);

    wizard.querySelectorAll('[data-esc-nav]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var target = btn.getAttribute('data-esc-nav');
        showPanel(wizard, target);
        if (target === 'esc-int') {
          var escPanel = wizard.querySelector('[data-panel="esc-int"]');
          var multi = wizard.getAttribute('data-multi-sd-esc') === 'true';
          resetSdFilters(escPanel);
          if (!multi && escPanel) {
            each(escPanel, '.js-esc-target-row[data-flow="esc-int"]', function (row) {
              row.hidden = false;
            });
          } else if (multi && escPanel) {
            each(escPanel, '.js-esc-target-row[data-flow="esc-int"]', function (row) {
              row.hidden = true;
            });
          }
        }
        if (target === 're-int') {
          var rePanel = wizard.querySelector('[data-panel="re-int"]');
          var multiRe = wizard.getAttribute('data-multi-sd-re') === 'true';
          resetSdFilters(rePanel);
          if (!multiRe && rePanel) {
            each(rePanel, '.js-esc-target-row[data-flow="re-int"]', function (row) {
              row.hidden = false;
            });
          } else if (multiRe && rePanel) {
            each(rePanel, '.js-esc-target-row[data-flow="re-int"]', function (row) {
              row.hidden = true;
            });
          }
        }
      });
    });

    wizard.querySelectorAll('[data-panel="esc-int"] .js-sd-filter-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var panel = wizard.querySelector('[data-panel="esc-int"]');
        var tok = btn.getAttribute('data-sd') || 'none';
        each(panel, '.js-sd-filter-btn', function (b) {
          b.classList.toggle('is-active', b === btn);
        });
        applySdFilter(panel, tok);
      });
    });

    wizard.querySelectorAll('[data-panel="re-int"] .js-sd-filter-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var panel = wizard.querySelector('[data-panel="re-int"]');
        var tok = btn.getAttribute('data-sd') || 'none';
        each(panel, '.js-sd-filter-btn', function (b) {
          b.classList.toggle('is-active', b === btn);
        });
        applySdFilter(panel, tok);
      });
    });

    wizard.querySelectorAll('form.inline-form.js-esc-target-row').forEach(function (form) {
      var reassignInput = form.querySelector('input[name="reassign"][value="true"]');
      if (!reassignInput) {
        return;
      }
      form.addEventListener('submit', function (e) {
        if (form.getAttribute('data-confirm-reopen') !== 'true') {
          return;
        }
        var msg = form.getAttribute('data-reopen-msg') || '';
        if (!window.confirm(msg)) {
          e.preventDefault();
        }
      });
    });
  });

  document.querySelectorAll('[data-sysco-escalate-dialog-id]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var id = btn.getAttribute('data-sysco-escalate-dialog-id');
      if (!id) {
        return;
      }
      var dlg = document.getElementById(id);
      if (dlg && typeof dlg.showModal === 'function') {
        dlg.showModal();
      }
    });
  });

  document.querySelectorAll('dialog.sysco-escalate-dialog').forEach(function (dlg) {
    dlg.addEventListener('click', function (e) {
      if (e.target === dlg) {
        dlg.close();
      }
    });
    dlg.querySelectorAll('[data-sysco-close-escalate]').forEach(function (b) {
      b.addEventListener('click', function () {
        dlg.close();
      });
    });
  });
})();
