(function(){ "use strict";
window.addEventListener("message",function(event){
 if(event.source!==window.parent||!event.data||event.data.type!=="di-preview-render")return;
 const snapshot=event.data.snapshot||{};
 if(!window.InvitationRenderer)return;
 window.InvitationRenderer.render(document.getElementById("preview-root"),snapshot);
});
window.parent.postMessage({type:"di-preview-ready"},"*");
})();