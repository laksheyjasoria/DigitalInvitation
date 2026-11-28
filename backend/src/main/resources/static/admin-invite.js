const form=document.getElementById("invite-form");
const message=document.getElementById("invite-message");
const token=new URLSearchParams(location.search).get("token")||"";
if(!token){form.hidden=true;message.textContent="This invitation link is missing its token.";}
form.addEventListener("submit",async event=>{
 event.preventDefault();
 const data=Object.fromEntries(new FormData(form));data.token=token;
 const button=form.querySelector("button");button.disabled=true;message.textContent="Activating your account…";
 try{
  const response=await fetch("/api/v1/auth/accept-admin-invite",{method:"POST",headers:{"Content-Type":"application/json","Accept":"application/json"},body:JSON.stringify(data)});
  const result=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(result.error?.message||result.message||"Unable to accept this invitation");
  form.hidden=true;message.textContent="Your "+result.data.role.toLowerCase()+" account is active. You can now sign in from the administration page.";
  const link=document.createElement("a");link.href="/app.html";link.textContent="Go to sign in";message.append(document.createElement("br"),link);
 }catch(error){message.textContent=error.message;button.disabled=false;}
});