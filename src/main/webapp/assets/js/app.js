document.addEventListener('DOMContentLoaded',()=>{
 const sidebar=document.getElementById('sidebar'); const menu=document.getElementById('menuButton');
 if(menu)menu.addEventListener('click',()=>sidebar.classList.toggle('open'));
 document.querySelectorAll('form[data-confirm]').forEach(form=>form.addEventListener('submit',e=>{if(!confirm(form.dataset.confirm))e.preventDefault()}));
 document.querySelectorAll('[data-confirm-button]').forEach(button=>button.addEventListener('click',e=>{if(!confirm('Calculate and finalize this payroll run? This action creates official payroll history.'))e.preventDefault()}));
 document.querySelectorAll('[data-count]').forEach(el=>{const target=Number(el.dataset.count)||0;let value=0;const step=Math.max(1,Math.ceil(target/24));const timer=setInterval(()=>{value=Math.min(target,value+step);el.textContent=value;if(value>=target)clearInterval(timer)},28)});
 const alert=document.querySelector('.alert.success');if(alert)setTimeout(()=>{alert.style.opacity='0';alert.style.transform='translateY(-6px)'},4500);
});
// Browser hints complement (never replace) server-side date validation.
document.querySelectorAll('form').forEach(form=>{
 const action=form.querySelector('input[name="action"]')?.value;
 if(action==='apply'||action==='update'&&form.querySelector('[name="leaveTypeId"]')){
  const start=form.querySelector('[name="startDate"]'),end=form.querySelector('[name="endDate"]');
  const now=new Date(),today=[now.getFullYear(),String(now.getMonth()+1).padStart(2,'0'),String(now.getDate()).padStart(2,'0')].join('-');
  if(start){start.min=today;start.addEventListener('change',()=>{if(end)end.min=start.value||today;});}
  if(end)end.min=start?.value||today;
 }
});
