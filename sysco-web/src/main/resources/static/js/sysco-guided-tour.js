(function () {
  var g = typeof globalThis !== 'undefined' ? globalThis : window;

  /**
   * driver.js 1.x IIFE sets window.driver = { js: { driver: factoryFn } } (dist/driver.js.iife.js).
   * Some builds or CDNs may differ; try fallbacks.
   */
  function driverFactory() {
    var d = g.driver;
    if (d && d.js && typeof d.js.driver === 'function') {
      return d.js.driver;
    }
    var legacy = g['driver.js'];
    if (legacy && typeof legacy.driver === 'function') {
      return legacy.driver;
    }
    if (typeof d === 'function') {
      return d;
    }
    return null;
  }

  function readPayload() {
    var el = document.getElementById('sysco-tour-payload');
    if (!el) {
      return null;
    }
    var raw = '';
    if (el.tagName && el.tagName.toLowerCase() === 'textarea') {
      raw = el.value || '';
    } else {
      raw = el.textContent || '';
    }
    try {
      return JSON.parse(raw || '{}');
    } catch (e) {
      return null;
    }
  }

  function postTutorialComplete() {
    var url = document.body.getAttribute('data-tour-complete-url');
    if (!url) {
      return;
    }
    var tokenMeta = document.querySelector('meta[name="_csrf"]');
    var headerMeta = document.querySelector('meta[name="_csrf_header"]');
    if (!tokenMeta || !headerMeta || !tokenMeta.content) {
      return;
    }
    var headers = {};
    headers[headerMeta.content] = tokenMeta.content;
    fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: headers,
    }).catch(function () {});
  }

  function mapSteps(rawSteps) {
    return (rawSteps || []).map(function (s) {
      return {
        element: s.selector,
        popover: {
          title: s.title,
          description: s.description,
          side: 'bottom',
          align: 'start',
        },
      };
    });
  }

  var activeDriver = null;

  function runTour() {
    var create = driverFactory();
    if (!create) {
      return;
    }
    var payload = readPayload();
    if (!payload || !payload.steps || !payload.steps.length) {
      return;
    }
    if (activeDriver && typeof activeDriver.isActive === 'function' && activeDriver.isActive()) {
      if (typeof activeDriver.destroy === 'function') {
        activeDriver.destroy();
      }
      activeDriver = null;
    }
    var labels = payload.labels || {};
    var steps = mapSteps(payload.steps);
    activeDriver = create({
      showProgress: true,
      animate: true,
      allowClose: true,
      nextBtnText: labels.next || 'Next',
      prevBtnText: labels.prev || 'Previous',
      doneBtnText: labels.done || 'Done',
      progressText: labels.progress || '{{current}} / {{total}}',
      steps: steps,
      onDestroyed: function () {
        postTutorialComplete();
        activeDriver = null;
      },
    });
    activeDriver.drive();
  }

  function bindGuidedTour() {
    var helpBtn = document.getElementById('sysco-help-tour-btn');
    if (helpBtn) {
      helpBtn.addEventListener('click', function () {
        runTour();
      });
    }
    var auto = document.body && document.body.getAttribute('data-sysco-auto-tour');
    if (auto === 'true') {
      g.setTimeout(runTour, 500);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindGuidedTour);
  } else {
    bindGuidedTour();
  }
})();
