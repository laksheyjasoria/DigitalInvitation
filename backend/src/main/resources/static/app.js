const API="/api/v1";const CUSTOMER_MEDIA_UPLOADS_ENABLED=false;let token=sessionStorage.getItem("di_token")||"",isAdminUser=false;let tab="login",activeInvitation=null,templateDefinition=null,templatePackagePreview=null,categories=[],draftContent=null,previewReviewed=false,previewPendingApproval=false,paymentRequired=true,extensionEnabled=false,previewEnabled=true,customSectionsEnabled=true,defaultTimezone="Asia/Kolkata",supportedTimezones=["Asia/Kolkata"],supportedLocales=["en","hi"];
function populateInvitationPreferences(preferredTimezone,preferredLocale){const timezoneSelect=$("timezone"),localeSelect=$("invitation-locale");if(!timezoneSelect||!localeSelect)return;const zones=[...new Set((supportedTimezones||[]).filter(Boolean))];if(!zones.includes(defaultTimezone))zones.unshift(defaultTimezone);supportedTimezones=zones;const locales=[...new Set((supportedLocales||[]).filter(Boolean))];if(!locales.includes("en"))locales.unshift("en");supportedLocales=locales;const zoneLabels={"Asia/Kolkata":"India Standard Time (Asia/Kolkata)","UTC":"Coordinated Universal Time (UTC)","Europe/London":"United Kingdom (Europe/London)","America/New_York":"Eastern Time (America/New_York)"};const localeLabels={en:"English",hi:"Hindi"};timezoneSelect.innerHTML=zones.map(z=>'<option value="'+escapeHtml(z)+'">'+escapeHtml(zoneLabels[z]||z)+'</option>').join("");localeSelect.innerHTML=locales.map(l=>'<option value="'+escapeHtml(l)+'">'+escapeHtml(localeLabels[l]||l.toUpperCase())+'</option>').join("");timezoneSelect.value=zones.includes(preferredTimezone)?preferredTimezone:defaultTimezone;localeSelect.value=locales.includes(preferredLocale)?preferredLocale:locales[0];}
const $=id=>document.getElementById(id),escapeHtml=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
populateInvitationPreferences(defaultTimezone,"en");
const initialPasswordInput=$("auth-form").elements.password;initialPasswordInput.minLength=8;initialPasswordInput.maxLength=72;initialPasswordInput.pattern="";initialPasswordInput.setCustomValidity("");initialPasswordInput.autocomplete="current-password";
async function refreshAccessToken(){const r=await fetch(API+"/auth/refresh",{method:"POST",credentials:"same-origin"});const b=await r.json().catch(()=>({}));if(!r.ok||!b.data?.accessToken){token="";sessionStorage.removeItem("di_token");return false;}token=b.data.accessToken;sessionStorage.setItem("di_token",token);return true;}
async function api(path,options={},allowRefresh=true){const headers={...(options.headers||{})};if(token)headers.Authorization="Bearer "+token;if(options.body&&!(options.body instanceof FormData))headers["Content-Type"]="application/json";const res=await fetch(API+path,{...options,credentials:"same-origin",headers});if(res.status===401&&allowRefresh&&!path.startsWith("/auth/")&&await refreshAccessToken())return api(path,options,false);const body=await res.json().catch(()=>({}));if(!res.ok)throw new Error(body.error?.message||body.message||"Request failed ("+res.status+")");return body.data;}
const previewFrames=()=>[...document.querySelectorAll("iframe[data-template-preview-frame]")];
function renderPreviewFrame(frame,snapshot){if(!frame)return;const original=snapshot;frame._diSnapshot=snapshot;const renderPrepared=prepared=>{if(frame._diSnapshot!==original)return;frame._diSnapshot=prepared;if(prepared?.templatePackagePreview){frame._diMode="package";window.TemplatePackageRenderer.renderIntoFrame(frame,prepared.templatePackagePreview,prepared);return;}if(frame._diMode==="package"){frame._diMode="platform";frame._diReady=false;frame.dataset.previewInitialized="";}if(!frame.dataset.previewInitialized){frame._diMode="platform";frame.dataset.previewInitialized="true";frame.src="/template-preview-frame.html";return;}if(frame._diReady)frame.contentWindow.postMessage({type:"di-preview-render",snapshot:prepared},"*");};const locationUrl=String(snapshot?.content?.locationUrl||"").trim();if(locationUrl&&!snapshot.locationQrDataUrl){api("/invitations/qr-preview",{method:"POST",body:JSON.stringify({locationUrl})}).then(result=>renderPrepared({...snapshot,locationQrDataUrl:result.dataUrl||""})).catch(error=>{console.warn("Unable to generate invitation location QR preview",error);renderPrepared({...snapshot,locationQrDataUrl:""});});return;}renderPrepared(snapshot);}
window.addEventListener("message",event=>{
 const frame=previewFrames().find(item=>item.contentWindow===event.source);
 if(!frame)return;
 if(frame._diMode==="package"){if(event.data?.type==="di-template-resize")frame.style.height=Math.max(520,Math.min(5000,Number(event.data.height)||760))+"px";if(event.data?.type==="di-preview-rendered"&&frame.id==="preview-frame"&&previewPendingApproval){previewPendingApproval=false;previewReviewed=true;$("create-order").disabled=false;$("consent-panel").classList.remove("hidden");$("create-order").textContent=paymentRequired?"Publish invitation · Continue to payment":"Publish invitation (free)";$("payment-message").textContent="Preview rendered. Review it carefully, accept the declarations below, then publish.";$("consent-panel").scrollIntoView({behavior:"smooth",block:"nearest"});}return;}
 if(event.data?.type==="di-preview-ready"){frame._diReady=true;if(frame._diSnapshot)frame.contentWindow.postMessage({type:"di-preview-render",snapshot:frame._diSnapshot},"*");return;}
 if(event.data?.type==="di-preview-rendered"&&frame.id==="preview-frame"&&previewPendingApproval){
  previewPendingApproval=false;previewReviewed=true;$("create-order").disabled=false;$("consent-panel").classList.remove("hidden");$("create-order").textContent=paymentRequired?"Publish invitation · Continue to payment":"Publish invitation (free)";$("payment-message").textContent="Preview rendered. Review it carefully, accept the declarations below, then publish.";$("consent-panel").scrollIntoView({behavior:"smooth",block:"nearest"});
 }
});
function showWorkspace(){$("auth-panel").classList.add("hidden");$("workspace").classList.remove("hidden");$("logout").classList.remove("hidden");$("account-label").textContent="Signed in";loadWorkspace();}
function showAuth(){$("auth-panel").classList.remove("hidden");$("workspace").classList.add("hidden");$("logout").classList.add("hidden");$("account-label").textContent="Not signed in";}
document.querySelectorAll(".tab").forEach(b=>b.addEventListener("click",()=>{tab=b.dataset.tab;document.querySelectorAll(".tab").forEach(x=>x.classList.toggle("active",x===b));$("name-wrap").classList.toggle("hidden",tab!=="register");$("auth-submit").textContent=tab==="register"?"Create account":"Sign in";const passwordInput=$("auth-form").elements.password;passwordInput.minLength=8;passwordInput.maxLength=72;passwordInput.pattern=tab==="register"?"(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{8,72}":"";passwordInput.title=tab==="register"?"Use at least 8 characters with at least one letter, one number and one symbol.":"Enter your password (minimum 8 characters).";passwordInput.autocomplete=tab==="register"?"new-password":"current-password";}));
$("auth-form").addEventListener("submit",async e=>{e.preventDefault();$("auth-error").textContent="";const passwordInput=$("auth-form").elements.password;const enteredPassword=String(passwordInput.value||"");if(tab==="login"){if(enteredPassword.length<8||enteredPassword.length>72){$("auth-error").textContent="Password must be between 8 and 72 characters.";return;}}else if(enteredPassword.length<8||enteredPassword.length>72||!/[A-Za-z]/.test(enteredPassword)||!/[0-9]/.test(enteredPassword)||!/[^A-Za-z0-9\s]/.test(enteredPassword)){ $("auth-error").textContent="Use 8–72 characters with at least one letter, one number and one symbol.";return;}passwordInput.pattern="";passwordInput.setCustomValidity("");const data=Object.fromEntries(new FormData(e.target));try{if(tab==="register"){await api("/auth/register",{method:"POST",body:JSON.stringify(data)});document.querySelector('[data-tab="login"]').click();$("auth-error").textContent="Account created. Check your email for the verification link before signing in.";}else{const result=await api("/auth/login",{method:"POST",body:JSON.stringify(data)});token=result.accessToken;sessionStorage.setItem("di_token",token);isAdminUser=["ADMIN","SUPER_ADMIN"].includes(result.user?.role);if(["ADMIN","SUPER_ADMIN","SUPPORT"].includes(result.user?.role)){window.location.replace("/admin.html");return;}showWorkspace();}}catch(err){$("auth-error").textContent=err.message;}});
$("logout").addEventListener("click",async()=>{try{await fetch(API+"/auth/logout",{method:"POST",credentials:"same-origin"});}catch(_){}token="";sessionStorage.removeItem("di_token");activeInvitation=null;showAuth();});
async function loadWorkspace(){try{const [list,cats]=await Promise.all([api("/invitations"),api("/catalog/categories")]);categories=cats;$("category").innerHTML=cats.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.display_name)+'</option>').join("");$("gallery-category").innerHTML=cats.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.display_name)+'</option>').join("");$("invitation-list").innerHTML=list.length?list.map(i=>'<div class="invite-item"><button class="quiet" data-id="'+i.id+'"><strong>'+escapeHtml(i.title)+'</strong><small>'+escapeHtml(i.status)+' · '+(i.last_function_date||"Date not set")+'</small></button>'+(extensionEnabled&&["PUBLISHED","EXPIRED"].includes(i.status)?'<button class="secondary" data-extend="'+i.id+'">Extend</button>':"")+'</div>').join(""):'<p class="muted">No invitations yet. Create your first one.</p>';document.querySelectorAll("[data-id]").forEach(b=>b.addEventListener("click",()=>openInvitation(b.dataset.id)));document.querySelectorAll("[data-extend]").forEach(b=>b.addEventListener("click",()=>startExtension(b.dataset.extend)));}catch(e){$("auth-error").textContent=e.message;}}
function showTemplateInfo(template){const info=$("template-info");if(!info)return;if(!template){info.textContent="No published designs are available in this category yet.";return;}const design=templateDefinition?.design||{};const palette=design.palette||{};const swatches=Object.values(palette).filter(value=>typeof value==="string"&&/^#[0-9a-f]{6}$/i.test(value));info.innerHTML='<strong>'+escapeHtml(template.display_name)+'</strong><span> · '+escapeHtml(template.description||"Flexible invitation design")+'</span><div class="template-swatches">'+swatches.map(color=>'<i style="background:'+color+'" aria-label="Theme colour"></i>').join("")+'</div><small>Style: '+escapeHtml(design.style||templateDefinition?.layout||"classic")+' · '+(templateDefinition?.sections?.length||0)+' editable sections</small>';}
async function loadTemplates(categoryId){const rows=await api("/catalog/categories/"+categoryId+"/templates");$("template").innerHTML=rows.map(t=>'<option value="'+t.template_version_id+'">'+escapeHtml(t.display_name)+'</option>').join("");if(rows.length){const detail=await api("/catalog/templates/"+rows[0].id);templateDefinition=typeof detail.definition==="string"?JSON.parse(detail.definition):detail.definition;templatePackagePreview=detail.packagePreview||null;showTemplateInfo(rows[0]);renderSections();}else{templateDefinition={sections:[]};templatePackagePreview=null;showTemplateInfo(null);renderSections();}}
function sampleTemplateContent(definition){
 const samples={headline:"A Beautiful Celebration",hostnames:"Alex & Sam",intro:"Together with our families, we invite you to share a day filled with love and joy.",eventname:"Wedding Ceremony",date:"14 February 2027",time:"4:00 PM",venue:"The Garden Estate",description:"An evening of celebration and togetherness.",heading:"A day to remember",body:"Join us as we make beautiful memories together.",familynames:"The Sharma and Mehta families",note:"Your presence is our greatest gift.",contact:"+91 98765 43210",deadline:"1 February 2027"};
 return {locale:"en",sections:(definition?.sections||[]).map(section=>{const values={};(section.fields||[]).forEach(field=>{const key=typeof field==="object"?String(field.key||""):String(field);values[key]=samples[key.toLowerCase()]||"Details to be announced";});const sampleItems=section.repeatable?[Object.fromEntries((section.fields||[]).map(field=>{const key=typeof field==="object"?String(field.key||""):String(field);return [key,samples[key.toLowerCase()]||"Details to be announced"];}))]:[];return {key:section.key,label:section.label,enabled:section.enabled!==false,values:section.repeatable?{}:values,items:sampleItems};})};
}
let galleryChoices=[];
function chooseTemplate(choice){
 activeInvitation=null;draftContent=null;templateDefinition=choice.def;templatePackagePreview=choice.preview||null;
 $("category").value=choice.t.category_id||$("gallery-category").value;
 $("template").innerHTML='<option value="'+choice.t.template_version_id+'">'+escapeHtml(choice.t.display_name)+'</option>';
 $("template").value=choice.t.template_version_id;
 $("template-gallery").classList.add("hidden");$("editor-form").classList.remove("hidden");
 $("upload-media").disabled=true;$("media-manager").classList.add("hidden");$("rsvp-management").classList.add("hidden");
 $("published-result").classList.add("hidden");$("save").classList.remove("hidden");$("create-order").classList.remove("hidden");
 previewReviewed=false;previewPendingApproval=false;$("create-order").disabled=true;$("invite-title").value="";$("function-date").value="";$("location-url").value="";$("share-message").value="You are invited! 🎉\n\nWe would be delighted to have you join us for our special celebration.\n\nClick here to view your invitation:";$("timezone").value=defaultTimezone;
 $("invitation-locale").value=supportedLocales[0]||"en";$("consent-panel").classList.add("hidden");
 showTemplateInfo(choice.t);renderSections();$("editor-title").textContent="Personalize "+choice.t.display_name;
 const dialog=$("template-preview-dialog");if(dialog.open)dialog.close();
 $("editor-form").scrollIntoView({behavior:"smooth",block:"start"});
}
async function showTemplateGallery(categoryId){
 $("template-gallery").classList.remove("hidden");$("editor-form").classList.add("hidden");$("empty-editor").classList.add("hidden");
 $("gallery-category").value=categoryId||categories[0]?.id||"";$("template-grid").innerHTML="<p class=muted>Loading designs...</p>";
 const rows=await api("/catalog/categories/"+encodeURIComponent($("gallery-category").value)+"/templates");
 if(!rows.length){$("template-grid").innerHTML="<p class=muted>No published designs for this occasion yet.</p>";return;}
 galleryChoices=[];
 for(const t of rows){const d=await api("/catalog/templates/"+t.id);const def=typeof d.definition==="string"?JSON.parse(d.definition):d.definition;galleryChoices.push({t,def,preview:d.packagePreview||null,sampleContent:d.packageSampleContent||sampleTemplateContent(def)});}
 $("template-grid").innerHTML=galleryChoices.map((x,i)=>'<article class="template-choice"><div class="template-card-stage"><iframe class="template-card-frame" data-template-preview-frame data-template-thumb="'+i+'" src="about:blank" sandbox="allow-scripts" title="Invitation sample preview"></iframe></div><div class="template-choice-copy"><h3>'+escapeHtml(x.t.display_name)+'</h3><p>'+escapeHtml(x.t.description||"A thoughtfully designed invitation")+'</p><small>'+(x.def?.sections?.length||0)+' editable sections · '+escapeHtml(x.def?.design?.style||"classic")+' layout</small><div class="template-card-actions"><button type="button" class="secondary" data-template-preview="'+i+'">Full preview</button><button type="button" class="primary" data-template-choice="'+i+'">Choose design</button></div></div></article>').join("");
 galleryChoices.forEach((x,i)=>renderPreviewFrame(document.querySelector('[data-template-thumb="'+i+'"]'),{title:x.t.display_name,content:x.sampleContent,design:x.def?.design||{},layout:x.def?.layout,templatePackagePreview:x.preview}));
 document.querySelectorAll("[data-template-choice]").forEach(b=>b.addEventListener("click",()=>chooseTemplate(galleryChoices[Number(b.dataset.templateChoice)])));
 document.querySelectorAll("[data-template-preview]").forEach(b=>b.addEventListener("click",()=>{
  const choice=galleryChoices[Number(b.dataset.templatePreview)];$("template-preview-title").textContent=choice.t.display_name;
  renderPreviewFrame($("template-preview-full"),{title:choice.t.display_name,content:choice.sampleContent,design:choice.def?.design||{},layout:choice.def?.layout,templatePackagePreview:choice.preview});
  $("template-preview-use").onclick=()=>chooseTemplate(choice);$("template-preview-dialog").showModal();
 }));
}
$("new-invitation").addEventListener("click",()=>showTemplateGallery($("gallery-category").value||categories[0]?.id).catch(e=>alert(e.message)));
$("gallery-category").addEventListener("change",e=>showTemplateGallery(e.target.value).catch(err=>alert(err.message)));
$("gallery-cancel").addEventListener("click",()=>{$("template-gallery").classList.add("hidden");$("empty-editor").classList.remove("hidden");});
$("template-preview-close").addEventListener("click",()=>$("template-preview-dialog").close());
$("category").addEventListener("change",e=>{invalidateInvitationPreview();loadTemplates(e.target.value);});
$("template").addEventListener("change",async()=>{try{invalidateInvitationPreview();const rows=await api("/catalog/categories/"+$("category").value+"/templates");const selected=rows.find(t=>t.template_version_id===$("template").value);if(selected){const d=await api("/catalog/templates/"+selected.id);templateDefinition=typeof d.definition==="string"?JSON.parse(d.definition):d.definition;templatePackagePreview=d.packagePreview||null;showTemplateInfo(selected);renderSections();}}catch(e){alert(e.message);}});
function fieldMeta(field,section){
 if(typeof field==="object"&&field){const key=String(field.key||field.name||"field"),required=field.required===true||((section.requiredFields||[]).includes(key));return {key,label:String(field.label||field.key||field.name||"Field"),type:String(field.type||"text"),required,optional:field.optional===undefined?!required:field.optional===true,placeholder:String(field.placeholder||"")};}
 const key=String(field),labels={headline:"Invitation headline",hostNames:"Names / hosts",intro:"Invitation message",eventName:"Function name",date:"Date",time:"Time",venue:"Venue / address",description:"Description",heading:"Heading",body:"Message",familyNames:"Family names",message:"Message",deadline:"RSVP deadline",contact:"Contact details",note:"Additional note",media:"Photo gallery"};
 const required=(section.requiredFields||[]).includes(key);return {key,label:labels[key]||key.replace(/([A-Z])/g," $1").replace(/^./,c=>c.toUpperCase()),type:/date/i.test(key)?"date":/email/i.test(key)?"email":/phone|mobile|contact/i.test(key)?"text":/url|website/i.test(key)?"url":/message|intro|description|body|note/i.test(key)?"textarea":"text",required,optional:!required,placeholder:""};
}
function renderSections(){
 if(!templateDefinition)templateDefinition={sections:[]};
 if(!Array.isArray(templateDefinition.sections))templateDefinition.sections=[];
 const sections=templateDefinition.sections;
 $("section-editor").innerHTML=sections.map((section,i)=>{
  const saved=draftContent?.sections?.find(x=>x.key===section.key)||{},label=section.label,hidden=new Set(saved.hiddenFields||[]);
  const fields=(section.fields||[]).map(field=>fieldMeta(field,section));
  const fieldInputs=values=>fields.filter(field=>!hidden.has(field.key)).map(field=>'<label class="template-field"><span>'+escapeHtml(field.label)+(field.required?' <b class="required-mark">Required</b>':' <small>Optional</small>')+'</span>'+(field.type==="textarea"?'<textarea data-field="'+escapeHtml(field.key)+'" placeholder="'+escapeHtml(field.placeholder||field.label)+'">'+escapeHtml(values?.[field.key]||"")+'</textarea>':'<input type="'+(["date","email","url","number","tel"].includes(field.type)?field.type:"text")+'" data-field="'+escapeHtml(field.key)+'" value="'+escapeHtml(values?.[field.key]||"")+'" placeholder="'+escapeHtml(field.placeholder||field.label)+'">')+(field.optional?'<button type="button" class="quiet field-hide" data-hide-field="'+i+':'+escapeHtml(field.key)+'">Remove field</button>':"")+'</label>').join("");
  const hiddenFields=fields.filter(field=>hidden.has(field.key)&&field.optional);
  const restore=hiddenFields.length?'<details class="optional-fields"><summary>Add optional fields ('+hiddenFields.length+')</summary><div>'+hiddenFields.map(field=>'<button type="button" class="secondary" data-restore-field="'+i+':'+escapeHtml(field.key)+'">＋ '+escapeHtml(field.label)+'</button>').join("")+'</div></details>':"";
  const content=section.repeatable
   ? '<div class="repeatable-items">'+(saved.items?.length?saved.items:[saved.values||{}]).map((item,index)=>'<div class="repeatable-item"><div class="section-title"><strong>Entry '+(index+1)+'</strong>'+(index>0?'<button type="button" class="quiet" data-remove-item="'+i+':'+index+'">Remove</button>':"")+'</div><div class="fields">'+fieldInputs(item)+'</div></div>').join("")+'</div><button type="button" class="secondary" data-add-item="'+i+'">＋ Add another</button>'
   : '<div class="fields">'+fieldInputs(saved.values)+'</div>';
  const requiredSection=section.required===true;
  return '<div class="section-card" data-index="'+i+'"><div class="section-title"><label><input type="checkbox" data-section="'+i+'" '+((saved.enabled??section.enabled)?"checked":"")+(requiredSection?' disabled aria-label="Required section"':'')+'> <span>'+escapeHtml(label)+(requiredSection?' <b class="required-mark">Required</b>':'')+'</span></label></div>'+content+restore+'</div>';
 }).join("");
 document.querySelectorAll("[data-hide-field]").forEach(btn=>btn.addEventListener("click",()=>{const [index,key]=btn.dataset.hideField.split(":");const data=readContent().sections[Number(index)];data.hiddenFields=[...new Set([...(data.hiddenFields||[]),key])];saveSectionDraft(data);renderSections();invalidateInvitationPreview();}));
 document.querySelectorAll("[data-restore-field]").forEach(btn=>btn.addEventListener("click",()=>{const [index,key]=btn.dataset.restoreField.split(":");const data=readContent().sections[Number(index)];data.hiddenFields=(data.hiddenFields||[]).filter(field=>field!==key);saveSectionDraft(data);renderSections();invalidateInvitationPreview();}));
 document.querySelectorAll("[data-add-item]").forEach(btn=>btn.addEventListener("click",()=>{const current=readContent().sections[Number(btn.dataset.addItem)];if(!current.items)current.items=[];current.items.push({});saveSectionDraft(current);renderSections();}));
 document.querySelectorAll("[data-remove-item]").forEach(btn=>btn.addEventListener("click",()=>{const [sectionIndex,itemIndex]=btn.dataset.removeItem.split(":").map(Number);const current=readContent().sections[sectionIndex];current.items.splice(itemIndex,1);saveSectionDraft(current);renderSections();}));
}
function saveSectionDraft(data){draftContent=draftContent||{locale:$("invitation-locale").value||"en",sections:[]};draftContent.sections=draftContent.sections||[];const index=draftContent.sections.findIndex(section=>section.key===data.key);if(index>=0)draftContent.sections[index]=data;else draftContent.sections.push(data);}
function readContent(){
 const sections=[...document.querySelectorAll(".section-card")].map((card,i)=>{
  const section=templateDefinition.sections[i],saved=draftContent?.sections?.find(item=>item.key===section.key)||{},hidden=saved.hiddenFields||[];
  if(section.repeatable){
   const previousItems=saved.items||[];
   const items=[...card.querySelectorAll(".repeatable-item")].map((item,index)=>{const row={...(previousItems[index]||{})};item.querySelectorAll("[data-field]").forEach(input=>{const key=input.dataset.field;if(input.value.trim())row[key]=input.value.trim();else delete row[key];});return row;});
   return {key:section.key,label:section.label,enabled:card.querySelector("[data-section]").checked,hiddenFields:hidden,items};
  }
  const values={...(saved.values||{})};card.querySelectorAll("[data-field]").forEach(input=>{const key=input.dataset.field;if(input.value.trim())values[key]=input.value.trim();else delete values[key];});
  return {key:section.key,label:section.label,enabled:card.querySelector("[data-section]").checked,hiddenFields:hidden,values};
 });
 return {locale:$("invitation-locale").value||"en",locationUrl:$("location-url")?.value.trim()||"",shareMessage:$("share-message")?.value.trim()||"",sections};
}
async function saveDraft(){const title=$("invite-title").value.trim();if(!title)throw new Error("Enter an invitation title");const content=readContent();if(content.locationUrl){let location;try{location=new URL(content.locationUrl);}catch(_){throw new Error("Enter a valid Google Maps or other HTTPS location URL.");}if(location.protocol!=="https:"||!location.hostname)throw new Error("Location URL must use HTTPS.");content.locationUrl=location.href;}for(const section of (templateDefinition?.sections||[])){
 const saved=content.sections.find(item=>item.key===section.key);
 if(section.required&&saved?.enabled===false)throw new Error("This template requires the section: "+section.label);
 if(!saved?.enabled)continue;
 const requiredFields=section.requiredFields||[];
 const missing=requiredFields.filter(field=>!saved.hiddenFields?.includes(field)&&!String(saved.values?.[field]||"").trim()&&!(saved.items||[]).some(item=>String(item[field]||"").trim()));
 if(missing.length)throw new Error("Complete required field(s) in "+section.label+": "+missing.join(", "));
 if(section.repeatable){for(const item of saved.items||[]){const hasAny=Object.values(item||{}).some(value=>String(value||"").trim());if(hasAny){const empty=requiredFields.filter(field=>!String(item[field]||"").trim());if(empty.length)throw new Error("Complete "+empty.join(", ")+" in every "+section.label+" entry.");}}}
 }
 draftContent=content;const scheduledDates=content.sections.flatMap(section=>[...Object.entries(section.values||{}),...(section.items||[]).flatMap(item=>Object.entries(item))]).filter(([key,value])=>/date/i.test(key)&&/^\d{4}-\d{2}-\d{2}$/.test(String(value))).map(([,value])=>String(value)).sort();const latestFunctionDate=scheduledDates.length?scheduledDates[scheduledDates.length-1]:$("function-date").value||null;const body={title,content,templateVersionId:$("template").value,lastFunctionDate:latestFunctionDate,timezone:$("timezone").value};if(!activeInvitation){body.templateVersionId=$("template").value;if(!body.templateVersionId)throw new Error("Choose an available template");const result=await api("/invitations",{method:"POST",body:JSON.stringify(body)});activeInvitation=result.id;}else await api("/invitations/"+activeInvitation,{method:"PUT",body:JSON.stringify(body)});await loadWorkspace();$("upload-media").disabled=false;await loadMedia();$("consent-panel").classList.remove("hidden");$("payment-message").textContent="Draft saved. Review each declaration before publication.";}
$("save").addEventListener("click",async()=>{try{await saveDraft();}catch(e){$("payment-message").textContent=e.message;}});
async function loadRsvps(){
 if(!activeInvitation)return;
 try{
  const rows=await api("/invitations/"+activeInvitation+"/rsvps");
  $("rsvp-list").innerHTML=rows.length?rows.map(r=>'<div class="invite-item"><strong>'+escapeHtml(r.guest_name)+'</strong><small>'+escapeHtml(r.attendance)+' · '+escapeHtml(r.guest_count)+' guest(s) · '+escapeHtml(r.guest_email||"No email")+'</small>'+(r.message?'<p>'+escapeHtml(r.message)+'</p>':"")+'<small>'+escapeHtml(r.created_at)+'</small></div>').join(""):'<p class="muted">No guest responses yet.</p>';
 }catch(e){$("rsvp-list").innerHTML='<p class="error">'+escapeHtml(e.message)+'</p>';}
}
async function exportRsvps(){
 if(!activeInvitation)return;
 try{
  const path=API+"/invitations/"+activeInvitation+"/rsvps/export";
  const send=()=>fetch(path,{credentials:"same-origin",headers:token?{Authorization:"Bearer "+token}:{}});
  let response=await send();
  if(response.status===401&&await refreshAccessToken())response=await send();
  if(!response.ok)throw new Error("Unable to export RSVP responses ("+response.status+")");
  const blob=await response.blob(),url=URL.createObjectURL(blob),link=document.createElement("a");
  link.href=url;link.download="invitation-rsvps.csv";document.body.appendChild(link);link.click();link.remove();URL.revokeObjectURL(url);
 }catch(e){alert(e.message);}
}
$("load-rsvps").addEventListener("click",loadRsvps);
$("export-rsvps").addEventListener("click",exportRsvps);
async function openInvitation(id){try{const i=await api("/invitations/"+id);activeInvitation=id;previewReviewed=false;previewPendingApproval=false;$("create-order").disabled=true;draftContent=typeof i.content==="string"?JSON.parse(i.content):i.content;$("published-result").classList.add("hidden");$("preview-box").classList.add("hidden");$("empty-editor").classList.add("hidden");$("editor-form").classList.remove("hidden");$("invite-title").value=i.title;$("function-date").value=i.last_function_date||"";$("location-url").value=draftContent?.locationUrl||"";$("share-message").value=draftContent?.shareMessage||"You are invited! 🎉\n\nWe would be delighted to have you join us for our special celebration.\n\nClick here to view your invitation:";populateInvitationPreferences(i.timezone,draftContent?.locale||"en");const selectedDefinition=await api("/catalog/template-versions/"+encodeURIComponent(i.template_version_id));$("category").value=selectedDefinition.category_id;await loadTemplates(selectedDefinition.category_id);$("template").innerHTML='<option value="'+selectedDefinition.template_version_id+'">'+escapeHtml(selectedDefinition.display_name)+' · v'+selectedDefinition.version_number+'</option>';$("template").value=selectedDefinition.template_version_id;templateDefinition=typeof selectedDefinition.definition==="string"?JSON.parse(selectedDefinition.definition):selectedDefinition.definition;templatePackagePreview=selectedDefinition.packagePreview||null;showTemplateInfo(selectedDefinition);renderSections();
 await loadMedia();
 $("consent-panel").classList.remove("hidden");
 const state=i.status;
 $("rsvp-management").classList.toggle("hidden",!["PUBLISHED","EXPIRED"].includes(state));
 if(["PUBLISHED","EXPIRED"].includes(state))await loadRsvps();
 $("save").classList.toggle("hidden",state==="READY_TO_PUBLISH"||state==="PUBLISHED"||state==="EXPIRED");
 $("create-order").classList.toggle("hidden",state==="READY_TO_PUBLISH"||state==="PUBLISHED"||state==="EXPIRED"||state==="SUSPENDED"||state==="PENDING_PAYMENT"&&["CREATED","PAYMENT_PENDING"].includes(i.order_status));
 $("resume-checkout").classList.toggle("hidden",!(state==="PENDING_PAYMENT"&&["CREATED","PAYMENT_PENDING"].includes(i.order_status)));
 $("publish-invitation").classList.toggle("hidden",state!=="READY_TO_PUBLISH");
 if(state==="PUBLISHED"){
  $("consent-panel").classList.add("hidden");$("published-result").classList.remove("hidden");
  const publicUrl=location.origin+"/i/"+i.public_slug;
  const shareMessage=String(draftContent?.shareMessage||"You are invited! 🎉\n\nWe would be delighted to have you join us for our special celebration.\n\nClick here to view your invitation:").trim();const fullShareMessage=(shareMessage?shareMessage+"\n\n":"")+publicUrl;$("published-result").innerHTML='<div class="published-success"><p class="eyebrow">READY TO SHARE</p><h2>Your invitation is live!</h2><label>Published invitation URL<div class="share-url-row"><input id="published-url" readonly value="'+escapeHtml(publicUrl)+'"><button id="copy-published-url" type="button" class="secondary">Copy link</button></div></label><div class="share-actions"><a class="share-whatsapp" target="_blank" rel="noopener" href="https://wa.me/?text='+encodeURIComponent(fullShareMessage)+'">Share on WhatsApp</a><button id="copy-share-message" type="button" class="secondary">Copy invitation message</button><button id="native-share" type="button" class="secondary">More sharing options</button></div><p class="muted">Valid until '+escapeHtml(i.expires_at||"the configured expiry date")+'</p></div>';
  $("copy-published-url").addEventListener("click",async()=>{try{await navigator.clipboard.writeText(publicUrl);$("copy-published-url").textContent="Copied!";}catch(e){$("published-url").select();document.execCommand("copy");$("copy-published-url").textContent="Copied!";}});
  $("copy-share-message").addEventListener("click",async()=>{try{await navigator.clipboard.writeText(fullShareMessage);$("copy-share-message").textContent="Message copied!";}catch(e){alert("Unable to copy the invitation message.");}});$("native-share").addEventListener("click",async()=>{if(navigator.share){try{await navigator.share({title:i.title||"Invitation",text:shareMessage,url:publicUrl});}catch(e){if(e.name!=="AbortError")alert("Unable to open sharing options.");}}else{try{await navigator.clipboard.writeText(fullShareMessage);alert("Invitation message copied. Paste it into any app.");}catch(e){$("published-url").select();}}});
  $("published-result").scrollIntoView({behavior:"smooth",block:"center"});
 }else if(state==="EXPIRED"||state==="SUSPENDED"){
  $("consent-panel").classList.add("hidden");$("published-result").classList.remove("hidden");
  $("published-result").textContent=state==="EXPIRED"?"This invitation has expired. You can still review guest responses or purchase an extension if enabled.":"This invitation has been suspended by the platform administrator.";
 }
 if(state==="PENDING_PAYMENT"&&i.order_status==="FAILED")$("payment-message").textContent="The previous payment attempt failed. Update your draft and try again.";
} catch(e){alert(e.message);}}

$("preview").addEventListener("click",()=>{try{const content=readContent();previewReviewed=false;previewPendingApproval=true;$("create-order").disabled=true;$("payment-message").textContent="Rendering your invitation preview...";$("preview-box").className="preview invitation-preview";$("preview-box").classList.remove("hidden");renderPreviewFrame($("preview-frame"),{title:$("invite-title").value.trim()||"Your invitation",content,design:templateDefinition?.design||{},layout:templateDefinition?.layout,templatePackagePreview});}catch(e){previewPendingApproval=false;$("payment-message").textContent=e.message;}});
$("preview-toggle").addEventListener("click",()=>$("preview").click());

function invalidateInvitationPreview(){if(previewReviewed||previewPendingApproval){previewReviewed=false;previewPendingApproval=false;$("create-order").disabled=true;$("payment-message").textContent="Your invitation changed. Preview it again before continuing.";}if($("preview-box")&&!$("preview-box").classList.contains("hidden")&&templateDefinition){const content=readContent();renderPreviewFrame($("preview-frame"),{title:$("invite-title").value.trim()||"Your invitation",content,design:templateDefinition.design||{},layout:templateDefinition.layout,templatePackagePreview});}}
$("section-editor").addEventListener("input",invalidateInvitationPreview);
$("section-editor").addEventListener("change",invalidateInvitationPreview);
$("section-editor").addEventListener("click",invalidateInvitationPreview);

["invite-title","function-date","location-url","share-message","invitation-locale","timezone"].forEach(id=>{const el=$(id);if(el){el.addEventListener("input",invalidateInvitationPreview);el.addEventListener("change",invalidateInvitationPreview);}});
const declarations=[["accuracy","I confirm that names, dates, venues and invitation details are accurate."],["preview","I have reviewed and approve the invitation preview."],["rights","I have permission to upload and publish all submitted content and personal information."],["expiry","I understand the invitation validity and expiry policy displayed at checkout."],["publication","I understand that content I publish will be accessible to anyone with its unique link."],["terms","I agree to the applicable Terms and Conditions."],["privacy","I acknowledge the applicable Privacy Policy."]];
$("consent-items").innerHTML=declarations.map(([key,label])=>'<label class="consent"><input id="consent-'+key+'" type="checkbox"><span>'+escapeHtml(label)+'</span></label>').join("");
async function loadPolicies(){
 const keepTimezone=$("timezone")?.value||defaultTimezone,keepLocale=$("invitation-locale")?.value||"en";
 populateInvitationPreferences(keepTimezone,keepLocale);
 let config=null,policies=[];
 // Resolve the signed-in role independently: a policy/config endpoint failure must not
 // accidentally send an administrator into the paid checkout flow.
 try{
  const me=await api("/auth/me");
  const rawRole=String(me?.role||me?.user?.role||"").toUpperCase().replace(/^ROLE_/,"");
  isAdminUser=rawRole==="ADMIN"||rawRole==="SUPER_ADMIN";
 }catch(error){console.warn("Unable to determine account role for publication pricing",error);}
 try{config=await api("/public/config");}catch(error){console.warn("Unable to load publication settings",error);}
 try{policies=await api("/public/policies/current")||[];}catch(error){console.warn("Unable to load policy documents",error);}
 if(config){
  defaultTimezone=config.defaultTimezone||"Asia/Kolkata";
  supportedTimezones=Array.isArray(config.supportedTimezones)&&config.supportedTimezones.length?config.supportedTimezones:["Asia/Kolkata","UTC","Europe/London","America/New_York"];
  supportedLocales=Array.isArray(config.supportedLocales)&&config.supportedLocales.length?config.supportedLocales:["en","hi"];
  paymentRequired=!!config.paymentRequired&&!isAdminUser;
  extensionEnabled=!!config.extensionEnabled;
  previewEnabled=config.previewEnabled!==false;
  customSectionsEnabled=config.customSectionsEnabled!==false;
 }
 populateInvitationPreferences(keepTimezone,keepLocale);
 $("preview").disabled=!previewEnabled;$("preview-toggle").disabled=!previewEnabled;
 if(extensionEnabled)loadWorkspace();
 $("create-order").textContent=paymentRequired?"Publish invitation · Continue to payment":"Publish invitation (free)";
 $("policy-links").innerHTML=policies.map(item=>'<details><summary>'+escapeHtml(item.title)+' · v'+item.version_number+'</summary><pre>'+escapeHtml(item.body_markdown)+'</pre></details>').join("");
}
const originalShowWorkspace=showWorkspace;
showWorkspace=()=>{originalShowWorkspace();loadPolicies();};
async function startExtension(invitationId){
 try{
  const order=await api("/invitations/"+invitationId+"/extensions/orders",{method:"POST",body:"{}"});
  const checkout=await api("/payments/"+order.orderId+"/checkout",{method:"POST",body:"{}"});
  if(!window.Razorpay)throw new Error("Checkout script did not load");
  new window.Razorpay({key:checkout.keyId,order_id:checkout.providerOrder.id,amount:checkout.providerOrder.amount,currency:checkout.providerOrder.currency,name:"DigitalInvitation",description:"Invitation extension",
   handler:async response=>{try{await api("/payments/"+order.orderId+"/verify",{method:"POST",body:JSON.stringify({providerOrderId:response.razorpay_order_id,providerPaymentId:response.razorpay_payment_id,signature:response.razorpay_signature})});alert("Invitation extended successfully.");await loadWorkspace();}catch(e){alert(e.message);}}
  }).open();
 }catch(e){alert(e.message);}
}
async function publishNow(){await api("/invitations/"+activeInvitation+"/publish",{method:"POST",body:"{}"});await openInvitation(activeInvitation);$("payment-message").textContent="Published successfully. Your link and sharing options are ready.";}
$("create-order").addEventListener("click",async()=>{try{if(!previewReviewed)throw new Error("Please preview and approve your invitation before continuing to payment.");await saveDraft();const consent={};for(const [key] of declarations){if(!$("consent-"+key).checked)throw new Error("Please accept every required declaration.");consent[key]=true;}await api("/invitations/"+activeInvitation+"/consents",{method:"POST",body:JSON.stringify(consent)});if(!paymentRequired){await publishNow();return;}const order=await api("/invitations/"+activeInvitation+"/orders",{method:"POST",body:"{}"});const checkout=await api("/payments/"+order.orderId+"/checkout",{method:"POST",body:"{}"});if(!window.Razorpay)throw new Error("Checkout script did not load. Please retry.");const amountLabel=new Intl.NumberFormat(undefined,{style:"currency",currency:checkout.providerOrder.currency||"INR"}).format(Number(checkout.providerOrder.amount||0)/100);$("payment-message").textContent="Invitation total: "+amountLabel+". Review the amount in secure checkout before paying.";const options={key:checkout.keyId,order_id:checkout.providerOrder.id,amount:checkout.providerOrder.amount,currency:checkout.providerOrder.currency,name:"DigitalInvitation",description:"Digital invitation",handler:async response=>{try{await api("/payments/"+order.orderId+"/verify",{method:"POST",body:JSON.stringify({providerOrderId:response.razorpay_order_id,providerPaymentId:response.razorpay_payment_id,signature:response.razorpay_signature})});$("payment-message").textContent="Payment verified. You can now publish your invitation.";$("publish-invitation").classList.remove("hidden");}catch(err){$("payment-message").textContent=err.message;}},modal:{ondismiss:()=>{$("payment-message").textContent="Checkout closed. Your order remains available for retry.";} }};new window.Razorpay(options).open();}catch(e){if(String(e.message).includes("Administrator accounts publish without payment checkout")){try{await publishNow();return;}catch(publishError){$("payment-message").textContent=publishError.message;return;}}$("payment-message").textContent=e.message;}});
$("resume-checkout").addEventListener("click",async()=>{try{const detail=await api("/invitations/"+activeInvitation);const orderId=detail.order_id;if(!orderId)throw new Error("No pending order was found");const checkout=await api("/payments/"+orderId+"/checkout",{method:"POST",body:"{}"});if(!window.Razorpay)throw new Error("Checkout script did not load");new window.Razorpay({key:checkout.keyId,order_id:checkout.providerOrder.id,amount:checkout.providerOrder.amount,currency:checkout.providerOrder.currency,name:"DigitalInvitation",description:"Digital invitation",handler:async response=>{try{await api("/payments/"+orderId+"/verify",{method:"POST",body:JSON.stringify({providerOrderId:response.razorpay_order_id,providerPaymentId:response.razorpay_payment_id,signature:response.razorpay_signature})});$("resume-checkout").classList.add("hidden");$("publish-invitation").classList.remove("hidden");$("payment-message").textContent="Payment verified. Publish your invitation when ready.";}catch(e){$("payment-message").textContent=e.message;}}}).open();}catch(e){$("payment-message").textContent=e.message;}});
$("publish-invitation").addEventListener("click",async()=>{try{await api("/invitations/"+activeInvitation+"/publish",{method:"POST",body:"{}"});await openInvitation(activeInvitation);$("payment-message").textContent="Published successfully. Your link and sharing options are ready.";}catch(e){$("payment-message").textContent=e.message;}});
(async()=>{if(!token)await refreshAccessToken();if(token)showWorkspace();else showAuth();})();


// Invitation media library: private by default; public access is an explicit owner action.
async function loadMedia(){
 if(!CUSTOMER_MEDIA_UPLOADS_ENABLED){$("media-manager").classList.add("hidden");$("upload-media").disabled=true;return;}
 if(!activeInvitation)return;
 $("media-manager").classList.remove("hidden");
 try{
  const rows=await api("/invitations/"+activeInvitation+"/media");
  $("media-list").innerHTML=rows.length?rows.map(m=>'<div class="invite-item"><strong>'+escapeHtml(m.original_filename)+'</strong><small>'+escapeHtml(m.content_type)+' · '+Math.ceil(Number(m.size_bytes)/1024)+' KB · '+escapeHtml(m.visibility)+'</small><div class="button-row"><button type="button" class="quiet" data-media-visibility="'+m.id+'" data-visibility="'+(m.visibility==="PUBLIC"?"PRIVATE":"PUBLIC")+'">'+(m.visibility==="PUBLIC"?"Make private":"Make public")+'</button><button type="button" class="quiet" data-media-delete="'+m.id+'">Delete</button><button type="button" class="quiet" data-media-copy="'+m.id+'">Copy '+(m.visibility==="PUBLIC"?"public":"private")+' URL</button></div></div>').join(""):'<p class="muted">No images uploaded yet.</p>';
  document.querySelectorAll("[data-media-visibility]").forEach(b=>b.addEventListener("click",async()=>{
   try{await api("/invitations/"+activeInvitation+"/media/"+b.dataset.mediaVisibility+"/visibility",{method:"PATCH",body:JSON.stringify({visibility:b.dataset.visibility})});await loadMedia();}
   catch(e){$("media-message").textContent=e.message;}
  }));
  document.querySelectorAll("[data-media-delete]").forEach(b=>b.addEventListener("click",async()=>{
   if(!confirm("Delete this image from the invitation?"))return;
   try{await api("/invitations/"+activeInvitation+"/media/"+b.dataset.mediaDelete,{method:"DELETE"});await loadMedia();}
   catch(e){$("media-message").textContent=e.message;}
  }));
  document.querySelectorAll("[data-media-copy]").forEach(b=>b.addEventListener("click",()=>{
   const row=rows.find(x=>x.id===b.dataset.mediaCopy);if(!row)return;
   const path=row.visibility==="PUBLIC"?location.origin+API+"/public/media/"+row.id:location.origin+API+"/invitations/"+activeInvitation+"/media/"+row.id+"/content";
   navigator.clipboard?.writeText(path).then(()=>$("media-message").textContent="Media URL copied. Private URLs require an authenticated session.").catch(()=>$("media-message").textContent=path);
  }));
 }catch(e){$("media-list").innerHTML='<p class="error">'+escapeHtml(e.message)+'</p>';}
}
$("upload-media").addEventListener("click",async()=>{
 if(!CUSTOMER_MEDIA_UPLOADS_ENABLED){$("media-message").textContent="Customer photo uploads are currently disabled.";return;}
 const file=$("media-file").files?.[0];if(!file){$("media-message").textContent="Choose an image first.";return;}
 if(!activeInvitation){$("media-message").textContent="Save the invitation draft before uploading media.";return;}
 const form=new FormData();form.append("file",file);
 try{
  const send=()=>fetch(API+"/invitations/"+activeInvitation+"/media",{method:"POST",credentials:"same-origin",headers:token?{Authorization:"Bearer "+token}:{},body:form});
  let response=await send();if(response.status===401&&await refreshAccessToken())response=await send();
  const body=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(body.error?.message||body.message||"Image upload failed ("+response.status+")");
  $("media-file").value="";$("media-message").textContent="Image uploaded privately to the configured storage provider.";await loadMedia();
 }catch(e){$("media-message").textContent=e.message;}
});
