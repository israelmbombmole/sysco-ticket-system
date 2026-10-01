(function () {
    function setOpen(group, open) {
        group.classList.toggle("is-open", open);
        var button = group.querySelector(".nav-group-toggle");
        if (button) {
            button.setAttribute("aria-expanded", open ? "true" : "false");
        }
    }

    document.querySelectorAll(".nav-group-toggle").forEach(function (button) {
        button.addEventListener("click", function () {
            var group = button.closest(".nav-group");
            if (!group) {
                return;
            }
            var willOpen = !group.classList.contains("is-open");
            document.querySelectorAll(".nav-group").forEach(function (other) {
                if (other !== group) {
                    setOpen(other, false);
                }
            });
            setOpen(group, willOpen);
        });
    });

    document.addEventListener("sysco-tour-start", function () {
        document.querySelectorAll(".nav-group").forEach(function (group) {
            setOpen(group, true);
        });
    });
})();
