(() => {
  const defaults={title:"Alex & Sam",values:{headline:"A celebration of love",hostNames:"Alex & Sam",intro:"Request the pleasure of your company as we begin our next chapter.",eventName:"Wedding Ceremony",date:"2027-02-14",time:"16:00",venue:"The Garden Estate, New Delhi",note:"Your presence will make our day complete."}};
  const setText=(id,value)=>{const node=document.getElementById(id);if(node&&typeof value==="string")node.textContent=value;};
  const render=data=>{
    const values=data&&data.values&&typeof data.values==="object"?data.values:{};
    setText("names",values.hostNames||data.title||defaults.title);
    setText("eyebrow",values.headline||defaults.values.headline);
    setText("intro",values.intro||defaults.values.intro);
    setText("event-name",values.eventName||defaults.values.eventName);
    setText("event-date",values.date||defaults.values.date);
    setText("event-time",values.time||defaults.values.time);
    setText("event-venue",values.venue||defaults.values.venue);
    setText("closing",values.note||defaults.values.note);
  };
  render(defaults);
  window.addEventListener("message",event=>{
    if(event.source!==window.parent||!event.data||event.data.type!=="INVITATION_PREVIEW")return;
    render(event.data.content);
  });
})();