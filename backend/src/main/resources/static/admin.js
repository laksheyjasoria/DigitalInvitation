let token=sessionStorage.getItem("di_token")||"";
let currentRole="";
const $=id=>document.getElementById(id);
const API="/api/v1";
const escapeHtml=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
async function refreshAccessToken(){
 const r=await fetch(API+"/auth/refresh",{method:"POST",credentials:"same-origin"});
 const b=await r.json().catch(()=>({}));
 if(!r.ok||!b.data?.accessToken){token="";sessionStorage.removeItem("di_token");return false;}
 token=b.data.accessToken;sessionStorage.setItem("di_token",token);return true;
}
async function api(path,options={},retry=true){
 const headers={...(options.headers||{})};
 if(token)headers.Authorization="Bearer "+token;
 if(options.body&&!(options.body instanceof FormData))headers["Content-Type"]="application/json";
 const r=await fetch(API+path,{...options,credentials:"same-origin",headers});
 if(r.status===401&&retry&&!path.startsWith("/auth/")&&await refreshAccessToken())return api(path,options,false);
 const b=await r.json().catch(()=>({}));
 if(!r.ok)throw new Error(b.error?.message||b.message||"Request failed ("+r.status+")");
 return b.data;
}
function msg(s){$("admin-message").textContent=s;}
function renderSettings(rows){
 $("settings").innerHTML=rows.map(s=>{
  let value=s.setting_value;
  try{value=typeof value==="string"?value:JSON.stringify(value);}catch(_){}
  return '<form class="setting-row" data-key="'+escapeHtml(s.setting_key)+'"><label>'+escapeHtml(s.setting_key)+
   '<input name="value" value="'+escapeHtml(value)+'"></label><button class="secondary">Save</button></form>';
 }).join("");
 document.querySelectorAll("#settings form").forEach(form=>form.addEventListener("submit",async e=>{
  e.preventDefault();let value=form.elements.value.value;
  try{value=JSON.parse(value);}catch(_){}
  const type=Array.isArray(value)||(value&&typeof value==="object")?"JSON":typeof value==="number"?(Number.isInteger(value)?"INTEGER":"DECIMAL"):typeof value==="boolean"?"BOOLEAN":"STRING";
  try{await api("/admin/settings/"+encodeURIComponent(form.dataset.key),{method:"PUT",body:JSON.stringify({valueType:type,value,description:"Updated from admin dashboard"})});msg("Saved "+form.dataset.key);}
  catch(err){msg(err.message);}
 }));
}
function renderUsers(rows){
 $("users").innerHTML=rows.map(u=>'<div class="setting-row"><strong>'+escapeHtml(u.display_name)+'</strong> · '+escapeHtml(u.email)+' · '+escapeHtml(u.role)+' · '+escapeHtml(u.status)+
  (u.role==="CUSTOMER"&&["ACTIVE","SUSPENDED"].includes(u.status)?' <button class="quiet" data-user="'+u.id+'" data-status="'+(u.status==="ACTIVE"?"SUSPENDED":"ACTIVE")+'">'+(u.status==="ACTIVE"?"Suspend":"Activate")+'</button>':"")+'</div>').join("");
 document.querySelectorAll("[data-user]").forEach(b=>b.addEventListener("click",async()=>{
  try{await api("/admin/users/"+b.dataset.user+"/status",{method:"PATCH",body:JSON.stringify({status:b.dataset.status})});msg("Customer status updated.");await load();}
  catch(e){msg(e.message);}
 }));
}
function renderCategories(rows){
 $("categories").innerHTML=rows.map(c=>'<div class="setting-row"><strong>'+escapeHtml(c.display_name)+'</strong> · '+escapeHtml(c.category_key)+' · '+(c.enabled?"Enabled":"Disabled")+
  ' <button class="quiet" data-category="'+c.id+'" data-enabled="'+(!c.enabled)+'">'+(c.enabled?"Disable":"Enable")+'</button></div>').join("");
 document.querySelectorAll("[data-category]").forEach(b=>b.addEventListener("click",async()=>{
  try{await api("/admin/categories/"+b.dataset.category,{method:"PATCH",body:JSON.stringify({enabled:b.dataset.enabled==="true"})});msg("Category updated.");await load();}
  catch(e){msg(e.message);}
 }));
}
function renderTemplates(rows){
 $("templates").innerHTML=rows.map(t=>{
  const canToggle=["PUBLISHED","ARCHIVED"].includes(t.status);
  const action=canToggle?'<button class="quiet" data-template-status="'+t.id+'" data-status="'+(t.status==="PUBLISHED"?"ARCHIVED":"PUBLISHED")+'">'+(t.status==="PUBLISHED"?"Archive":"Restore")+'</button>':'<span class="muted">Publish a validated package in Template Studio</span>';
  return '<article class="setting-row"><strong>'+escapeHtml(t.display_name)+'</strong> · '+escapeHtml(t.category_name)+' · '+escapeHtml(t.template_key)+' · '+escapeHtml(t.status)+' · v'+escapeHtml(t.current_version)+'<div class="button-row">'+action+'</div></article>';
 }).join("");
 document.querySelectorAll("[data-template-status]").forEach(b=>b.addEventListener("click",async()=>{
  try{await api("/admin/templates/"+b.dataset.templateStatus+"/status",{method:"PATCH",body:JSON.stringify({status:b.dataset.status})});msg("Template status updated.");await load();}
  catch(e){msg(e.message);}
 }));
}
async function load(){
 if(!token){msg("Sign in with an administrator account, then refresh this page.");return;}
 try{
  const [settings,categories,allCategories,templates,users,orders,auditRows,emailRows]=await Promise.all([
   api("/admin/settings"),api("/catalog/categories"),api("/admin/categories"),api("/admin/templates"),
   api("/admin/users"),api("/admin/orders"),api("/admin/audit"),api("/admin/email-outbox")
  ]);
  renderSettings(settings);renderUsers(users);renderCategories(allCategories);renderTemplates(templates);
  $("orders").innerHTML=orders.map(o=>'<div class="setting-row">'+escapeHtml(o.order_number)+' · '+escapeHtml(o.email)+' · '+escapeHtml(o.title)+' · '+escapeHtml(o.currency)+' '+escapeHtml(o.amount_minor)+' · '+escapeHtml(o.status)+'</div>').join("");
  $("email-outbox").innerHTML=emailRows.map(x=>'<div class="setting-row"><strong>'+escapeHtml(x.template_key)+'</strong> · '+escapeHtml(x.recipient)+' · '+escapeHtml(x.status)+' · attempts '+escapeHtml(x.attempts)+' · '+escapeHtml(x.created_at)+(x.status==="FAILED"?' <button class="quiet" data-email-retry="'+x.id+'">Retry</button>':"")+'</div>').join("")||'<p class="muted">No email delivery records.</p>';
  document.querySelectorAll("[data-email-retry]").forEach(b=>b.addEventListener("click",async()=>{try{await api("/admin/email-outbox/"+b.dataset.emailRetry+"/retry",{method:"POST",body:"{}"});msg("Email queued for retry.");await load();}catch(e){msg(e.message);}}));
  $("audit").innerHTML=auditRows.map(x=>'<div class="setting-row">'+escapeHtml(x.action)+' · '+escapeHtml(x.resource_type)+' · '+escapeHtml(x.resource_id)+' · '+escapeHtml(x.created_at)+'</div>').join("");
  $("template-category").innerHTML=categories.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.display_name)+'</option>').join("");
  if(currentRole==="SUPER_ADMIN"){const invites=await api("/admin/invites");$("admin-invites").textContent=invites.map(i=>i.email+" · "+i.role+" · "+(i.consumed_at?"Accepted":i.expires_at)).join("\n")||"No invitations created yet.";}
 }catch(e){msg(e.message);}
}
$("admin-invite-form")?.addEventListener("submit",async event=>{event.preventDefault();const data=Object.fromEntries(new FormData(event.target));try{const result=await api("/admin/invites",{method:"POST",body:JSON.stringify(data)});$("admin-invite-result").textContent="Share this single-use link with "+result.email+": "+result.inviteUrl+" (expires "+result.expiresAt+")";event.target.reset();await load();}catch(error){msg(error.message);}});
$("refresh").addEventListener("click",load);
$("category-form").addEventListener("submit",async e=>{
 e.preventDefault();const data=Object.fromEntries(new FormData(e.target));
 try{await api("/admin/categories",{method:"POST",body:JSON.stringify(data)});msg("Category created.");e.target.reset();await load();}
 catch(err){msg(err.message);}
});
$("policy-form").addEventListener("submit",async e=>{
 e.preventDefault();const type=$("policy-type").value;
 try{const r=await api("/admin/policies/"+type,{method:"POST",body:JSON.stringify({title:$("policy-title").value,bodyMarkdown:$("policy-body").value})});msg(type+" policy version "+r.version+" published.");e.target.reset();}
 catch(err){msg(err.message);}
});
(async()=>{if(!token&&!await refreshAccessToken()){window.location.replace("/app.html");return;}try{const me=await api("/auth/me");currentRole=me.role;if(!["ADMIN","SUPER_ADMIN","SUPPORT"].includes(me.role)){window.location.replace("/app.html");return;}if(me.role==="SUPPORT"){document.querySelectorAll("form").forEach(form=>form.hidden=true);document.querySelectorAll("#categories button,#templates button,#users button,#email-outbox button").forEach(button=>button.hidden=true);document.querySelector("main .welcome p").textContent="Read-only support access.";}else if(me.role!=="SUPER_ADMIN")$("admin-invite-section").hidden=true;await load();}catch(e){token="";sessionStorage.removeItem("di_token");window.location.replace("/app.html");}})();
