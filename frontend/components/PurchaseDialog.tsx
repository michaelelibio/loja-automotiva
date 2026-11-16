'use client';

import { useEffect, useId, useRef, type ReactNode } from 'react';

export function PurchaseDialog({title,children,onClose,busy=false}: {
  title:string; children:ReactNode; onClose:()=>void; busy?:boolean;
}) {
  const ref=useRef<HTMLDialogElement>(null);
  const titleId=useId();
  useEffect(()=>{
    const element=ref.current;
    const previous=document.activeElement;
    const overflow=document.body.style.overflow;
    if(element && !element.open)element.showModal();
    document.body.style.overflow='hidden';
    return ()=>{element?.close();document.body.style.overflow=overflow;if(previous instanceof HTMLElement)previous.focus();};
  },[]);
  return <dialog ref={ref} className="purchase-dialog" aria-labelledby={titleId} aria-busy={busy}
    onCancel={event=>{event.preventDefault();if(!busy)onClose();}}>
    <button type="button" className="purchase-dialog-close" aria-label="Fechar confirmação" disabled={busy} onClick={onClose}>×</button>
    <h2 id={titleId}>{title}</h2>{children}
  </dialog>;
}
