(() => {
  const root = document.querySelector(".invitation");
  if (root && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    root.classList.add("is-ready");
    document.querySelectorAll(".function").forEach((item, index) => {
      item.style.animationDelay = (index * 180) + "ms";
    });
  }
})();
